package com.thaimer.bfour.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.thaimer.bfour.alarm.AlarmRules
import com.thaimer.bfour.ble.BfourConnection
import com.thaimer.bfour.ble.BfourProtocol
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide brain of the app: talks to the thermometer, keeps the UI state,
 * decides when to alarm. The foreground service turns [events] into sound and
 * notifications; the UI only reads [state] and calls the actions below.
 *
 * All methods must be called on the main thread.
 */
object Thermo {
    sealed interface Event {
        data class Almost(val probe: Int) : Event
        data class Done(val probe: Int) : Event
        data object ConnectionLost : Event
    }

    private val _state = MutableStateFlow(ThermoState())
    val state: StateFlow<ThermoState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private lateinit var prefs: Prefs
    private lateinit var connection: BfourConnection
    private val main = Handler(Looper.getMainLooper())

    private val alarms = mutableMapOf<Int, AlarmRules.State>()
    private var lastDataAt = 0L
    private var lastSampleAt = 0L
    private var disconnectedAt = 0L
    private var running = false

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = Prefs(app)
        connection = BfourConnection(app, connectionListener)
        _state.value = ThermoState(
            model = BfourProtocol.modelName(prefs.deviceType),
            unit = prefs.unit,
            probes = buildProbes(BfourProtocol.probeCount(prefs.deviceType)),
        )
    }

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        running = true
        connection.start(prefs.deviceAddress)
        main.post(watchdog)
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacks(watchdog)
        connection.stop()
        alarms.keys.toList().forEach { alarms[it] = AlarmRules.acknowledge(alarms.getValue(it)) }
        _state.update { s ->
            s.copy(
                link = Link.IDLE,
                lostWhileCooking = false,
                probes = s.probes.map { it.copy(celsius = null, ringing = false) },
            )
        }
    }

    // ---- user actions -------------------------------------------------------

    fun setTarget(probe: Int, target: Target?) {
        prefs.setTarget(probe, target)
        alarms[probe] = AlarmRules.State()
        _state.update { s ->
            s.copy(probes = s.probes.map {
                if (it.number == probe) it.copy(target = target, ringing = false) else it
            })
        }
        sendTarget(probe, target)
        reevaluate()
    }

    fun setUnit(unit: TempUnit) {
        prefs.unit = unit
        _state.update { it.copy(unit = unit) }
        if (_state.value.link == Link.CONNECTED) sendSetup()
    }

    /** Silence every ringing probe here and on the thermometer itself. */
    fun stopAlarm() {
        alarms.keys.toList().forEach { alarms[it] = AlarmRules.acknowledge(alarms.getValue(it)) }
        _state.update { s -> s.copy(probes = s.probes.map { it.copy(ringing = false) }) }
        if (_state.value.link == Link.CONNECTED) connection.send(BfourProtocol.stopAlarm())
    }

    fun forgetDevice() {
        prefs.deviceAddress = null
        prefs.deviceType = null
        connection.forget()
    }

    // ---- thermometer events -------------------------------------------------

    private val connectionListener = object : BfourConnection.Listener {
        override fun onLink(link: Link) {
            val wasConnected = _state.value.link == Link.CONNECTED
            if (link == Link.CONNECTED) {
                disconnectedAt = 0L
                lastDataAt = SystemClock.elapsedRealtime()
            } else if (wasConnected) {
                disconnectedAt = SystemClock.elapsedRealtime()
            }
            _state.update { s ->
                s.copy(
                    link = link,
                    lostWhileCooking = if (link == Link.CONNECTED) false else s.lostWhileCooking,
                    probes = if (link == Link.CONNECTED) s.probes
                    else s.probes.map { it.copy(celsius = null) },
                )
            }
        }

        override fun onDeviceFound(address: String, deviceType: Int?) {
            prefs.deviceAddress = address
            prefs.deviceType = deviceType
            val count = BfourProtocol.probeCount(deviceType)
            _state.update { s ->
                s.copy(
                    model = BfourProtocol.modelName(deviceType),
                    probes = if (s.probes.size == count) s.probes else buildProbes(count),
                )
            }
        }

        override fun onReady() = sendSetup()

        override fun onData(data: ByteArray) {
            val count = _state.value.probes.size
            when (val msg = BfourProtocol.parse(data, count)) {
                is BfourProtocol.Message.Temperatures -> onTemperatures(msg.celsius)
                BfourProtocol.Message.AlarmDismissedOnDevice -> stopAlarm()
                else -> Unit
            }
        }
    }

    private fun onTemperatures(temps: List<Double?>) {
        val now = SystemClock.elapsedRealtime()
        lastDataAt = now
        val wallNow = System.currentTimeMillis()
        val addSample = now - lastSampleAt >= SAMPLE_EVERY_MS
        if (addSample) lastSampleAt = now
        _state.update { s ->
            s.copy(probes = s.probes.mapIndexed { i, p ->
                val t = temps.getOrNull(i)
                val history = when {
                    t == null -> p.history
                    addSample -> (p.history + Sample(wallNow, t)).takeLast(MAX_SAMPLES)
                    else -> p.history
                }
                p.copy(celsius = t, history = history)
            })
        }
        reevaluate()
    }

    private fun reevaluate() {
        val fired = mutableListOf<Event>()
        _state.update { s ->
            s.copy(probes = s.probes.map { p ->
                val r = AlarmRules.evaluate(
                    alarms[p.number] ?: AlarmRules.State(),
                    p.celsius,
                    p.target?.celsius,
                )
                alarms[p.number] = r.state
                when (r.event) {
                    AlarmRules.Event.ALMOST -> fired += Event.Almost(p.number)
                    AlarmRules.Event.DONE -> fired += Event.Done(p.number)
                    null -> Unit
                }
                p.copy(ringing = r.state.ringing)
            })
        }
        fired.forEach { _events.tryEmit(it) }
    }

    private fun sendSetup() {
        val s = _state.value
        connection.send(BfourProtocol.setDisplayUnit(s.unit == TempUnit.F))
        s.probes.forEach { sendTarget(it.number, it.target) }
        connection.send(BfourProtocol.setAlarmInterval())
    }

    private fun sendTarget(probe: Int, target: Target?) {
        if (_state.value.link != Link.CONNECTED) return
        val degrees = target?.let { _state.value.unit.wholeDegrees(it.celsius) } ?: 0
        connection.send(BfourProtocol.setTarget(probe, degrees))
    }

    /** Catches a silent link and a thermometer that stays out of reach. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            val s = _state.value
            if (s.link == Link.CONNECTED) {
                val silentFor = now - lastDataAt
                if (silentFor > STALE_MS && s.probes.any { it.celsius != null }) {
                    _state.update { st -> st.copy(probes = st.probes.map { it.copy(celsius = null) }) }
                }
                if (silentFor > RECONNECT_AFTER_MS) connection.reconnect()
            } else if (disconnectedAt != 0L && !s.lostWhileCooking &&
                now - disconnectedAt > LOST_ALERT_MS &&
                s.probes.any { p -> p.target != null && alarms[p.number]?.done != true }
            ) {
                _state.update { it.copy(lostWhileCooking = true) }
                _events.tryEmit(Event.ConnectionLost)
            }
            main.postDelayed(this, WATCHDOG_MS)
        }
    }

    private fun buildProbes(count: Int) = (1..count).map { n ->
        ProbeState(
            number = n,
            celsius = null,
            target = prefs.target(n),
            history = emptyList(),
            ringing = false,
        )
    }

    private const val SAMPLE_EVERY_MS = 5_000L
    private const val MAX_SAMPLES = 12 * 60 * 2 // two hours at one sample per 5 s
    private const val STALE_MS = 6_000L
    private const val RECONNECT_AFTER_MS = 20_000L
    private const val LOST_ALERT_MS = 30_000L
    private const val WATCHDOG_MS = 2_000L
}

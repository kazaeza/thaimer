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
import java.io.File

/**
 * Process-wide brain of the app: talks to the thermometer, keeps the UI state
 * and the temperature history, decides when to alarm. The foreground service
 * turns [events] into sound and notifications; the UI only reads [state] and
 * [history] and calls the actions below.
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

    private val _history = MutableStateFlow(LogView.EMPTY)

    /** Every reading of the current and recent cooks; see [TempLog]. */
    val history: StateFlow<LogView> = _history.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private lateinit var prefs: Prefs
    private lateinit var connection: BfourConnection
    private lateinit var log: TempLog
    private lateinit var logFile: TempLogFile
    private val main = Handler(Looper.getMainLooper())

    private val alarms = mutableMapOf<Int, AlarmRules.State>()
    private var lastDataAt = 0L
    private var lastSampleAt = 0L
    private var disconnectedAt = 0L
    private var running = false

    // The history file is read in the background; readings that arrive meanwhile
    // are kept in memory and merged after the saved ones.
    private var loading = false
    private var loadToken = 0

    // Wall-clock time that can't jump while the app runs, so the chart never
    // folds back on itself if the phone's clock is changed.
    private var wallAtStart = 0L
    private var elapsedAtStart = 0L

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val app = context.applicationContext
        prefs = Prefs(app)
        connection = BfourConnection(app, connectionListener)
        wallAtStart = System.currentTimeMillis()
        elapsedAtStart = SystemClock.elapsedRealtime()

        val count = BfourProtocol.probeCount(prefs.deviceType)
        log = TempLog(count)
        logFile = TempLogFile(File(app.filesDir, LOG_FILE))
        startHistoryLoad(count)
        _state.value = ThermoState(
            model = BfourProtocol.modelName(prefs.deviceType),
            unit = prefs.unit,
            probes = buildProbes(count),
        )
    }

    val isRunning: Boolean get() = running

    /** Wall-clock milliseconds used for history rows and the chart's "now". */
    fun nowMs(): Long = wallAtStart + (SystemClock.elapsedRealtime() - elapsedAtStart)

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

    /** A target as it was before a change, with its alarm state, so the change can be undone. */
    class TargetChange internal constructor(
        val probe: Int,
        internal val target: Target?,
        internal val alarm: AlarmRules.State,
    )

    /**
     * Changes only the target and its alarm; the temperature history is untouched.
     * Returns what was there before, for [restore].
     */
    fun setTarget(probe: Int, target: Target?): TargetChange {
        val before = TargetChange(
            probe,
            _state.value.probes.firstOrNull { it.number == probe }?.target,
            alarms[probe] ?: AlarmRules.State(),
        )
        applyTarget(probe, target, AlarmRules.State(target = target?.celsius))
        return before
    }

    /** Puts a target back exactly as it was, including an alarm that was already silenced. */
    fun restore(change: TargetChange) {
        applyTarget(change.probe, change.target, change.alarm)
    }

    private fun applyTarget(probe: Int, target: Target?, alarm: AlarmRules.State) {
        prefs.setTarget(probe, target)
        alarms[probe] = alarm
        _state.update { s ->
            s.copy(probes = s.probes.map {
                if (it.number == probe) it.copy(target = target, ringing = alarm.ringing) else it
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

    /** The only way the chart is ever emptied: an explicit, confirmed user action. */
    fun clearHistory() {
        loadToken++ // a read still in progress must not bring the old rows back
        loading = false
        log.clear()
        logFile.delete()
        _history.value = log.view()
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
            // A scan that finds our own thermometer again may not carry the model
            // in its advertisement; keep what we already know.
            val type = deviceType ?: prefs.deviceType.takeIf { address.equals(prefs.deviceAddress, true) }
            prefs.deviceAddress = address
            prefs.deviceType = type
            val count = BfourProtocol.probeCount(type)
            if (count != log.probeCount) replaceLog(log.resized(count))
            _state.update { s ->
                s.copy(
                    model = BfourProtocol.modelName(type),
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
        _state.update { s ->
            s.copy(probes = s.probes.mapIndexed { i, p -> p.copy(celsius = temps.getOrNull(i)) })
        }
        if (now - lastSampleAt >= SAMPLE_EVERY_MS && temps.any { it != null }) {
            lastSampleAt = now
            record(temps)
        }
        reevaluate()
    }

    private fun record(temps: List<Double?>) {
        val t = nowMs()
        if (!log.append(t, temps)) return
        logFile.append(t, temps)
        val oldest = log.oldestTimeMs
        if (!loading && oldest != null && oldest < t - RETENTION_MS - TRIM_SLACK_MS) {
            log.dropBefore(t - RETENTION_MS)
            logFile.rewrite(log.view())
        }
        _history.value = log.view()
    }

    private fun startHistoryLoad(count: Int) {
        loading = true
        val token = ++loadToken
        logFile.load(count, nowMs(), RETENTION_MS) { loaded -> main.post { onHistoryLoaded(token, loaded) } }
    }

    private fun onHistoryLoaded(token: Int, loaded: TempLog) {
        if (token != loadToken) return
        loading = false
        // Readings that arrived while the file was being read come after the saved ones.
        val fresh = log.view()
        val resized = loaded.probeCount != fresh.probeCount
        val merged = if (resized) loaded.resized(fresh.probeCount) else loaded
        for (row in 0 until fresh.size) {
            merged.append(fresh.time(row), (1..fresh.probeCount).map { p ->
                fresh.temp(p, row).takeUnless { it.isNaN() }?.toDouble()
            })
        }
        log = merged
        // Rows appended to the file meanwhile already follow the saved ones, unless the columns changed.
        if (resized) logFile.rewrite(merged.view())
        _history.value = log.view()
    }

    /** Swaps in a different history (e.g. another probe count) and makes the file match it. */
    private fun replaceLog(newLog: TempLog) {
        log = newLog
        logFile.rewrite(log.view())
        _history.value = log.view()
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
        ProbeState(number = n, celsius = null, target = prefs.target(n), ringing = false)
    }

    private const val LOG_FILE = "temperature-log.csv"
    private const val SAMPLE_EVERY_MS = 5_000L
    private const val RETENTION_MS = 48 * 60 * 60_000L
    private const val TRIM_SLACK_MS = 6 * 60 * 60_000L
    private const val STALE_MS = 6_000L
    private const val RECONNECT_AFTER_MS = 20_000L
    private const val LOST_ALERT_MS = 30_000L
    private const val WATCHDOG_MS = 2_000L
}

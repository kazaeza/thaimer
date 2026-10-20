package com.thaimer.bfour.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.thaimer.bfour.core.Thermo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the Bluetooth link alive while the screen is off and turns
 * [Thermo.events] into sound and notifications.
 */
class ThermoService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifier: Notifier
    private lateinit var sound: AlarmSound
    private var ringing = false
    private var lastStatusAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Thermo.init(this)
        notifier = Notifier(this).also { it.createChannels() }
        sound = AlarmSound(this)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, Notifier.ID_STATUS, notifier.status(Thermo.state.value), type)
        } catch (e: Exception) {
            // Missing Bluetooth permission or a background start the system refused.
            Log.w(TAG, "cannot start in foreground", e)
            stopSelf()
            return
        }

        Thermo.start()
        scope.launch { Thermo.state.collect { onState() } }
        scope.launch {
            Thermo.events.collect { e ->
                val s = Thermo.state.value
                when (e) {
                    is Thermo.Event.Almost -> s.probes.firstOrNull { it.number == e.probe }
                        ?.let { notifier.showAlmost(s, it) }
                    is Thermo.Event.ConnectionLost -> notifier.showLost()
                    is Thermo.Event.Done -> Unit // handled by the ringing state
                }
            }
        }
    }

    private fun onState() {
        val s = Thermo.state.value
        val nowRinging = s.ringingProbes.isNotEmpty()
        if (nowRinging) notifier.showDone(s)
        if (nowRinging != ringing) {
            ringing = nowRinging
            if (nowRinging) {
                sound.start()
            } else {
                sound.stop()
                notifier.cancelDone()
            }
        }
        if (!s.lostWhileCooking) notifier.cancelLost()

        // The thermometer reports every second; the notification doesn't need to.
        val now = SystemClock.elapsedRealtime()
        if (now - lastStatusAt >= STATUS_EVERY_MS) {
            lastStatusAt = now
            ContextCompat.getSystemService(this, android.app.NotificationManager::class.java)
                ?.notify(Notifier.ID_STATUS, notifier.status(s))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> Thermo.stopAlarm()
            ACTION_SHUTDOWN -> {
                Thermo.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (::sound.isInitialized) sound.stop()
        if (::notifier.isInitialized) {
            notifier.cancelDone()
            notifier.cancelLost()
        }
        Thermo.stop()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ThermoService"
        private const val STATUS_EVERY_MS = 3_000L
        const val ACTION_STOP_ALARM = "com.thaimer.bfour.STOP_ALARM"
        const val ACTION_SHUTDOWN = "com.thaimer.bfour.SHUTDOWN"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ThermoService::class.java))
        }

        fun shutdown(context: Context) {
            if (!Thermo.isRunning) return
            context.startService(Intent(context, ThermoService::class.java).setAction(ACTION_SHUTDOWN))
        }
    }
}

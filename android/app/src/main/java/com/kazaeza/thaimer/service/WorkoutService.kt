package com.kazaeza.thaimer.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kazaeza.thaimer.ThaimerApp
import com.kazaeza.thaimer.engine.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the workout alive with the screen off: foreground status so the process is not frozen,
 * a partial wake lock while the clock runs so cues fire on time, and the lock-screen notification.
 */
class WorkoutService : Service() {
    private val scope = MainScope()
    private var watcher: Job? = null
    private var shutdown: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notifier: SessionNotifier

    private val controller get() = (application as ThaimerApp).controller

    override fun onCreate() {
        super.onCreate()
        notifier = SessionNotifier(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() demands this promptly, whatever happens next.
        ServiceCompat.startForeground(
            this, SessionNotifier.ID, notifier.build(controller.state.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        when (intent?.action) {
            ACTION_TOGGLE -> controller.togglePause()
            ACTION_NEXT -> controller.next()
            ACTION_STOP -> controller.stop()
        }
        if (watcher == null) watcher = scope.launch { controller.state.collect(::render) }
        return START_NOT_STICKY
    }

    private fun render(session: Session?) {
        shutdown?.cancel()
        when {
            session == null -> stopNow()
            session.finished -> {
                // Let the final bell and "well done" play out before letting the process sleep.
                notifier.show(session)
                shutdown = scope.launch {
                    delay(6_000)
                    stopNow()
                }
            }
            else -> {
                notifier.show(session)
                if (session.running) acquireWakeLock() else releaseWakeLock()
            }
        }
    }

    private fun stopNow() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "thaimer:session")
            .apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 60 * 1000L)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "com.kazaeza.thaimer.TOGGLE"
        const val ACTION_NEXT = "com.kazaeza.thaimer.NEXT"
        const val ACTION_STOP = "com.kazaeza.thaimer.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java))
        }
    }
}

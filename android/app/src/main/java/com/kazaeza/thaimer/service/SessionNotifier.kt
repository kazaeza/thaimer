package com.kazaeza.thaimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.kazaeza.thaimer.MainActivity
import com.kazaeza.thaimer.R
import com.kazaeza.thaimer.engine.Session
import com.kazaeza.thaimer.model.BlockType

/** The lock-screen card: current interval with a live countdown, what's next, and controls. */
class SessionNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // Default importance so the card shows on the lock screen; silent, because the app's own
        // bells and voice already do the talking.
        val channel = NotificationChannel(CHANNEL, "Тренировка", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Таймер во время тренировки"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun show(session: Session?) = manager.notify(ID, build(session))

    fun build(session: Session?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_thaimer)
            .setColor(0xFFFF7A00.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
        if (session == null) return builder.setContentTitle("thaimer").setContentText("Тренировка").build()
        if (session.finished) {
            return builder.setContentTitle("Готово! Тренировка окончена 💪")
                .setContentText(session.timeline.workout.title)
                .setOngoing(false)
                .build()
        }

        val timeline = session.timeline
        val elapsed = session.elapsedAt(SystemClock.elapsedRealtime())
        val segment = timeline.segments[timeline.indexAt(elapsed)]
        val block = timeline.blockOf(segment)
        val title = buildString {
            if (!session.running) append("Пауза · ")
            if (block.type == BlockType.ROUND) append("Раунд ${block.round}/${timeline.totalRounds} · ")
            append(segment.step.name)
        }
        val next = timeline.segments.getOrNull(segment.index + 1)
        val text = when {
            next == null -> "Последний отрезок"
            next.step.surprise -> "Далее: ???"
            else -> "Далее: ${next.step.name}"
        }
        builder.setContentTitle(title).setContentText(text).setSubText(timeline.workout.title)
        if (session.running) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + (timeline.countdownTarget(segment) - elapsed))
        } else {
            builder.setShowWhen(false)
        }
        builder.addAction(
            if (session.running) R.drawable.ic_pause else R.drawable.ic_play,
            if (session.running) "Пауза" else "Продолжить",
            serviceIntent(WorkoutService.ACTION_TOGGLE),
        )
        builder.addAction(R.drawable.ic_next, "Дальше", serviceIntent(WorkoutService.ACTION_NEXT))
        builder.addAction(R.drawable.ic_stop, "Стоп", serviceIntent(WorkoutService.ACTION_STOP))
        return builder.build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(),
        Intent(context, WorkoutService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL = "session"
        const val ID = 7
    }
}

package com.thaimer.bfour.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.thaimer.bfour.R
import com.thaimer.bfour.core.Link
import com.thaimer.bfour.core.ProbeState
import com.thaimer.bfour.core.ThermoState
import com.thaimer.bfour.core.linkText
import com.thaimer.bfour.core.probeName
import com.thaimer.bfour.ui.MainActivity

class Notifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_STATUS, context.getString(R.string.ch_status), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
                // AlarmSound plays the sound itself on the alarm stream.
                NotificationChannel(CH_ALARM, context.getString(R.string.ch_alarm), NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        setSound(null, null)
                        enableVibration(false)
                    },
                NotificationChannel(CH_ALERTS, context.getString(R.string.ch_alerts), NotificationManager.IMPORTANCE_HIGH),
            )
        )
    }

    fun status(s: ThermoState): Notification {
        val title = "${s.model} · ${linkText(context, s.link)}"
        val text = if (s.link == Link.CONNECTED) {
            s.probes.joinToString("   ") { p -> "${shortName(p, s)} ${probeLine(p, s)}" }
        } else {
            null
        }
        return NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_thermo)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(0, alarm = false))
            .addAction(0, context.getString(R.string.notif_shutdown), serviceIntent(ThermoService.ACTION_SHUTDOWN))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun showDone(s: ThermoState) {
        val ringing = s.ringingProbes
        if (ringing.isEmpty()) return cancelDone()
        val text = ringing.joinToString("\n") { p ->
            context.getString(
                R.string.notif_done_text,
                probeName(context, p.number, s.probes.size),
                p.celsius?.let { s.unit.format(it) } ?: context.getString(R.string.no_value),
                p.target?.let { s.unit.formatWhole(it.celsius) } ?: "",
            )
        }
        val n = NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_thermo)
            .setContentTitle(context.getString(R.string.alarm_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(1, alarm = true))
            .setFullScreenIntent(openApp(2, alarm = true), true)
            .addAction(0, context.getString(R.string.alarm_stop), serviceIntent(ThermoService.ACTION_STOP_ALARM))
            .setDeleteIntent(serviceIntent(ThermoService.ACTION_STOP_ALARM))
            .build()
        nm.notify(ID_DONE, n)
    }

    fun cancelDone() = nm.cancel(ID_DONE)

    fun showAlmost(s: ThermoState, probe: ProbeState) {
        val target = probe.target ?: return
        val text = context.getString(
            R.string.notif_almost_text,
            probeName(context, probe.number, s.probes.size),
            probe.celsius?.let { s.unit.format(it) } ?: context.getString(R.string.no_value),
            s.unit.formatWhole(target.celsius),
        )
        alert(ID_ALMOST + probe.number, context.getString(R.string.notif_almost_title), text)
    }

    fun showLost() = alert(
        ID_LOST,
        context.getString(R.string.notif_lost_title),
        context.getString(R.string.notif_lost_text),
    )

    fun cancelLost() = nm.cancel(ID_LOST)

    private fun alert(id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_thermo)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(3, alarm = false))
            .build()
        nm.notify(id, n)
    }

    private fun shortName(p: ProbeState, s: ThermoState): String =
        probeName(context, p.number, s.probes.size).substringBefore(' ')

    private fun probeLine(p: ProbeState, s: ThermoState): String {
        val now = p.celsius?.let { s.unit.format(it) } ?: context.getString(R.string.no_value)
        val target = p.target ?: return now
        return "$now → ${s.unit.formatWhole(target.celsius)}"
    }

    private fun openApp(requestCode: Int, alarm: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ALARM, alarm)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceIntent(action: String): PendingIntent = PendingIntent.getService(
        context, action.hashCode(),
        Intent(context, ThermoService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ID_STATUS = 1
        const val ID_DONE = 2
        const val ID_LOST = 3
        const val ID_ALMOST = 10
        private const val CH_STATUS = "status"
        private const val CH_ALARM = "alarm"
        private const val CH_ALERTS = "alerts"
    }
}

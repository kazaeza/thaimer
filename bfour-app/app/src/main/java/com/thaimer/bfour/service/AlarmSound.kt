package com.thaimer.bfour.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Loud looping alarm on the alarm stream (plays in silent mode) plus vibration.
 * Stops by itself after [MAX_RING_MS] so a forgotten phone doesn't ring forever.
 */
class AlarmSound(private val context: Context) {
    private var player: MediaPlayer? = null
    private var active = false
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    private val autoStop = Runnable { stop() }
    private val handler = Handler(Looper.getMainLooper())

    fun start() {
        if (active) return
        active = true
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        handler.postDelayed(autoStop, MAX_RING_MS)
    }

    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacks(autoStop)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator?.cancel()
    }

    private companion object {
        const val MAX_RING_MS = 10 * 60_000L
    }
}

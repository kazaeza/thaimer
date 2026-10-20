package com.thaimer.bfour.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Loud looping alarm on the alarm stream (plays in silent mode) plus vibration.
 * Falls back to other system sounds, and finally to a generated beep, so the
 * alarm is never silent. Stops by itself after [MAX_RING_MS] so a forgotten
 * phone doesn't ring forever.
 */
class AlarmSound(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val autoStop = Runnable { stop() }

    fun start() {
        if (active) return
        active = true
        player = sequenceOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_RINGTONE,
            RingtoneManager.TYPE_NOTIFICATION,
        ).mapNotNull { RingtoneManager.getDefaultUri(it) }.firstNotNullOfOrNull { play(it) }
        if (player == null) startBeeping()
        vibrate()
        handler.postDelayed(autoStop, MAX_RING_MS)
    }

    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacks(autoStop)
        handler.removeCallbacks(beep)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        tone?.release()
        tone = null
        vibrator?.cancel()
    }

    private fun play(uri: Uri): MediaPlayer? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(alarmAttributes)
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            mp
        } catch (e: Exception) {
            Log.w(TAG, "cannot play $uri", e)
            mp.release()
            null
        }
    }

    private fun startBeeping() {
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull()
        handler.post(beep)
    }

    private val beep = object : Runnable {
        override fun run() {
            if (!active) return
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_MS)
            handler.postDelayed(this, BEEP_EVERY_MS)
        }
    }

    private fun vibrate() {
        val v = vibrator ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, alarmAttributes)
        }
    }

    private companion object {
        const val TAG = "AlarmSound"
        const val MAX_RING_MS = 10 * 60_000L
        const val BEEP_MS = 700
        const val BEEP_EVERY_MS = 1_200L
    }
}

package com.kazaeza.thaimer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kazaeza.thaimer.R
import com.kazaeza.thaimer.data.Settings
import com.kazaeza.thaimer.engine.CueSink
import com.kazaeza.thaimer.model.Buzz
import com.kazaeza.thaimer.model.Cue
import com.kazaeza.thaimer.model.Sound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceStatus { LOADING, READY, MISSING_RUSSIAN, UNAVAILABLE }

/**
 * Bells and beeps through SoundPool, Russian voice through the system TTS, plus vibration.
 * While anything plays it holds transient audio focus with ducking, so music in another app
 * dips under the coach instead of drowning it.
 */
class CuePlayer(context: Context, private val settings: () -> Settings) : CueSink {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(AudioManager::class.java)

    private val effects = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val speech = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(effects).build()
    private val sounds: Map<Sound, Int> = mapOf(
        Sound.BELL to pool.load(app, R.raw.bell, 1),
        Sound.BELL_END to pool.load(app, R.raw.bell_end, 1),
        Sound.CLAPPER to pool.load(app, R.raw.clapper, 1),
        Sound.BURST to pool.load(app, R.raw.burst, 1),
        Sound.GO to pool.load(app, R.raw.go, 1),
        Sound.PIP to pool.load(app, R.raw.pip, 1),
    )
    private val soundLengthMs = mapOf(
        Sound.BELL to 2_440L, Sound.BELL_END to 3_040L, Sound.CLAPPER to 360L,
        Sound.BURST to 760L, Sound.GO to 590L, Sound.PIP to 200L,
    )
    private val streams = ArrayDeque<Int>()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)

    private var tts: TextToSpeech? = null
    private val _voiceStatus = MutableStateFlow(VoiceStatus.LOADING)
    val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()
    private var utterance = 0
    @Volatile private var speakingId: String? = null

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(effects)
        .setOnAudioFocusChangeListener { }
        .build()
    private var focusHeld = false
    private var releaseAt = 0L
    private val releaseFocus = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            when {
                speakingId != null -> Unit // the utterance callback re-posts us
                now < releaseAt -> main.postAtTime(this, releaseAt)
                focusHeld -> {
                    audio.abandonAudioFocusRequest(focus)
                    focusHeld = false
                }
            }
        }
    }

    /** Binds the TTS engine early so the first announcement is not lost. */
    fun warmUp() {
        if (tts != null) return
        _voiceStatus.value = VoiceStatus.LOADING
        tts = TextToSpeech(app) { status -> main.post { onTtsReady(status) } }
    }

    private var voiceInstallPending = false

    /** The user is off to the system screen to download a Russian voice. */
    fun expectVoiceInstall() {
        voiceInstallPending = true
    }

    /** On return to the app: re-bind the engine once if a voice was being installed. */
    fun refreshIfMissing() {
        if (!voiceInstallPending) return
        voiceInstallPending = false
        if (_voiceStatus.value == VoiceStatus.MISSING_RUSSIAN || _voiceStatus.value == VoiceStatus.UNAVAILABLE) reloadVoice()
    }

    private fun reloadVoice() {
        tts?.shutdown()
        tts = null
        warmUp()
    }

    private fun onTtsReady(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            _voiceStatus.value = VoiceStatus.UNAVAILABLE
            return
        }
        val result = engine.setLanguage(Locale.forLanguageTag("ru-RU"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            _voiceStatus.value = VoiceStatus.MISSING_RUSSIAN
            return
        }
        // An installed offline voice answers instantly and works without internet.
        runCatching {
            engine.voices
                ?.filter {
                    it.locale.language == "ru" && !it.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features
                }
                ?.maxByOrNull { it.quality }
                ?.let { engine.voice = it }
        }
        engine.setSpeechRate(1.08f)
        engine.setAudioAttributes(speech)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finished(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId)
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String) = finished(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) = finished(utteranceId)
        })
        _voiceStatus.value = VoiceStatus.READY
    }

    private fun finished(utteranceId: String) {
        main.post {
            if (speakingId == utteranceId) {
                speakingId = null
                holdFocus(250)
            }
        }
    }

    override fun play(cue: Cue) {
        val s = settings()
        var busyMs = 0L
        if (s.sounds) cue.sound?.let { sound ->
            sounds[sound]?.let { id ->
                val stream = pool.play(id, 1f, 1f, 1, 0, 1f)
                if (stream != 0) {
                    streams.addLast(stream)
                    if (streams.size > 8) streams.removeFirst()
                }
            }
            busyMs = soundLengthMs[sound] ?: 0
        }
        val text = cue.speech
        if (s.voice && text != null && _voiceStatus.value == VoiceStatus.READY) {
            // Let the bell or whistle hit first, then talk over its tail.
            val lead = if (s.sounds && cue.sound != null && cue.sound != Sound.GO && cue.sound != Sound.PIP) 350L else 0L
            speak(text, lead)
        }
        if (s.vibration) cue.buzz?.let(::vibrate)
        if (busyMs > 0 || speakingId != null) holdFocus(busyMs)
    }

    private fun speak(text: String, leadMs: Long) {
        val engine = tts ?: return
        val id = "cue-${++utterance}"
        speakingId = id
        // If the engine never reports back, don't keep other apps' music ducked forever.
        main.postDelayed({
            if (speakingId == id) {
                speakingId = null
                holdFocus(0)
            }
        }, 10_000)
        if (leadMs > 0) {
            engine.playSilentUtterance(leadMs, TextToSpeech.QUEUE_FLUSH, "lead-$utterance")
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, id)
        } else {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    override fun silence() {
        streams.forEach(pool::stop)
        streams.clear()
        speakingId = null
        tts?.stop()
        main.removeCallbacks(releaseFocus)
        releaseAt = 0
        main.post(releaseFocus)
    }

    private fun holdFocus(forMs: Long) {
        if (!focusHeld) {
            focusHeld = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        releaseAt = maxOf(releaseAt, SystemClock.uptimeMillis() + forMs + 300)
        main.removeCallbacks(releaseFocus)
        main.postAtTime(releaseFocus, releaseAt)
    }

    private fun vibrate(buzz: Buzz) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = when (buzz) {
            Buzz.SHORT -> VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
            Buzz.DOUBLE -> VibrationEffect.createWaveform(longArrayOf(0, 90, 70, 90), -1)
            Buzz.LONG -> VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, effects)
        }
    }
}

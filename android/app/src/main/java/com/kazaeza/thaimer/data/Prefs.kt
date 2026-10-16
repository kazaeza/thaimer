package com.kazaeza.thaimer.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val voice: Boolean = true,
    val sounds: Boolean = true,
    val vibration: Boolean = true,
    val tenSecondWarning: Boolean = true,
    val prepSeconds: Int = 10,
)

data class Stats(
    val workouts: Int = 0,
    val seconds: Long = 0,
    val lastDone: Map<String, Long> = emptyMap(),
    val timesDone: Map<String, Int> = emptyMap(),
)

/** Settings and training history, persisted in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("thaimer", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(readStats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    var askedForNotifications: Boolean
        get() = sp.getBoolean(ASKED_NOTIFICATIONS, false)
        set(value) = sp.edit { putBoolean(ASKED_NOTIFICATIONS, value) }

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        sp.edit {
            putBoolean(VOICE, s.voice)
            putBoolean(SOUNDS, s.sounds)
            putBoolean(VIBRATION, s.vibration)
            putBoolean(TEN_SECONDS, s.tenSecondWarning)
            putInt(PREP, s.prepSeconds)
        }
        _settings.value = s
    }

    fun recordWorkout(workoutId: String, workedSeconds: Long, now: Long = System.currentTimeMillis()) {
        sp.edit {
            putInt(COUNT, sp.getInt(COUNT, 0) + 1)
            putLong(SECONDS, sp.getLong(SECONDS, 0) + workedSeconds)
            putLong(LAST_PREFIX + workoutId, now)
            putInt(TIMES_PREFIX + workoutId, sp.getInt(TIMES_PREFIX + workoutId, 0) + 1)
        }
        _stats.value = readStats()
    }

    private fun readSettings() = Settings(
        voice = sp.getBoolean(VOICE, true),
        sounds = sp.getBoolean(SOUNDS, true),
        vibration = sp.getBoolean(VIBRATION, true),
        tenSecondWarning = sp.getBoolean(TEN_SECONDS, true),
        prepSeconds = sp.getInt(PREP, 10),
    )

    private fun readStats(): Stats {
        val ids = Workouts.all.map { it.id }
        return Stats(
            workouts = sp.getInt(COUNT, 0),
            seconds = sp.getLong(SECONDS, 0),
            lastDone = ids.mapNotNull { id -> sp.getLong(LAST_PREFIX + id, 0).takeIf { it > 0 }?.let { id to it } }.toMap(),
            timesDone = ids.mapNotNull { id -> sp.getInt(TIMES_PREFIX + id, 0).takeIf { it > 0 }?.let { id to it } }.toMap(),
        )
    }

    private companion object {
        const val VOICE = "voice"
        const val SOUNDS = "sounds"
        const val VIBRATION = "vibration"
        const val TEN_SECONDS = "ten_seconds"
        const val PREP = "prep_seconds"
        const val ASKED_NOTIFICATIONS = "asked_notifications"
        const val COUNT = "stats.count"
        const val SECONDS = "stats.seconds"
        const val LAST_PREFIX = "last."
        const val TIMES_PREFIX = "times."
    }
}

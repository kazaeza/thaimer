package com.kazaeza.thaimer

import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.kazaeza.thaimer.audio.CuePlayer
import com.kazaeza.thaimer.data.Prefs
import com.kazaeza.thaimer.engine.SessionController
import com.kazaeza.thaimer.model.Workout
import com.kazaeza.thaimer.service.WorkoutService
import kotlinx.coroutines.MainScope

class ThaimerApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var player: CuePlayer
        private set
    lateinit var controller: SessionController
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        player = CuePlayer(this) { prefs.settings.value }
        controller = SessionController(MainScope(), player, SystemClock::elapsedRealtime) { done ->
            // Count it only if most of it was actually done, not skipped through.
            if (done.workedMs * 2 >= done.timeline.totalMs) {
                prefs.recordWorkout(done.timeline.workout.id, done.workedMs / 1000)
            }
        }
    }

    fun startWorkout(context: Context, workout: Workout) {
        val settings = prefs.settings.value
        controller.start(workout, settings.prepSeconds, settings.tenSecondWarning)
        WorkoutService.start(context)
    }
}

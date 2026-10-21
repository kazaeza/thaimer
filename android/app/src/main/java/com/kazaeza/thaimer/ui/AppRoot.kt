package com.kazaeza.thaimer.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazaeza.thaimer.ThaimerApp
import com.kazaeza.thaimer.audio.VoiceStatus
import com.kazaeza.thaimer.data.Workouts
import com.kazaeza.thaimer.engine.Session
import com.kazaeza.thaimer.model.Workout
import com.kazaeza.thaimer.ui.detail.DetailScreen
import com.kazaeza.thaimer.ui.finish.FinishScreen
import com.kazaeza.thaimer.ui.home.HomeScreen
import com.kazaeza.thaimer.ui.intro.IntroScreen
import com.kazaeza.thaimer.ui.session.SessionActions
import com.kazaeza.thaimer.ui.session.SessionScreen
import com.kazaeza.thaimer.ui.settings.SettingsSheet
import com.kazaeza.thaimer.ui.theme.Thaimer

private enum class Screen { INTRO, HOME, DETAIL, SESSION, FINISH }

@Composable
fun AppRoot(app: ThaimerApp) {
    val context = LocalContext.current
    val controller = app.controller
    val session by controller.state.collectAsStateWithLifecycle()
    val settings by app.prefs.settings.collectAsStateWithLifecycle()
    val stats by app.prefs.stats.collectAsStateWithLifecycle()
    val voiceStatus by app.player.voiceStatus.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingStart by rememberSaveable { mutableStateOf<String?>(null) }
    // Once per launch; survives rotation and theme changes, skipped if a workout is already on.
    var showIntro by rememberSaveable { mutableStateOf(true) }

    fun begin(workout: Workout) = app.startWorkout(context, workout)

    // The lock-screen controls need the notification; ask once, start either way.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingStart?.let(Workouts::byId)?.let(::begin)
        pendingStart = null
    }
    fun requestStart(workout: Workout) {
        val needsAsk = Build.VERSION.SDK_INT >= 33 && !app.prefs.askedForNotifications &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            app.prefs.askedForNotifications = true
            pendingStart = workout.id
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            begin(workout)
        }
    }

    val current = session
    LaunchedEffect(current != null) { if (current != null) showIntro = false }
    val screen = when {
        showIntro && current == null -> Screen.INTRO
        current?.finished == true -> Screen.FINISH
        current != null -> Screen.SESSION
        openId?.let(Workouts::byId) != null -> Screen.DETAIL
        else -> Screen.HOME
    }
    // The outgoing screen keeps drawing its last session while it fades away.
    val lastSession = remember { arrayOfNulls<Session>(1) }
    if (current != null) lastSession[0] = current

    val actions = remember(controller) {
        SessionActions(
            onToggle = controller::togglePause,
            onNext = controller::next,
            onPrevious = controller::previous,
            onJump = controller::jumpTo,
            onPause = controller::pause,
            onStop = controller::stop,
            onToggleSound = {
                app.prefs.update {
                    val on = !(it.sounds || it.voice)
                    it.copy(sounds = on, voice = on)
                }
            },
        )
    }

    AnimatedContent(
        targetState = screen,
        // Opaque floor under the cross-fade, so the dark window never shows through mid-fade.
        modifier = Modifier.fillMaxSize().background(Thaimer.palette.background),
        transitionSpec = {
            if (initialState == Screen.INTRO) fadeIn(tween(450)) togetherWith fadeOut(tween(450))
            else fadeIn(tween(220)) togetherWith fadeOut(tween(160))
        },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.INTRO -> IntroScreen(onDone = { showIntro = false })
            Screen.HOME -> HomeScreen(
                groups = Workouts.groups,
                stats = stats,
                onOpen = { openId = it.id },
                onSettings = { showSettings = true },
            )
            Screen.DETAIL -> {
                val workout = openId?.let(Workouts::byId)
                if (workout != null) {
                    BackHandler { openId = null }
                    DetailScreen(
                        workout, settings,
                        onBack = { openId = null },
                        onStart = { requestStart(workout) },
                        voiceMissing = voiceStatus == VoiceStatus.MISSING_RUSSIAN || voiceStatus == VoiceStatus.UNAVAILABLE,
                    )
                }
            }
            Screen.SESSION -> lastSession[0]?.let {
                SessionScreen(it, soundOn = settings.sounds || settings.voice, actions = actions)
            }
            Screen.FINISH -> lastSession[0]?.takeIf { it.finished }?.let {
                FinishScreen(it, onDone = {
                    controller.stop()
                    openId = null
                })
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            voiceStatus = voiceStatus,
            onChange = app.prefs::update,
            onFixVoice = {
                app.player.expectVoiceInstall()
                val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            onDismiss = { showSettings = false },
        )
    }
}

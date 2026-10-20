package com.thaimer.bfour.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thaimer.bfour.core.Thermo
import com.thaimer.bfour.service.ThermoService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Thermo.init(this)
        showOverLockScreen(intent)
        setContent {
            BfourTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    App(onAlarmStopped = { showOverLockScreen(null) })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showOverLockScreen(intent)
    }

    /** Opened from the "done" alarm: light up the screen and show above the lock screen. */
    private fun showOverLockScreen(intent: Intent?) {
        val on = intent?.getBooleanExtra(EXTRA_ALARM, false) == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        } else {
            @Suppress("DEPRECATION")
            val flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (on) window.addFlags(flags) else window.clearFlags(flags)
        }
    }

    companion object {
        const val EXTRA_ALARM = "alarm"
    }
}

@Composable
private fun App(onAlarmStopped: () -> Unit) {
    val context = LocalContext.current
    val state by Thermo.state.collectAsStateWithLifecycle()
    val history by Thermo.history.collectAsStateWithLifecycle()
    var showChart by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(Permissions.granted(context)) }
    var askedBefore by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedBefore = true
        granted = Permissions.granted(context)
    }

    // The user may grant access in system settings and come back.
    LifecycleResumeEffect(Unit) {
        granted = Permissions.granted(context)
        onPauseOrDispose { }
    }
    // Opening the app means "watch my thermometer".
    LaunchedEffect(granted) {
        if (granted && !Thermo.isRunning) ThermoService.start(context)
    }

    Box(Modifier.fillMaxSize()) {
        if (granted) {
            if (showChart) {
                ChartScreen(
                    state = state,
                    log = history,
                    onBack = { showChart = false },
                    onClear = { Thermo.clearHistory() },
                )
            } else {
                MainScreen(state, history, onOpenChart = { showChart = true })
            }
        } else {
            PermissionScreen(
                askedBefore = askedBefore,
                onGrant = { launcher.launch(Permissions.toRequest) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                },
            )
        }
        if (state.ringingProbes.isNotEmpty()) {
            AlarmOverlay(state, onStop = {
                Thermo.stopAlarm()
                onAlarmStopped()
            })
        }
    }
}

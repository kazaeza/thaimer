package com.kazaeza.thaimer

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kazaeza.thaimer.ui.AppRoot
import com.kazaeza.thaimer.ui.theme.ThaimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Volume keys control the coach's voice and bells even between cues.
        volumeControlStream = AudioManager.STREAM_MUSIC
        val app = application as ThaimerApp
        app.player.warmUp()
        setContent {
            ThaimerTheme {
                AppRoot(app)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as ThaimerApp).player.refreshIfMissing()
    }
}

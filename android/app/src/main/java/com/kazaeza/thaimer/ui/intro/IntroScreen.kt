package com.kazaeza.thaimer.ui.intro

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kazaeza.thaimer.ui.components.ChampionLogo
import com.kazaeza.thaimer.ui.components.Embers
import com.kazaeza.thaimer.ui.components.rememberFireTime
import com.kazaeza.thaimer.ui.theme.Fire
import com.kazaeza.thaimer.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cold-start splash: the gym's fighter lit by fire, then the name. Tap to skip. */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val time = rememberFireTime()
    val logo = remember { Animatable(0f) }
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logo.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        delay(500)
        words.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        delay(1_300)
        onDone()
    }
    LightStatusBarIcons()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Fire.night, Fire.ember, Fire.night)))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Пропустить", onClick = onDone),
    ) {
        Embers(time, Modifier.fillMaxSize(), count = 28)
        Column(
            Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ChampionLogo(
                time,
                Modifier
                    .fillMaxWidth(0.82f)
                    .graphicsLayer {
                        alpha = logo.value
                        scaleX = 0.88f + 0.12f * logo.value
                        scaleY = 0.88f + 0.12f * logo.value
                    },
            )
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.graphicsLayer {
                    alpha = words.value
                    translationY = (1f - words.value) * 32.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("thaimer", style = Type.hero.copy(fontSize = 60.sp, lineHeight = 64.sp), color = Color.White)
                Text(
                    "มวยไทย · тайский бокс дома",
                    style = Type.body.copy(letterSpacing = 1.sp), color = Fire.gold, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Keeps status- and navigation-bar icons white while a dark, full-bleed screen is up. */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (status != null) controller.isAppearanceLightStatusBars = status
            if (navigation != null) controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

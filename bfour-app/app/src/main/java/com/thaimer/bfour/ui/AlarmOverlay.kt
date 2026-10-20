package com.thaimer.bfour.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaimer.bfour.R
import com.thaimer.bfour.core.ThermoState
import com.thaimer.bfour.core.probeName

/** Full-screen "it's done" with one huge button. Back also stops the alarm. */
@Composable
fun AlarmOverlay(state: ThermoState, onStop: () -> Unit) {
    BackHandler(onBack = onStop)
    val pulse = rememberInfiniteTransition(label = "pulse")
    val bg by pulse.animateColor(
        initialValue = Color(0xFFB3261E),
        targetValue = Color(0xFFE8590C),
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "bg",
    )
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            // Swallow taps so the screen underneath can't be used by accident.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(96.dp),
            )
            Text(
                stringResource(R.string.alarm_title),
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(16.dp))
            state.ringingProbes.forEach { p ->
                val temp = p.celsius?.let { state.unit.format(it) } ?: stringResource(R.string.no_value)
                Text(
                    "${probeName(context, p.number, state.probes.size)}: $temp",
                    color = Color.White,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(88.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFB3261E)),
            ) {
                Text(stringResource(R.string.alarm_stop), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

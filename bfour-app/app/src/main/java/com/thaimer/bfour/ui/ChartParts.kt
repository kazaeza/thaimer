package com.thaimer.bfour.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.thaimer.bfour.R
import com.thaimer.bfour.core.LogView
import com.thaimer.bfour.core.ProbeState
import com.thaimer.bfour.core.TempUnit
import com.thaimer.bfour.core.Thermo
import com.thaimer.bfour.core.Trend
import kotlinx.coroutines.delay
import java.util.Locale

/** Source of "now" for charts; screenshots replace it with a fixed time. */
val LocalClock = staticCompositionLocalOf<() -> Long> { { Thermo.nowMs() } }

/** Current time, ticking every second so charts scroll even without new readings. */
@Composable
fun rememberNow(): Long {
    val clock = LocalClock.current
    val now by produceState(clock()) {
        while (true) {
            delay(1_000)
            value = clock()
        }
    }
    return now
}

/** Live reading, target and forecast for one probe, ready for [TempChart]. */
fun chartLine(probe: ProbeState, log: LogView, nowMs: Long, color: Color): ChartLine {
    val live = probe.celsius
    val target = probe.target?.celsius
    val rate = Trend.ratePerMinute(log, probe.number, nowMs)
    val eta = if (live != null && target != null) Trend.minutesTo(target, live, rate) else null
    return ChartLine(probe.number, color, live, target, eta, rate)
}

@Composable
fun etaText(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.eta_min, minutes)
    else stringResource(R.string.eta_hours, minutes / 60, minutes % 60)

@Composable
fun durationText(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    return if (minutes < 60) stringResource(R.string.duration_min, minutes)
    else stringResource(R.string.duration_hm, minutes / 60, minutes % 60)
}

/** "+0.8°/мин" in the display unit. */
@Composable
fun rateText(ratePerMinC: Double, unit: TempUnit): String {
    val perMin = if (unit == TempUnit.F) ratePerMinC * 9.0 / 5.0 else ratePerMinC
    return stringResource(R.string.rate_fmt, String.format(Locale.getDefault(), "%+.1f°", perMin))
}

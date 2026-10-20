package com.thaimer.bfour.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaimer.bfour.R
import com.thaimer.bfour.alarm.AlarmRules
import com.thaimer.bfour.core.ChartRange
import com.thaimer.bfour.core.ChartWindow
import com.thaimer.bfour.core.Link
import com.thaimer.bfour.core.LogView
import com.thaimer.bfour.core.ProbeState
import com.thaimer.bfour.core.ThermoState
import com.thaimer.bfour.core.probeName

private enum class Phase { IDLE, COOKING, ALMOST, DONE }

/**
 * One probe: big current temperature, its target and a live chart of the cook.
 * The card itself is not a button; only the target row (or "set target") and
 * the chart react to taps, so a stray touch can't change anything.
 */
@Composable
fun ProbeCard(
    probe: ProbeState,
    state: ThermoState,
    log: LogView,
    nowMs: Long,
    onEditTarget: () -> Unit,
    onOpenChart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cook = LocalCookColors.current
    val colors = MaterialTheme.colorScheme
    val target = probe.target
    val temp = probe.celsius
    val phase = when {
        target == null || temp == null -> Phase.IDLE
        temp >= target.celsius -> Phase.DONE
        temp >= target.celsius - AlarmRules.ALMOST_MARGIN_C -> Phase.ALMOST
        else -> Phase.COOKING
    }
    val container by animateColorAsState(
        when (phase) {
            Phase.DONE -> cook.done
            Phase.ALMOST -> cook.almost
            else -> colors.surfaceContainerHigh
        },
        label = "container",
    )
    val content = when (phase) {
        Phase.DONE -> cook.onDone
        Phase.ALMOST -> cook.onAlmost
        else -> colors.onSurface
    }
    val unit = state.unit
    val line = chartLine(probe, log, nowMs, if (phase == Phase.DONE) content else cook.line(probe.number))
    // Yesterday's cook belongs on the chart screen, not on today's card.
    val hasChart = ChartWindow.cook(log, nowMs)?.ongoing == true || temp != null

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProbeDot(probe.number, state.probes.size)
                Spacer(Modifier.width(10.dp))
                Text(
                    probeName(LocalContext.current, probe.number, state.probes.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                when (phase) {
                    Phase.DONE -> Badge(stringResource(R.string.state_done))
                    Phase.ALMOST -> Badge(stringResource(R.string.state_almost))
                    else -> Unit
                }
            }

            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                if (temp != null) {
                    // Shrinks the number to fit narrow phones and short cards.
                    BasicText(
                        unit.format(temp),
                        style = TextStyle(color = content, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 96.sp),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    Text(
                        stringResource(R.string.no_value),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = content.copy(alpha = 0.35f),
                    )
                    if (state.link == Link.CONNECTED) {
                        Text(
                            stringResource(R.string.probe_absent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            if (target != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = stringResource(R.string.edit_target), onClick = onEditTarget)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = stringResource(R.string.target_fmt, unit.formatWhole(target.celsius)) +
                        (target.label?.let { " · $it" } ?: "")
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val eta = line.etaMinutes
                    if ((phase == Phase.COOKING || phase == Phase.ALMOST) && eta != null && eta > 0) {
                        Text(etaText(eta), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit_target),
                        modifier = Modifier.size(20.dp),
                        tint = content.copy(alpha = 0.7f),
                    )
                }
                LinearProgressIndicator(
                    progress = { ((temp ?: 0.0) / target.celsius).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = line.color,
                    trackColor = content.copy(alpha = 0.15f),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                FilledTonalButton(onClick = onEditTarget) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.set_target))
                }
            }

            if (hasChart) {
                Spacer(Modifier.height(10.dp))
                val (from, to) = ChartWindow.of(ChartRange.ALL, log, nowMs)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = stringResource(R.string.open_chart), onClick = onOpenChart),
                ) {
                    TempChart(
                        log = log,
                        lines = listOf(line),
                        fromMs = from,
                        toMs = to,
                        nowMs = nowMs,
                        unit = unit,
                        modifier = Modifier.fillMaxSize(),
                        detailed = false,
                    )
                    Icon(
                        Icons.Rounded.OpenInFull,
                        contentDescription = stringResource(R.string.open_chart),
                        modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                        tint = content.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/** Inverted pill so it stands out on any card colour. */
@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(LocalContentColor.current, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
    )
}

@Composable
fun ProbeDot(number: Int, count: Int) {
    val (fill, edge) = when {
        count == 2 && number == 1 -> Color(0xFF111111) to Color(0xFF9E9E9E)
        count == 2 && number == 2 -> Color(0xFFF5F5F5) to Color(0xFF9E9E9E)
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primary
    }
    Box(
        Modifier
            .size(18.dp)
            .background(fill, CircleShape)
            .border(1.5.dp, edge, CircleShape)
    )
}

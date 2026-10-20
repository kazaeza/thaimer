package com.thaimer.bfour.ui

import androidx.activity.compose.BackHandler
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thaimer.bfour.R
import com.thaimer.bfour.core.ChartRange
import com.thaimer.bfour.core.ChartWindow
import com.thaimer.bfour.core.LogView
import com.thaimer.bfour.core.ProbeState
import com.thaimer.bfour.core.ThermoState
import com.thaimer.bfour.core.probeName

/** Full-screen history of every probe: how it heated up and when it should be done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    state: ThermoState,
    log: LogView,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cook = LocalCookColors.current
    var range by rememberSaveable { mutableStateOf(ChartRange.ALL) }
    var menuOpen by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    val now = rememberNow()
    // Menus and dialogs are separate windows and would sit on top of the alarm screen.
    val ringing = state.ringingProbes.isNotEmpty()
    LaunchedEffect(ringing) {
        if (ringing) {
            menuOpen = false
            askClear = false
        }
    }
    val lines = state.probes.map { chartLine(it, log, now, cook.line(it.number)) }
    val (from, to) = ChartWindow.of(range, log, now)
    val cookSpan = ChartWindow.cook(log, now)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.chart_title), style = MaterialTheme.typography.titleLarge)
                        if (cookSpan != null) {
                            Text(
                                stringResource(
                                    if (cookSpan.ongoing) R.string.cooking_for else R.string.cooked_for,
                                    durationText(cookSpan.durationMs),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menuOpen && !ringing, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chart_clear)) },
                                onClick = {
                                    menuOpen = false
                                    askClear = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxSize(),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ChartRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = range == r,
                        onClick = { range = r },
                        shape = SegmentedButtonDefaults.itemShape(i, ChartRange.entries.size),
                        icon = {},
                    ) { Text(stringResource(r.label)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            val chart = @Composable { modifier: Modifier ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = modifier,
                ) {
                    Box {
                        TempChart(
                            log = log,
                            lines = lines,
                            fromMs = from,
                            toMs = to,
                            nowMs = now,
                            unit = state.unit,
                            modifier = Modifier.fillMaxSize().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                        )
                        if (cookSpan == null && lines.all { it.liveC == null }) {
                            Text(
                                stringResource(R.string.chart_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            )
                        }
                    }
                }
            }
            val legend = @Composable { modifier: Modifier ->
                // Scrolls, so six probes still fit on a short screen.
                Column(
                    modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.probes.forEachIndexed { i, p -> ProbeStats(p, lines[i], state, now) }
                    Text(
                        stringResource(R.string.chart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Sideways the chart gets the full height and the legend moves to the right.
                Row(Modifier.weight(1f)) {
                    chart(Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(16.dp))
                    legend(Modifier.width(320.dp).fillMaxHeight())
                }
            } else {
                chart(Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                legend(Modifier.heightIn(max = 220.dp))
            }
        }
    }

    if (askClear && !ringing) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.chart_clear_title)) },
            text = { Text(stringResource(R.string.chart_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askClear = false
                    onClear()
                }) { Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private val ChartRange.label: Int
    get() = when (this) {
        ChartRange.MIN_15 -> R.string.range_15m
        ChartRange.HOUR_1 -> R.string.range_1h
        ChartRange.HOUR_3 -> R.string.range_3h
        ChartRange.ALL -> R.string.range_all
    }

/** Legend row: who is who on the chart, how fast it heats and when it will be done. */
@Composable
private fun ProbeStats(probe: ProbeState, line: ChartLine, state: ThermoState, nowMs: Long) {
    val context = LocalContext.current
    val clock = remember(context) { clockFormatter(context) }
    val unit = state.unit
    val rate = line.ratePerMin
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProbeDot(probe.number, state.probes.size)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(width = 16.dp, height = 4.dp).background(line.color, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Text(
                probeName(context, probe.number, state.probes.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                probe.celsius?.let { unit.format(it) } ?: stringResource(R.string.no_value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        // Lines up under the name: dot 18 + gap 8 + swatch 16 + gap 8.
        Row(Modifier.padding(start = 50.dp)) {
            Text(
                rate?.let { rateText(it, unit) } ?: stringResource(R.string.no_value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Spacer(Modifier.weight(1f))
            val target = probe.target
            val eta = line.etaMinutes
            val text = when {
                target == null -> stringResource(R.string.no_target)
                eta != null && eta > 0 -> stringResource(
                    R.string.eta_at,
                    stringResource(R.string.target_fmt, unit.formatWhole(target.celsius)) + " · " + etaText(eta),
                    clock.clock(nowMs + eta * 60_000L),
                )
                else -> stringResource(R.string.target_fmt, unit.formatWhole(target.celsius))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

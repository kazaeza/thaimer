package com.kazaeza.thaimer.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazaeza.thaimer.data.Stats
import com.kazaeza.thaimer.model.Kind
import com.kazaeza.thaimer.model.Workout
import com.kazaeza.thaimer.ui.components.AppIcons
import com.kazaeza.thaimer.ui.components.IntensityBars
import com.kazaeza.thaimer.ui.components.RoundIconButton
import com.kazaeza.thaimer.ui.components.SectionLabel
import com.kazaeza.thaimer.ui.components.WorkoutDonut
import com.kazaeza.thaimer.ui.components.drawRingArc
import com.kazaeza.thaimer.ui.daysAgo
import com.kazaeza.thaimer.ui.hoursMinutes
import com.kazaeza.thaimer.ui.minutes
import com.kazaeza.thaimer.ui.plural
import com.kazaeza.thaimer.ui.rounds
import com.kazaeza.thaimer.ui.theme.Oswald
import com.kazaeza.thaimer.ui.theme.Thaimer
import com.kazaeza.thaimer.ui.theme.Type
import com.kazaeza.thaimer.ui.theme.color

@Composable
fun HomeScreen(
    workouts: List<Workout>,
    stats: Stats,
    onOpen: (Workout) -> Unit,
    onSettings: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val p = Thaimer.palette
    val bars = WindowInsets.systemBars.asPaddingValues()
    LazyColumn(
        Modifier.fillMaxSize().background(p.background),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = bars.calculateTopPadding() + 8.dp,
            bottom = bars.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(stats, onSettings) }
        item { SectionLabel("Тренировки", Modifier.padding(start = 4.dp, top = 8.dp)) }
        items(workouts, key = { it.id }) { w ->
            WorkoutCard(w, stats.timesDone[w.id] ?: 0, stats.lastDone[w.id], now) { onOpen(w) }
        }
    }
}

@Composable
private fun Header(stats: Stats, onSettings: () -> Unit) {
    val p = Thaimer.palette
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo(Modifier.size(38.dp))
            Spacer(Modifier.width(10.dp))
            Text("thaimer", style = Type.hero.copy(fontSize = 38.sp), color = p.text, modifier = Modifier.weight(1f))
            RoundIconButton(AppIcons.Settings, "Настройки", onSettings, tint = p.muted)
        }
        Text(
            "Тайский бокс дома — когда не попал в зал",
            style = Type.body, color = p.muted, modifier = Modifier.padding(top = 2.dp, start = 2.dp),
        )
        if (stats.workouts > 0) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("🔥 " + plural(stats.workouts, "тренировка", "тренировки", "тренировок"))
                StatChip("⏱️ " + hoursMinutes(stats.seconds))
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    val p = Thaimer.palette
    Text(
        text,
        style = Type.small.copy(fontWeight = FontWeight.Medium),
        color = p.text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** The app mark: the segmented timer ring. */
@Composable
fun Logo(modifier: Modifier = Modifier) {
    val dark = Thaimer.palette.dark
    val kinds = listOf(Kind.FIGHT, Kind.BURST, Kind.CLINCH, Kind.REST, Kind.LEGS)
    val sweeps = listOf(0.25f, 0.17f, 0.19f, 0.14f, 0.11f)
    Canvas(modifier) {
        var start = 0.014f
        kinds.forEachIndexed { i, k ->
            drawRingArc(start, sweeps[i], k.color(dark), size.minDimension * 0.17f, gapDegrees = 22f, cap = StrokeCap.Round)
            start += sweeps[i] + 0.028f
        }
    }
}

@Composable
private fun WorkoutCard(workout: Workout, timesDone: Int, lastDone: Long?, now: Long, onClick: () -> Unit) {
    val p = Thaimer.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkoutDonut(workout, Modifier.size(64.dp), stroke = 6.dp, emojiSize = 24.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(workout.title, style = Type.cardTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                workout.tagline, style = Type.small, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${minutes(workout.seconds)} · ${rounds(workout.rounds)}",
                    style = Type.small.copy(fontFamily = Oswald, fontSize = 14.sp, letterSpacing = 0.3.sp),
                    color = p.text,
                )
                Spacer(Modifier.width(10.dp))
                IntensityBars(workout.intensity)
                if (workout.hasRandomBursts) {
                    Spacer(Modifier.width(8.dp))
                    Text("🎲", fontSize = 13.sp)
                }
            }
            if (lastDone != null) {
                Text(
                    "✓ ${daysAgo(lastDone, now)} · ${plural(timesDone, "раз", "раза", "раз")}",
                    style = Type.small, color = Kind.COOLDOWN.color(p.dark),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(AppIcons.Chevron, null, tint = p.faint, modifier = Modifier.size(22.dp))
        }
    }
}

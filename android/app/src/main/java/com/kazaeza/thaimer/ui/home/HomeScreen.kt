package com.kazaeza.thaimer.ui.home

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazaeza.thaimer.data.Stats
import com.kazaeza.thaimer.model.Kind
import com.kazaeza.thaimer.model.Workout
import com.kazaeza.thaimer.model.WorkoutGroup
import com.kazaeza.thaimer.ui.components.AppIcons
import com.kazaeza.thaimer.ui.components.ChampionLogo
import com.kazaeza.thaimer.ui.components.Embers
import com.kazaeza.thaimer.ui.components.Flames
import com.kazaeza.thaimer.ui.components.FocusChips
import com.kazaeza.thaimer.ui.components.IntensityBars
import com.kazaeza.thaimer.ui.components.LOGO_ASPECT
import com.kazaeza.thaimer.ui.components.LOGO_FLAME_BASE
import com.kazaeza.thaimer.ui.components.RoundIconButton
import com.kazaeza.thaimer.ui.components.SectionLabel
import com.kazaeza.thaimer.ui.components.WorkoutDonut
import com.kazaeza.thaimer.ui.components.rememberFireTime
import com.kazaeza.thaimer.ui.daysAgo
import com.kazaeza.thaimer.ui.hoursMinutes
import com.kazaeza.thaimer.ui.minutes
import com.kazaeza.thaimer.ui.plural
import com.kazaeza.thaimer.ui.rounds
import com.kazaeza.thaimer.ui.theme.Fire
import com.kazaeza.thaimer.ui.theme.Oswald
import com.kazaeza.thaimer.ui.theme.Thaimer
import com.kazaeza.thaimer.ui.theme.Type
import com.kazaeza.thaimer.ui.theme.color
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    groups: List<WorkoutGroup>,
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
        item { Hero(stats, onSettings) }
        groups.forEach { group ->
            item(key = "group:${group.title}") { GroupHeader(group.title) }
            items(group.workouts, key = { it.id }) { w ->
                WorkoutCard(w, stats.timesDone[w.id] ?: 0, stats.lastDone[w.id], now) { onOpen(w) }
            }
        }
    }
}

/** Fight-poster header: the gym's fighter on fire, the name, and the running tally. */
@Composable
private fun Hero(stats: Stats, onSettings: () -> Unit) {
    val time = rememberFireTime()
    Box(
        Modifier
            .fillMaxWidth()
            .height(236.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1A110C), Fire.night))),
    ) {
        Flames(time, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(54.dp), tongues = 14, strength = 0.8f)
        Embers(time, Modifier.fillMaxSize(), count = 14)
        // Taller than the card on purpose: the fighter stands on the bottom edge (the logo's flame
        // line), and the empty space under it, where the gym's name would go, is clipped away.
        ChampionLogo(
            time,
            Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val height = 268.dp.roundToPx()
                    val width = (height * LOGO_ASPECT).roundToInt()
                    val logo = measurable.measure(Constraints.fixed(width, height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        logo.place(
                            x = constraints.maxWidth - width + 28.dp.roundToPx(),
                            y = constraints.maxHeight - (height * LOGO_FLAME_BASE).roundToInt(),
                        )
                    }
                },
            withWord = false,
        )
        Column(Modifier.fillMaxHeight().padding(start = 20.dp, top = 18.dp, bottom = 16.dp)) {
            Text("thaimer", style = Type.hero.copy(fontSize = 44.sp, lineHeight = 48.sp), color = Color.White)
            Text("มวยไทย", style = Type.body.copy(fontSize = 17.sp, letterSpacing = 1.sp), color = Fire.gold)
            Text(
                "Тайский бокс дома —\nкогда не попал в зал",
                style = Type.small, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            if (stats.workouts > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeroChip("🔥 " + plural(stats.workouts, "тренировка", "тренировки", "тренировок"))
                    HeroChip("⏱️ " + hoursMinutes(stats.seconds))
                }
            }
        }
        RoundIconButton(
            AppIcons.Settings, "Настройки", onSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            size = 40.dp, iconSize = 22.dp,
            background = Color.Black.copy(alpha = 0.35f), tint = Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun HeroChip(text: String) {
    Text(
        text,
        style = Type.small.copy(fontWeight = FontWeight.Medium),
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** A quiet divider between groups of workouts: a label and a hairline, nothing more. */
@Composable
private fun GroupHeader(title: String) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title)
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = Thaimer.palette.track)
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
            FocusChips(workout, Modifier.padding(top = 8.dp))
            if (lastDone != null) {
                Text(
                    "✓ ${daysAgo(lastDone, now)} · ${plural(timesDone, "раз", "раза", "раз")}",
                    style = Type.small, color = Kind.COOLDOWN.color(p.dark),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Chevron, null, tint = p.faint, modifier = Modifier.size(22.dp))
        }
    }
}

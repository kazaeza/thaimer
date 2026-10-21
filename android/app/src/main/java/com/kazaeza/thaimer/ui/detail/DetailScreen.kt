package com.kazaeza.thaimer.ui.detail

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazaeza.thaimer.data.Settings
import com.kazaeza.thaimer.model.Kind
import com.kazaeza.thaimer.model.Workout
import com.kazaeza.thaimer.ui.components.AppIcons
import com.kazaeza.thaimer.ui.components.DurationPill
import com.kazaeza.thaimer.ui.components.FocusChips
import com.kazaeza.thaimer.ui.components.IntensityBars
import com.kazaeza.thaimer.ui.components.KindDot
import com.kazaeza.thaimer.ui.components.RoundIconButton
import com.kazaeza.thaimer.ui.components.SectionLabel
import com.kazaeza.thaimer.ui.components.WorkoutDonut
import com.kazaeza.thaimer.ui.mmss
import com.kazaeza.thaimer.ui.plural
import com.kazaeza.thaimer.ui.shortDuration
import com.kazaeza.thaimer.ui.theme.Oswald
import com.kazaeza.thaimer.ui.theme.Thaimer
import com.kazaeza.thaimer.ui.theme.Type
import com.kazaeza.thaimer.ui.theme.color
import com.kazaeza.thaimer.ui.theme.emoji

@Composable
fun DetailScreen(
    workout: Workout,
    settings: Settings,
    onBack: () -> Unit,
    onStart: () -> Unit,
    voiceMissing: Boolean = false,
) {
    val p = Thaimer.palette
    val rows = remember(workout) { planRows(workout) }
    val bars = WindowInsets.systemBars.asPaddingValues()
    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding() + 120.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(AppIcons.Back, "Назад", onBack, tint = p.text)
                }
            }
            item { Header(workout) }
            item { StatsRow(workout) }
            item {
                Text(
                    workout.description, style = Type.body, color = p.text,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(p.surface)
                        .padding(16.dp),
                )
            }
            item { SectionLabel("План", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp)) }
            items(rows) { row -> PlanRowView(row) }
        }
        StartBar(settings, voiceMissing, onStart, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Header(workout: Workout) {
    val p = Thaimer.palette
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WorkoutDonut(workout, Modifier.size(128.dp), stroke = 10.dp, emojiSize = 44.sp)
        Text(
            workout.title, style = Type.hero.copy(fontSize = 34.sp, lineHeight = 38.sp), color = p.text,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
        )
        Text(workout.tagline, style = Type.body, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        FocusChips(workout, Modifier.fillMaxWidth().padding(top = 12.dp), centered = true)
    }
}

@Composable
private fun StatsRow(workout: Workout) {
    val p = Thaimer.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat("Время") { Text(mmss(workout.seconds.toLong()), style = Type.stat, color = p.text) }
        Stat("Раунды") { Text("${workout.rounds}", style = Type.stat, color = p.text) }
        Stat("Нагрузка") { Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) { IntensityBars(workout.intensity) } }
    }
}

@Composable
private fun Stat(label: String, value: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel(label)
        value()
    }
}

@Composable
private fun PlanRowView(row: PlanRow) {
    val p = Thaimer.palette
    when (row) {
        is PlanRow.Header -> Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = Type.cardTitle, color = p.text)
                if (row.times > 1) {
                    Text(
                        plural(row.times, "круг", "круга", "кругов") + " подряд · отдых ${shortDuration(row.restBetween)} между ними",
                        style = Type.small, color = p.muted,
                    )
                }
            }
            Text(shortDuration(row.seconds), style = Type.cardTitle.copy(fontWeight = FontWeight.Normal), color = p.muted)
        }
        is PlanRow.Item -> StepRow(row.step.kind, row.step.name, row.step.hint, row.step.seconds)
        is PlanRow.Sparring -> StepRow(
            Kind.FIGHT, "Спарринг со взрывами",
            "Темп спарринга, а в случайные моменты — взрывы по ${row.burstMin}–${row.burstMax} с и «под давлением» после каждого",
            row.seconds, emoji = "🎲",
        )
        is PlanRow.Repeat -> Row(Modifier.fillMaxWidth().padding(start = 76.dp, top = 2.dp, bottom = 6.dp)) {
            Text(
                "Повторить ×${row.times}",
                style = Type.small.copy(fontFamily = Oswald, fontSize = 15.sp, letterSpacing = 0.5.sp),
                color = p.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(p.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun StepRow(kind: Kind, name: String, hint: String, seconds: Int, emoji: String = kind.emoji) {
    val p = Thaimer.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindDot(kind, 44.dp, emoji = emoji, emojiSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = Type.body.copy(fontWeight = FontWeight.Medium), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hint.isNotBlank()) {
                Text(hint, style = Type.small, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        DurationPill(shortDuration(seconds), kind.color(p.dark))
    }
}

@Composable
private fun StartBar(settings: Settings, voiceMissing: Boolean, onStart: () -> Unit, modifier: Modifier) {
    val p = Thaimer.palette
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(p.background.copy(alpha = 0f), p.background, p.background)))
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(12.dp, RoundedCornerShape(32.dp), ambientColor = p.accent, spotColor = p.accent)
                .clip(RoundedCornerShape(32.dp))
                .background(p.accent)
                .clickable(role = Role.Button, onClickLabel = "Начать тренировку", onClick = onStart),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Play, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Text("СТАРТ", style = Type.title.copy(letterSpacing = 3.sp), color = p.onAccent)
        }
        val voice = when {
            !settings.voice -> "без голоса"
            voiceMissing -> "нет русского голоса — см. настройки"
            else -> "голос включён"
        }
        val prep = if (settings.prepSeconds > 0) "подготовка ${settings.prepSeconds} с" else "без подготовки"
        Text("$prep · $voice", style = Type.small, color = p.muted, modifier = Modifier.padding(top = 8.dp))
    }
}

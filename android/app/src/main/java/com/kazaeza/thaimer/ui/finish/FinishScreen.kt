package com.kazaeza.thaimer.ui.finish

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazaeza.thaimer.engine.Session
import com.kazaeza.thaimer.model.Kind
import com.kazaeza.thaimer.ui.components.StatColumn
import com.kazaeza.thaimer.ui.elapsedClock
import com.kazaeza.thaimer.ui.theme.Thaimer
import com.kazaeza.thaimer.ui.theme.Type

private val lines = listOf(
    "Устал — значит, вырос. Завтра будет легче.",
    "Дыхание сбито, а техника держится — это и есть класс.",
    "Каждый раунд дома — минус одна одышка в спарринге.",
    "Соперник сегодня отдыхал. Ты — нет.",
    "Лучший раунд — следующий. Восстанавливайся.",
)

@Composable
fun FinishScreen(session: Session, onDone: () -> Unit) {
    val p = Thaimer.palette
    val timeline = session.timeline
    val bursts = timeline.segments.count { it.step.kind == Kind.BURST }
    val line = remember(session) { lines.random() }
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    BackHandler(onBack = onDone)

    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .background(Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.28f), p.background, p.background))),
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🏆", fontSize = 96.sp, modifier = Modifier.scale(pop.value))
            Text("Красавчик!", style = Type.hero.copy(fontSize = 48.sp, lineHeight = 52.sp), color = p.text, modifier = Modifier.padding(top = 8.dp))
            Text("${timeline.workout.title} — готово", style = Type.body, color = p.muted, textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("Время", elapsedClock(session.workedMs), Modifier.weight(1f))
                StatColumn("Раунды", "${timeline.totalRounds}", Modifier.weight(1f), mono = false)
                StatColumn(if (bursts > 0) "Взрывы" else "Отрезки", "${if (bursts > 0) bursts else timeline.segments.size}", Modifier.weight(1f), mono = false)
            }
            Text(
                line, style = Type.body, color = p.text, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp, start = 12.dp, end = 12.dp),
            )
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(p.accent)
                    .clickable(role = Role.Button, onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text("ГОТОВО", style = Type.title.copy(letterSpacing = 3.sp, fontSize = 24.sp), color = p.onAccent)
            }
        }
    }
}

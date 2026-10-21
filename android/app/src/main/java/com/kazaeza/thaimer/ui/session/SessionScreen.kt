package com.kazaeza.thaimer.ui.session

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.kazaeza.thaimer.engine.Session
import com.kazaeza.thaimer.model.BlockType
import com.kazaeza.thaimer.model.Kind
import com.kazaeza.thaimer.model.Segment
import com.kazaeza.thaimer.model.Timeline
import com.kazaeza.thaimer.ui.components.AppIcons
import com.kazaeza.thaimer.ui.components.MonoText
import com.kazaeza.thaimer.ui.components.RoundIconButton
import com.kazaeza.thaimer.ui.components.SectionLabel
import com.kazaeza.thaimer.ui.components.StatColumn
import com.kazaeza.thaimer.ui.components.drawRingArc
import com.kazaeza.thaimer.ui.elapsedClock
import com.kazaeza.thaimer.ui.remainingClock
import com.kazaeza.thaimer.ui.shortDuration
import com.kazaeza.thaimer.ui.theme.Oswald
import com.kazaeza.thaimer.ui.theme.Thaimer
import com.kazaeza.thaimer.ui.theme.Type
import com.kazaeza.thaimer.ui.theme.color
import com.kazaeza.thaimer.ui.theme.emoji
import com.kazaeza.thaimer.ui.theme.kindColor
import kotlin.math.ceil

data class SessionActions(
    val onToggle: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onJump: (Int) -> Unit,
    val onPause: () -> Unit,
    val onStop: () -> Unit,
    val onToggleSound: () -> Unit,
)

@Composable
fun SessionScreen(session: Session, soundOn: Boolean, actions: SessionActions) {
    val now = rememberFrameClock(session)
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var showPlan by rememberSaveable { mutableStateOf(false) }
    KeepScreenOn()
    BackHandler { confirmExit = true }

    val elapsed = session.elapsedAt(now)
    SessionContent(
        timeline = session.timeline,
        elapsedMs = elapsed,
        running = session.running,
        soundOn = soundOn,
        onClose = { confirmExit = true },
        onPlan = { showPlan = true },
        actions = actions,
        startsInMs = session.startsInMs(now),
    )

    if (showPlan) {
        PlanSheet(
            timeline = session.timeline,
            currentIndex = session.timeline.indexAt(elapsed),
            onJump = { actions.onJump(it); showPlan = false },
            onDismiss = { showPlan = false },
        )
    }
    if (confirmExit) {
        LaunchedEffect(Unit) { actions.onPause() }
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Закончить тренировку?") },
            text = { Text("Таймер на паузе. Если закончить сейчас, тренировка не засчитается.") },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; actions.onStop() }) { Text("Закончить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false; actions.onToggle() }) { Text("Продолжить") }
            },
        )
    }
}

/**
 * Ticks every frame while the clock runs, so the rings sweep smoothly. Declared as an infinite
 * animation, so UI tests can still settle; it re-syncs whenever the engine publishes a change.
 */
@Composable
private fun rememberFrameClock(session: Session): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session) {
        now = SystemClock.elapsedRealtime()
        if (session.running) while (true) withInfiniteAnimationFrameMillis { now = SystemClock.elapsedRealtime() }
    }
    return now
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun SessionContent(
    timeline: Timeline,
    elapsedMs: Long,
    running: Boolean,
    soundOn: Boolean,
    onClose: () -> Unit,
    onPlan: () -> Unit,
    actions: SessionActions,
    startsInMs: Long = 0,
) {
    val p = Thaimer.palette
    val index = timeline.indexAt(elapsedMs)
    val segment = timeline.segments[index]
    val step = segment.step
    val stepColor = kindColor(step.kind)
    val round = timeline.blocks.lastOrNull { it.type == BlockType.ROUND && it.startMs <= elapsedMs }?.round ?: 0

    Box(Modifier.fillMaxSize().background(p.background)) {
        if (step.kind == Kind.BURST && running && startsInMs == 0L) BurstGlow(stepColor)
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(AppIcons.Close, "Закончить тренировку", onClose, tint = p.muted)
                Text(
                    timeline.workout.title, style = Type.small.copy(fontWeight = FontWeight.Medium), color = p.muted,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(48.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("Прошло", elapsedClock(elapsedMs), Modifier.weight(1f))
                StatColumn("Раунд", "${if (round == 0) "–" else round}/${timeline.totalRounds}", Modifier.weight(1f), mono = false)
                StatColumn("Осталось", remainingClock(timeline.totalMs - elapsedMs), Modifier.weight(1f))
            }
            // Next up, the rings and the coaching tip read as one group, centred in the free space.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val nextUpHeight = 80.dp
                val hintHeight = 46.dp
                val side = min(maxWidth, maxHeight - nextUpHeight - hintHeight - 24.dp).coerceIn(160.dp, 400.dp)
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NextUp(timeline, index, Modifier.height(nextUpHeight))
                    Spacer(Modifier.height(8.dp))
                    // The whole dial is a pause / play button: easy to hit from a step away.
                    Box(
                        Modifier
                            .size(side)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClickLabel = if (running) "Пауза" else "Старт",
                                onClick = actions.onToggle,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        SessionRings(timeline, elapsedMs, segment, Modifier.fillMaxSize().alpha(if (running) 1f else 0.55f))
                        RingCenter(timeline, segment, elapsedMs, running, startsInMs, Modifier.width(side * 0.6f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        step.hint,
                        style = Type.body, color = p.muted, textAlign = TextAlign.Center, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().height(hintHeight).padding(horizontal = 8.dp),
                    )
                }
            }
            Controls(running, soundOn, actions, onPlan, Modifier.padding(top = 4.dp, bottom = 10.dp))
        }
    }
}

@Composable
private fun BurstGlow(color: Color) {
    val pulse by rememberInfiniteTransition(label = "burst").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "pulse",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.38f * pulse), color.copy(alpha = 0.10f * pulse), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.52f),
                radius = size.width * 0.95f,
            ),
        )
    }
}

private data class Upcoming(val title: String, val kind: Kind?)

/**
 * Two lines of what's ahead: a new round or block announces itself first, then its opening
 * interval. Never looks past an ambush, and never shows the length of the stretch before one.
 */
private fun upcoming(timeline: Timeline, index: Int): List<Upcoming> {
    val out = mutableListOf<Upcoming>()
    var i = index + 1
    while (out.size < 2 && i < timeline.segments.size) {
        val s = timeline.segments[i]
        if (s.step.surprise) {
            out += Upcoming("???", null)
            break
        }
        val block = timeline.blockOf(s)
        if (s.index == block.segments.first && block.type != BlockType.REST) {
            val header = when (block.type) {
                BlockType.ROUND -> "Раунд ${block.round}" + if (block.title.isNotBlank()) " · ${block.title}" else ""
                else -> block.title
            }
            if (header.isNotBlank() && header != s.step.name) out += Upcoming(header, block.kind)
        }
        if (out.size < 2) {
            val length = if (timeline.isMasked(s)) "" else " · " + shortDuration(s.step.seconds)
            out += Upcoming(s.step.name + length, s.step.kind)
        }
        i++
    }
    return out
}

@Composable
private fun NextUp(timeline: Timeline, index: Int, modifier: Modifier = Modifier) {
    val p = Thaimer.palette
    val items = upcoming(timeline, index)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("Далее")
        if (items.isEmpty()) {
            Text("Финиш 🏁", style = Type.cardTitle, color = p.text)
        }
        items.forEachIndexed { i, item ->
            val c = item.kind?.let { kindColor(it) } ?: p.muted
            Text(
                item.title,
                style = if (i == 0) Type.cardTitle else Type.body.copy(fontFamily = Oswald, fontSize = 16.sp),
                color = if (i == 0) c else c.copy(alpha = 0.8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Two rings: the outer one is the whole workout block by block (done blocks fade, the dot is
 * "you are here"), the thick inner one is what is left of the current countdown.
 */
@Composable
private fun SessionRings(timeline: Timeline, elapsedMs: Long, segment: Segment, modifier: Modifier) {
    val p = Thaimer.palette
    val dark = p.dark
    Canvas(modifier) {
        val outerW = 10.dp.toPx()
        val innerW = 18.dp.toPx()
        val gap = 12.dp.toPx()

        val total = timeline.totalMs.toFloat()
        val progress = elapsedMs / total
        timeline.blocks.forEach { b ->
            drawSplitArc(b.startMs / total, b.durationMs / total, progress, b.kind.color(dark), outerW, 0f, 1.4f)
        }
        val r = size.minDimension / 2 - outerW / 2
        val angle = Math.toRadians((progress * 360f - 90f).toDouble())
        val marker = Offset(center.x + r * kotlin.math.cos(angle).toFloat(), center.y + r * kotlin.math.sin(angle).toFloat())
        drawCircle(p.background, radius = outerW * 0.9f, center = marker)
        drawCircle(p.text, radius = outerW * 0.55f, center = marker)

        // Counts down to the bell instead of the interval's end when the next interval is an ambush.
        val innerInset = outerW + gap
        drawRingArc(0f, 1f, p.track, innerW, inset = innerInset)
        val target = timeline.countdownTarget(segment)
        val span = (target - segment.startMs).toFloat()
        val left = ((target - elapsedMs) / span).coerceIn(0f, 1f)
        if (left > 0f) {
            drawRingArc(1f - left, left, segment.step.kind.color(dark), innerW, inset = innerInset, cap = StrokeCap.Round)
        }
    }
}

/** A ring slice dimmed up to [progress] and full colour after it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSplitArc(
    start: Float,
    sweep: Float,
    progress: Float,
    color: Color,
    width: Float,
    inset: Float,
    gapDegrees: Float,
) {
    val gap = gapDegrees / 360f
    val a = start + gap / 2
    val b = start + sweep - gap / 2
    if (b <= a) return
    val split = progress.coerceIn(a, b)
    if (split > a) drawRingArc(a, split - a, color.copy(alpha = 0.22f), width, inset = inset)
    if (b > split) drawRingArc(split, b - split, color, width, inset = inset)
}

@Composable
private fun RingCenter(timeline: Timeline, segment: Segment, elapsedMs: Long, running: Boolean, startsInMs: Long, modifier: Modifier) {
    val p = Thaimer.palette
    val block = timeline.blockOf(segment)
    val step = segment.step
    val caption = when (block.type) {
        BlockType.PREP -> timeline.workout.title
        BlockType.ROUND -> "Раунд ${block.round}" + if (block.title.isNotBlank()) " · ${block.title}" else ""
        BlockType.REST -> timeline.blocks.getOrNull(block.index - 1)
            ?.takeIf { it.type == BlockType.ROUND }?.let { "Раунд ${it.round} позади" } ?: "Перерыв"
        else -> block.title
    }
    val masked = timeline.isMasked(segment)
    val target = timeline.countdownTarget(segment)
    val remaining = target - elapsedMs
    val headStart = running && startsInMs > 0
    val finalSeconds = running && !headStart && !masked && remaining in 1..3_000
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            caption.uppercase(), style = Type.label, color = p.muted, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text(step.kind.emoji, fontSize = 30.sp, modifier = Modifier.padding(top = 4.dp))
        Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            BasicText(
                step.name,
                style = Type.title.copy(color = kindColor(step.kind), textAlign = TextAlign.Center),
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 30.sp, stepSize = 1.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (headStart) {
            // Silent head start after start / resume: a big countdown, no sounds.
            MonoText(
                ceil(startsInMs / 1000.0).toInt().toString(),
                Type.hero.copy(fontSize = 62.sp, lineHeight = 66.sp),
                p.accent,
            )
        } else {
            MonoText(
                remainingClock(remaining),
                Type.hero.copy(fontSize = 62.sp, lineHeight = 66.sp),
                if (finalSeconds) kindColor(step.kind) else p.text,
            )
        }
        Text(
            when {
                !running -> "ПАУЗА · НАЖМИ"
                headStart -> "ДО СТАРТА"
                masked -> "ДО ГОНГА"
                else -> " "
            },
            style = Type.label, color = if (!running || headStart) p.accent else p.muted,
        )
    }
}

@Composable
private fun Controls(running: Boolean, soundOn: Boolean, actions: SessionActions, onPlan: () -> Unit, modifier: Modifier) {
    val p = Thaimer.palette
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            if (soundOn) AppIcons.VolumeOn else AppIcons.VolumeOff,
            if (soundOn) "Выключить звук" else "Включить звук",
            actions.onToggleSound, tint = p.muted,
        )
        RoundIconButton(AppIcons.Previous, "Предыдущий отрезок", actions.onPrevious, size = 56.dp, iconSize = 34.dp)
        Box(
            Modifier
                .size(84.dp)
                .shadow(14.dp, CircleShape, ambientColor = p.accent, spotColor = p.accent)
                .clip(CircleShape)
                .background(p.accent)
                .clickable(role = Role.Button, onClickLabel = if (running) "Пауза" else "Старт", onClick = actions.onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (running) AppIcons.Pause else AppIcons.Play,
                contentDescription = if (running) "Пауза" else "Продолжить",
                tint = p.onAccent, modifier = Modifier.size(40.dp),
            )
        }
        RoundIconButton(AppIcons.Next, "Следующий отрезок", actions.onNext, size = 56.dp, iconSize = 34.dp)
        RoundIconButton(AppIcons.List, "План тренировки", onPlan, tint = p.muted)
    }
}

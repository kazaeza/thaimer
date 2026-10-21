package com.kazaeza.thaimer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazaeza.thaimer.model.Kind
import com.kazaeza.thaimer.model.TimelineBuilder
import com.kazaeza.thaimer.model.Workout
import com.kazaeza.thaimer.ui.theme.Oswald
import com.kazaeza.thaimer.ui.theme.Thaimer
import com.kazaeza.thaimer.ui.theme.Type
import com.kazaeza.thaimer.ui.theme.color
import com.kazaeza.thaimer.ui.theme.emoji
import com.kazaeza.thaimer.ui.theme.label
import kotlin.random.Random

/**
 * Oswald has proportional digits; give every digit the same slot so a ticking clock
 * doesn't wobble.
 */
@Composable
fun MonoText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val digit = with(density) { (style.fontSize * 0.56f).toDp() }
    val colon = with(density) { (style.fontSize * 0.27f).toDp() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEach { ch ->
            Box(Modifier.width(if (ch.isDigit()) digit else colon), contentAlignment = Alignment.Center) {
                Text(
                    ch.toString(), style = style, color = color, maxLines = 1, softWrap = false,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

@Composable
fun StatColumn(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = true) {
    val p = Thaimer.palette
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = Type.label, color = p.muted, maxLines = 1)
        if (mono) MonoText(value, Type.stat, p.text) else Text(value, style = Type.stat, color = p.text, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Type.label, color = Thaimer.palette.muted, modifier = modifier)
}

/** Coloured duration chip, like the reference's per-exercise pills. */
@Composable
fun DurationPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (Thaimer.palette.dark) Color(0xFF111114) else Color.White
        Text(text, style = TextStyle(fontFamily = Oswald, fontSize = 15.sp, letterSpacing = 0.5.sp), color = ink, maxLines = 1)
    }
}

/** What the workout builds, plus any kit it needs. */
@Composable
fun FocusChips(workout: Workout, modifier: Modifier = Modifier, centered: Boolean = false) {
    FlowRow(
        modifier,
        horizontalArrangement = if (centered) Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        workout.focus.forEach { Chip("${it.emoji} ${it.label}") }
        if (workout.equipment.isNotBlank()) Chip("🏋️ нужен ${workout.equipment}")
    }
}

@Composable
private fun Chip(text: String) {
    val p = Thaimer.palette
    Text(
        text,
        style = Type.small.copy(fontSize = 12.sp),
        color = p.text,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(p.surfaceAlt)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Five rising bars, filled up to the workout's intensity. */
@Composable
fun IntensityBars(level: Int, modifier: Modifier = Modifier, color: Color = Thaimer.palette.accent) {
    val track = Thaimer.palette.track
    Row(modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..5) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((5 + i * 2).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i <= level) color else track),
            )
        }
    }
}

@Composable
fun KindDot(kind: Kind, size: Dp, modifier: Modifier = Modifier, emoji: String? = null, emojiSize: TextUnit = 18.sp) {
    val c = kind.color(Thaimer.palette.dark)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) Text(emoji, fontSize = emojiSize, textAlign = TextAlign.Center)
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 26.dp,
    background: Color = Color.Transparent,
    tint: Color = Thaimer.palette.text,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A workout's whole structure as a ring of coloured blocks. */
@Composable
fun WorkoutDonut(
    workout: Workout,
    modifier: Modifier = Modifier,
    stroke: Dp = 6.dp,
    emojiSize: TextUnit = 24.sp,
) {
    val dark = Thaimer.palette.dark
    val arcs = remember(workout) {
        val t = TimelineBuilder.build(workout, prepSeconds = 0, random = Random(0))
        t.blocks.map { Triple(it.startMs.toFloat() / t.totalMs, it.durationMs.toFloat() / t.totalMs, it.kind) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            arcs.forEach { (start, sweep, kind) ->
                drawRingArc(start, sweep, kind.color(dark), w, gapDegrees = 3.5f)
            }
        }
        Text(workout.emoji, fontSize = emojiSize)
    }
}

/** Draws a slice of a ring; fractions are of a full turn starting at 12 o'clock. */
fun DrawScope.drawRingArc(
    startFraction: Float,
    sweepFraction: Float,
    color: Color,
    strokeWidth: Float,
    gapDegrees: Float = 0f,
    inset: Float = 0f,
    cap: StrokeCap = StrokeCap.Butt,
) {
    val sweep = sweepFraction * 360f - gapDegrees
    if (sweep <= 0.2f) return
    val half = strokeWidth / 2 + inset
    drawArc(
        color = color,
        startAngle = -90f + startFraction * 360f + gapDegrees / 2,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(half, half),
        size = Size(size.width - 2 * half, size.height - 2 * half),
        style = Stroke(strokeWidth, cap = cap),
    )
}

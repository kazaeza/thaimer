package com.kazaeza.thaimer.ui.components

import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kazaeza.thaimer.R
import com.kazaeza.thaimer.ui.theme.Fire
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Seconds since first composition, updated about 30 times a second: plenty for fire, half the
 * redraws of a 60 Hz screen. An infinite animation, so UI tests freeze it.
 */
@Composable
fun rememberFireTime(): State<Float> = produceState(0f) {
    val start = withInfiniteAnimationFrameMillis { it }
    var shown = start
    while (true) {
        withInfiniteAnimationFrameMillis { now ->
            if (now - shown >= FRAME_MS) {
                shown = now
                value = (now - start) / 1000f
            }
        }
    }
}

private const val FRAME_MS = 33L

private fun fract(x: Float) = x - floor(x)

/**
 * A row of flickering flame tongues, three passes from a red edge to a golden core.
 * Reads [time] only while drawing, so the animation redraws without recomposing.
 */
@Composable
fun Flames(time: State<Float>, modifier: Modifier, tongues: Int = 9, strength: Float = 1f, mound: Float = 0.25f) {
    val path = remember { Path() }
    Canvas(modifier) { drawFlames(time.value, tongues, strength, mound, path) }
}

/** Sparks drifting up through the whole area. */
@Composable
fun Embers(time: State<Float>, modifier: Modifier, count: Int = 18) {
    Canvas(modifier) { drawEmbers(time.value, count) }
}

private val passes = listOf(
    Triple(1.00f, Fire.red, 0.85f),
    Triple(0.70f, Fire.orange, 0.90f),
    Triple(0.40f, Fire.gold, 0.95f),
)

/** [mound] 0 = an even row of tongues, 1 = a heap that dies down to nothing at the edges. */
private fun DrawScope.drawFlames(t: Float, n: Int, strength: Float, mound: Float, path: Path) {
    val slot = size.width / n
    val base = size.height
    for ((scale, color, alpha) in passes) {
        for (i in 0 until n) {
            val seed = i * 1.618f
            val centre = 1f - abs(i - (n - 1) / 2f) / (n / 2f) // taller in the middle
            val flicker = 0.62f + 0.22f * sin(t * 3.1f + seed * 1.7f) +
                0.12f * sin(t * 5.3f + seed * 2.9f) + 0.06f * sin(t * 9.7f + seed)
            val height = size.height * flicker * scale * strength * (1f - mound + mound * centre)
            val half = slot * 0.9f * scale
            val x = slot * (i + 0.5f) + sin(t * 1.3f + seed * 2.1f) * slot * 0.12f
            val sway = sin(t * 2.3f + seed * 3.3f) * half * 0.6f
            val tip = base - height
            path.apply {
                reset()
                moveTo(x - half, base)
                cubicTo(x - half, base - height * 0.35f, x - half * 0.35f + sway * 0.5f, base - height * 0.7f, x + sway, tip)
                cubicTo(x + half * 0.35f + sway * 0.5f, base - height * 0.7f, x + half, base - height * 0.35f, x + half, base)
                close()
            }
            drawPath(
                path,
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0f), 0.45f to color.copy(alpha = alpha * 0.8f), 1f to color.copy(alpha = alpha),
                    startY = tip, endY = base,
                ),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

private fun DrawScope.drawEmbers(t: Float, n: Int) {
    for (k in 0 until n) {
        val seed = k * 0.618034f
        val speed = 0.10f + 0.10f * fract(seed * 3.1f)
        val rise = fract(t * speed + fract(seed * 7.3f))
        val x = size.width * fract(seed * 13.7f) + sin(t * 1.7f + k) * 10.dp.toPx()
        val y = size.height * (1f - rise)
        val r = (1.2f + 1.6f * fract(seed * 5.1f)).dp.toPx()
        val color = lerp(Fire.gold, Fire.orange, fract(seed * 2f))
        drawCircle(color.copy(alpha = (1f - rise) * 0.85f), r, Offset(x, y))
    }
}

/** Where the flames of the traced logo stand, as a share of its height (the word sits below). */
const val LOGO_FLAME_BASE = 0.785f
private const val FLAME_BASE = LOGO_FLAME_BASE
private const val FLAME_TOP = 0.50f
const val LOGO_ASPECT = 1060f / 1290f

/**
 * The "Чемпион" gym logo, traced into vectors and brought to life: the fighter and the bag as a
 * silhouette backlit by a glow, the painted flames recoloured as fire and flickering, live flames
 * behind them. [withWord] adds the gym's name underneath.
 */
@Composable
fun ChampionLogo(
    time: State<Float>,
    modifier: Modifier = Modifier,
    withWord: Boolean = true,
    figure: Color = Fire.night,
    word: Color = Color.White,
) {
    BoxWithConstraints(modifier.aspectRatio(LOGO_ASPECT)) {
        val h = maxHeight
        // Backlight.
        Canvas(Modifier.matchParentSize()) {
            val pulse = 0.85f + 0.15f * sin(time.value * 2.2f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Fire.orange.copy(alpha = 0.55f * pulse), Fire.red.copy(alpha = 0.25f * pulse), Color.Transparent),
                    center = Offset(size.width * 0.48f, size.height * 0.52f),
                    radius = size.width * 0.62f,
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width * 0.48f, size.height * 0.52f),
            )
        }
        // Live flames behind the painted ones.
        Flames(
            time,
            Modifier
                .fillMaxWidth()
                .height(h * (FLAME_BASE - FLAME_TOP + 0.08f))
                .offset(y = h * (FLAME_TOP - 0.08f)),
            tongues = 11,
            mound = 0.75f,
        )
        // The painted flames, filled with a fire gradient and breathing upwards.
        Image(
            painterResource(R.drawable.logo_flames), null,
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    val t = time.value
                    scaleY = 1f + 0.035f * sin(t * 4.1f) + 0.015f * sin(t * 9.3f)
                    scaleX = 1f + 0.01f * sin(t * 3.3f)
                    transformOrigin = TransformOrigin(0.5f, FLAME_BASE)
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Fire.core, Fire.gold, Fire.orange, Fire.red),
                            startY = size.height * FLAME_TOP, endY = size.height * FLAME_BASE,
                        ),
                        blendMode = BlendMode.SrcIn,
                    )
                },
        )
        // Rim light around the fighter (RenderEffect blur needs Android 12+; older phones skip it).
        if (Build.VERSION.SDK_INT >= 31) {
            Image(
                painterResource(R.drawable.logo_figure), null,
                Modifier
                    .matchParentSize()
                    .blur(10.dp)
                    .graphicsLayer { alpha = 0.55f + 0.2f * sin(time.value * 2.2f) },
                colorFilter = ColorFilter.tint(Fire.orange),
            )
        }
        Image(painterResource(R.drawable.logo_figure), "Тайбоксер бьёт коленом по мешку", Modifier.matchParentSize(), colorFilter = ColorFilter.tint(figure))
        if (withWord) {
            Image(painterResource(R.drawable.logo_word), "Чемпион", Modifier.matchParentSize(), colorFilter = ColorFilter.tint(word))
        }
    }
}

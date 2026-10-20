package com.thaimer.bfour.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.thaimer.bfour.core.ChartTicks
import com.thaimer.bfour.core.LogView
import com.thaimer.bfour.core.TempUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** One probe on the chart. */
data class ChartLine(
    val probe: Int,
    val color: Color,
    /** Current reading, drawn as the live tip of the line. */
    val liveC: Double?,
    val targetC: Double?,
    /** Minutes to the target at the current rate; draws the forecast. */
    val etaMinutes: Int?,
    /** °C per minute over the last few minutes, null without enough data. */
    val ratePerMin: Double? = null,
)

private const val BREAK_GAP_MS = 30_000L
private const val MAX_GRID_LINES = 40

/**
 * Temperature over time from [fromMs] to [toMs]. With [detailed] it has axes,
 * labels and a finger-drag readout; without, it is a bare sparkline for cards.
 * The chart only reads data: no gesture on it can change anything.
 */
@Composable
fun TempChart(
    log: LogView,
    lines: List<ChartLine>,
    fromMs: Long,
    toMs: Long,
    nowMs: Long,
    unit: TempUnit,
    modifier: Modifier = Modifier,
    detailed: Boolean = true,
) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
    val tooltipStyle = MaterialTheme.typography.labelLarge.copy(color = scheme.inverseOnSurface)
    val grid = scheme.onSurface.copy(alpha = 0.08f)
    val tooltipBg = scheme.inverseSurface
    val cursor = scheme.onSurface.copy(alpha = 0.45f)
    val timeFormat = remember(context) { clockFormatter(context) }
    var scrubX by remember { mutableStateOf<Float?>(null) }

    val gestures = if (detailed) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                scrubX = down.position.x
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    scrubX = change.position.x
                    change.consume()
                }
                scrubX = null
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier.then(gestures)) {
        if (toMs <= fromMs || size.width <= 0f || size.height <= 0f) return@Canvas
        val plot = if (detailed) {
            Rect(40.dp.toPx(), 12.dp.toPx(), size.width - 8.dp.toPx(), size.height - 22.dp.toPx())
        } else {
            Rect(0f, 3.dp.toPx(), size.width, size.height - 3.dp.toPx())
        }
        if (plot.width <= 0f || plot.height <= 0f) return@Canvas

        val first = (log.firstRowAtOrAfter(fromMs) - 1).coerceAtLeast(0)
        val (lo, hi, step) = valueRange(log, lines, first, fromMs, unit)
        val x = { t: Long -> plot.left + (t - fromMs).toFloat() / (toMs - fromMs) * plot.width }
        val y = { c: Double -> plot.bottom - ((unit.fromCelsius(c) - lo) / (hi - lo)).toFloat() * plot.height }

        if (detailed) {
            drawGrid(plot, lo, hi, step, fromMs, toMs, x, grid, labelStyle, measurer, timeFormat)
            if (nowMs in fromMs..toMs) {
                drawLine(
                    cursor.copy(alpha = 0.25f), Offset(x(nowMs), plot.top), Offset(x(nowMs), plot.bottom),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }

        clipRect(plot.left, plot.top, plot.right, plot.bottom) {
            lines.forEach { line ->
                line.targetC?.let { target ->
                    drawLine(
                        line.color.copy(alpha = 0.55f), Offset(plot.left, y(target)), Offset(plot.right, y(target)),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    )
                }
                drawSeries(log, line, first, plot, nowMs, x, y)
            }
        }

        if (detailed) {
            // Labels are nudged down until they stop overlapping, so close targets stay readable.
            val placed = mutableListOf<Rect>()
            fun place(layout: TextLayoutResult, at: Offset) {
                var box = Rect(at, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
                repeat(4) {
                    val hit = placed.firstOrNull { it.overlaps(box) } ?: return@repeat
                    box = box.translate(0f, hit.bottom + 1.dp.toPx() - box.top)
                }
                if (box.bottom > size.height || placed.any { it.overlaps(box) }) return
                placed += box
                drawText(layout, topLeft = box.topLeft)
            }
            lines.filter { it.targetC != null }.sortedBy { -it.targetC!! }.forEach { line ->
                val target = line.targetC!!
                val label = measurer.measure(unit.formatWhole(target), labelStyle.copy(color = line.color))
                place(label, Offset(plot.right - label.size.width, (y(target) - label.size.height).coerceAtLeast(0f)))
            }
            lines.forEach { line ->
                // When the forecast says the target will be reached, print the clock time there.
                val target = line.targetC ?: return@forEach
                val eta = line.etaMinutes ?: return@forEach
                val live = line.liveC ?: return@forEach
                if (eta <= 0 || live >= target) return@forEach
                val endX = x(nowMs + eta * 60_000L)
                if (endX > plot.right) return@forEach
                val text = measurer.measure(timeFormat.clock(nowMs + eta * 60_000L), labelStyle.copy(color = line.color))
                place(
                    text,
                    Offset(
                        (endX - text.size.width / 2f).coerceIn(plot.left, plot.right - text.size.width),
                        (y(target) + 3.dp.toPx()).coerceAtMost(plot.bottom - text.size.height),
                    ),
                )
            }
        }

        scrubX?.let { sx ->
            drawReadout(
                sx, log, lines, plot, fromMs, toMs, nowMs, x, y, unit,
                cursor, tooltipBg, tooltipStyle, measurer, timeFormat,
            )
        }
    }
}

/** Y axis bounds in display units, fitted to the visible data, live values and targets. */
private fun valueRange(
    log: LogView,
    lines: List<ChartLine>,
    first: Int,
    fromMs: Long,
    unit: TempUnit,
): Triple<Double, Double, Double> {
    var lo = Double.POSITIVE_INFINITY
    var hi = Double.NEGATIVE_INFINITY
    fun take(c: Double) {
        val v = unit.fromCelsius(c)
        if (v < lo) lo = v
        if (v > hi) hi = v
    }
    lines.forEach { line ->
        if (line.probe <= log.probeCount) {
            for (row in first until log.size) {
                if (log.time(row) < fromMs - BREAK_GAP_MS) continue
                val c = log.temp(line.probe, row)
                if (!c.isNaN()) take(c.toDouble())
            }
        }
        line.liveC?.let(::take)
        line.targetC?.let(::take)
    }
    if (lo == Double.POSITIVE_INFINITY) {
        lo = unit.fromCelsius(20.0)
        hi = unit.fromCelsius(70.0)
    }
    return ChartTicks.tempRange(lo, hi, minSpan = if (unit == TempUnit.C) 10.0 else 18.0)
}

private fun DrawScope.drawGrid(
    plot: Rect,
    lo: Double,
    hi: Double,
    step: Double,
    fromMs: Long,
    toMs: Long,
    x: (Long) -> Float,
    grid: Color,
    labelStyle: TextStyle,
    measurer: TextMeasurer,
    timeFormat: DateTimeFormatter,
) {
    val strokeWidth = 1.dp.toPx()
    var v = lo
    var guard = 0
    while (v <= hi + 1e-6 && guard++ < MAX_GRID_LINES) {
        val yy = plot.bottom - ((v - lo) / (hi - lo)).toFloat() * plot.height
        drawLine(grid, Offset(plot.left, yy), Offset(plot.right, yy), strokeWidth)
        val label = measurer.measure("${v.roundToInt()}°", labelStyle)
        drawText(
            label,
            topLeft = Offset(plot.left - label.size.width - 6.dp.toPx(), yy - label.size.height / 2f),
        )
        v += step
    }
    val zone = TimeZone.getDefault()
    val stepMs = ChartTicks.timeStep(toMs - fromMs)
    ChartTicks.timeTicks(fromMs, toMs, stepMs) { zone.getOffset(it) }.forEach { t ->
        val xx = x(t)
        drawLine(grid, Offset(xx, plot.top), Offset(xx, plot.bottom), strokeWidth)
        val label = measurer.measure(timeFormat.clock(t), labelStyle)
        val lx = (xx - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
        drawText(label, topLeft = Offset(lx, plot.bottom + 4.dp.toPx()))
    }
}

/** The probe's line, broken where readings are missing, plus its live tip and forecast. */
private fun DrawScope.drawSeries(
    log: LogView,
    line: ChartLine,
    first: Int,
    plot: Rect,
    nowMs: Long,
    x: (Long) -> Float,
    y: (Double) -> Float,
) {
    val path = Path()
    var penDown = false
    var lastT = 0L
    if (line.probe <= log.probeCount && first < log.size) {
        // About one point per pixel is plenty, even for a 12-hour cook.
        val stride = ((log.size - first) / plot.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
        val gap = BREAK_GAP_MS * stride
        var row = first
        while (row < log.size) {
            val c = log.temp(line.probe, row)
            val t = log.time(row)
            if (c.isNaN()) {
                penDown = false
            } else {
                if (penDown && t - lastT <= gap) path.lineTo(x(t), y(c.toDouble())) else path.moveTo(x(t), y(c.toDouble()))
                penDown = true
                lastT = t
            }
            row = if (row < log.size - 1) minOf(row + stride, log.size - 1) else log.size
        }
    }
    val live = line.liveC
    if (live != null && penDown && nowMs - lastT <= BREAK_GAP_MS * 2) {
        path.lineTo(x(nowMs), y(live))
    }
    drawPath(
        path, line.color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    if (live == null) return
    val target = line.targetC
    val eta = line.etaMinutes
    if (target != null && eta != null && eta > 0 && live < target) {
        val end = Offset(x(nowMs + eta * 60_000L), y(target))
        drawLine(
            line.color.copy(alpha = 0.7f), Offset(x(nowMs), y(live)), end,
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
            cap = StrokeCap.Round,
        )
        drawCircle(line.color, radius = 4.dp.toPx(), center = end, style = Stroke(2.dp.toPx()))
    }
    drawCircle(line.color, radius = 4.5.dp.toPx(), center = Offset(x(nowMs), y(live)))
}

/** Vertical cursor under the finger with the time and every probe's reading there. */
private fun DrawScope.drawReadout(
    sx: Float,
    log: LogView,
    lines: List<ChartLine>,
    plot: Rect,
    fromMs: Long,
    toMs: Long,
    nowMs: Long,
    x: (Long) -> Float,
    y: (Double) -> Float,
    unit: TempUnit,
    cursor: Color,
    background: Color,
    style: TextStyle,
    measurer: TextMeasurer,
    timeFormat: DateTimeFormatter,
) {
    val t = fromMs + ((sx - plot.left) / plot.width * (toMs - fromMs)).toLong()
    if (t < fromMs || t > nowMs) return
    val row = log.nearestRow(t)
    val hasRow = row >= 0 && abs(log.time(row) - t) <= BREAK_GAP_MS * 4
    val at = if (hasRow) log.time(row) else t
    val cx = x(at).coerceIn(plot.left, plot.right)
    drawLine(cursor, Offset(cx, plot.top), Offset(cx, plot.bottom), strokeWidth = 1.5.dp.toPx())

    val values = lines.map { line ->
        if (hasRow && line.probe <= log.probeCount) log.temp(line.probe, row) else Float.NaN
    }
    lines.forEachIndexed { i, line ->
        val c = values[i]
        if (!c.isNaN()) drawCircle(line.color, radius = 4.dp.toPx(), center = Offset(cx, y(c.toDouble())))
    }
    val text = buildAnnotatedString {
        append(timeFormat.clock(at))
        lines.forEachIndexed { i, line ->
            append("   ")
            withStyle(SpanStyle(color = line.color)) { append("●") }
            append(' ')
            append(if (values[i].isNaN()) "—" else unit.format(values[i].toDouble()))
        }
    }
    val layout = measurer.measure(text, style)
    val pad = 8.dp.toPx()
    val w = layout.size.width + pad * 2
    val h = layout.size.height + pad
    val left = (cx - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
    drawRoundRect(background, Offset(left, 0f), Size(w, h), CornerRadius(8.dp.toPx()))
    drawText(layout, topLeft = Offset(left + pad, pad / 2))
}

fun DateTimeFormatter.clock(ms: Long): String =
    format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun clockFormatter(context: Context): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")

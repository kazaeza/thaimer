package com.thaimer.bfour.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Round-number gridlines for the chart axes. */
object ChartTicks {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private val TIME_STEPS = longArrayOf(
        MIN, 2 * MIN, 5 * MIN, 10 * MIN, 15 * MIN, 30 * MIN,
        HOUR, 2 * HOUR, 3 * HOUR, 6 * HOUR, 12 * HOUR, 24 * HOUR,
    )

    /** Smallest round step (1, 2, 5, 10, 20, …) that splits [span] degrees into at most [maxTicks] parts. */
    fun tempStep(span: Double, maxTicks: Int = 5): Double {
        if (!span.isFinite() || span <= 0) return 1.0
        val raw = span / maxTicks
        val magnitude = 10.0.pow(floor(log10(raw)))
        val n = raw / magnitude
        val nice = when {
            n <= 1.0 -> 1.0
            n <= 2.0 -> 2.0
            n <= 5.0 -> 5.0
            else -> 10.0
        }
        return maxOf(1.0, nice * magnitude)
    }

    /** Axis bounds widened to whole steps, at least [minSpan] apart. */
    fun tempRange(lo: Double, hi: Double, minSpan: Double = 10.0): Triple<Double, Double, Double> {
        var a = lo
        var b = hi
        if (b - a < minSpan) {
            val mid = (a + b) / 2
            a = mid - minSpan / 2
            b = mid + minSpan / 2
        }
        val step = tempStep(b - a)
        return Triple(floor(a / step) * step, ceil(b / step) * step, step)
    }

    fun timeStep(spanMs: Long, maxTicks: Int = 5): Long =
        TIME_STEPS.firstOrNull { spanMs / it <= maxTicks } ?: TIME_STEPS.last()

    /**
     * Gridline times in [fromMs, toMs] that fall on whole steps of the local
     * clock (18:00, 18:15, …), whatever the time zone.
     */
    fun timeTicks(fromMs: Long, toMs: Long, stepMs: Long, zoneOffsetMs: (Long) -> Int): List<Long> {
        val offset = zoneOffsetMs(fromMs)
        var t = Math.floorDiv(fromMs + offset + stepMs - 1, stepMs) * stepMs - offset
        val ticks = mutableListOf<Long>()
        while (t <= toMs) {
            ticks += t
            t += stepMs
        }
        return ticks
    }
}

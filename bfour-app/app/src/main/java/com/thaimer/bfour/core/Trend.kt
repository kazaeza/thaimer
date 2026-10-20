package com.thaimer.bfour.core

import kotlin.math.roundToInt

/** How fast a probe is heating and when it should reach its target. */
object Trend {
    private const val WINDOW_MS = 5 * 60_000L
    private const val MIN_SPAN_MS = 90_000L
    private const val MIN_POINTS = 6
    private const val MIN_RATE_C_PER_MIN = 0.05
    private const val MAX_ETA_MIN = 24 * 60

    /**
     * °C per minute: least-squares slope over the last few minutes of the
     * current cook. Null when there is too little fresh data.
     */
    fun ratePerMinute(log: LogView, probe: Int, nowMs: Long): Double? {
        if (probe !in 1..log.probeCount) return null
        val sessionStart = log.sessionStartMs ?: return null
        val from = maxOf(nowMs - WINDOW_MS, sessionStart)
        var n = 0
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        var first = Long.MAX_VALUE
        var last = Long.MIN_VALUE
        for (row in log.firstRowAtOrAfter(from) until log.size) {
            val c = log.temp(probe, row)
            if (c.isNaN()) continue
            val t = log.time(row)
            val x = (t - from) / 60_000.0
            n++
            sx += x
            sy += c
            sxx += x * x
            sxy += x * c
            first = minOf(first, t)
            last = maxOf(last, t)
        }
        if (n < MIN_POINTS || last - first < MIN_SPAN_MS) return null
        val den = n * sxx - sx * sx
        if (den == 0.0) return null
        return (n * sxy - sx * sy) / den
    }

    /** Whole minutes until [targetC]; 0 once reached; null while not heating up. */
    fun minutesTo(targetC: Double, currentC: Double, ratePerMin: Double?): Int? {
        if (currentC >= targetC) return 0
        if (ratePerMin == null || ratePerMin < MIN_RATE_C_PER_MIN) return null
        val minutes = ((targetC - currentC) / ratePerMin).roundToInt().coerceAtLeast(1)
        return minutes.takeIf { it < MAX_ETA_MIN }
    }
}

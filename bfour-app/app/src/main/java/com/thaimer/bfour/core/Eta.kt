package com.thaimer.bfour.core

import kotlin.math.roundToInt

/** One temperature reading. */
data class Sample(val timeMs: Long, val celsius: Double)

object Eta {
    private const val WINDOW_MS = 5 * 60_000L
    private const val MIN_SPAN_MS = 90_000L
    private const val MIN_RATE_C_PER_MIN = 0.05

    /**
     * Minutes until [targetC] using a least-squares slope over the last few minutes.
     * Null when the temperature isn't rising steadily enough to make a guess.
     */
    fun minutesToTarget(history: List<Sample>, targetC: Double): Int? {
        if (history.size < 3) return null
        val last = history.last()
        if (last.celsius >= targetC) return 0
        val recent = history.filter { it.timeMs >= last.timeMs - WINDOW_MS }
        if (recent.size < 3 || last.timeMs - recent.first().timeMs < MIN_SPAN_MS) return null

        val t0 = recent.first().timeMs
        val xs = recent.map { (it.timeMs - t0) / 60_000.0 }
        val ys = recent.map { it.celsius }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        if (den == 0.0) return null
        val ratePerMin = num / den
        if (ratePerMin < MIN_RATE_C_PER_MIN) return null
        val minutes = (targetC - last.celsius) / ratePerMin
        return minutes.roundToInt().coerceAtLeast(1).takeIf { it < 24 * 60 }
    }
}

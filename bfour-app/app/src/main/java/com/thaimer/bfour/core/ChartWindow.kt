package com.thaimer.bfour.core

enum class ChartRange(val spanMs: Long?) {
    MIN_15(15 * 60_000L),
    HOUR_1(60 * 60_000L),
    HOUR_3(3 * 60 * 60_000L),

    /** The whole current cook. */
    ALL(null),
}

/** One cook on the chart: readings without a long break between them. */
data class Cook(val startMs: Long, val endMs: Long, val ongoing: Boolean) {
    val durationMs: Long get() = endMs - startMs
}

object ChartWindow {
    private const val MIN_SPAN_MS = 10 * 60_000L

    /** The current cook, or the last finished one; null when there is no data at all. */
    fun cook(log: LogView, nowMs: Long): Cook? {
        val start = log.sessionStartMs ?: return null
        val last = log.time(log.size - 1)
        val ongoing = nowMs - last <= TempLog.SESSION_GAP_MS
        return Cook(start, if (ongoing) nowMs else last, ongoing)
    }

    /**
     * Visible time range: the chosen span of the past plus a fifth of it ahead
     * of now, so the forecast to the target fits. [ChartRange.ALL] fits the
     * whole current cook, or shows the last one as it was when nothing cooks.
     */
    fun of(range: ChartRange, log: LogView, nowMs: Long): Pair<Long, Long> {
        range.spanMs?.let { return nowMs - it to nowMs + it / 5 }
        val cook = cook(log, nowMs) ?: return nowMs - MIN_SPAN_MS to nowMs + MIN_SPAN_MS / 5
        val past = maxOf(cook.durationMs, MIN_SPAN_MS)
        return if (cook.ongoing) {
            nowMs - past to nowMs + past / 5
        } else {
            cook.endMs - past to cook.endMs + past / 20
        }
    }
}

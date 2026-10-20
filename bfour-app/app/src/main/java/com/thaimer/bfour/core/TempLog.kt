package com.thaimer.bfour.core

/**
 * Temperature history for the chart: one row every few seconds with every
 * probe's reading. It has nothing to do with targets, so changing or clearing
 * a target never touches it; only [clear] does.
 *
 * Rows are only ever appended. Growing, trimming and clearing allocate new
 * arrays, so a [LogView] handed to the UI never changes underneath it.
 */
class TempLog(val probeCount: Int) {
    private var times = LongArray(INITIAL_CAPACITY)
    private var temps = Array(probeCount) { FloatArray(INITIAL_CAPACITY) }
    private var size = 0
    private var sessionStart = 0

    fun view(): LogView = LogView(times, temps, size, sessionStart)

    val oldestTimeMs: Long? get() = if (size > 0) times[0] else null

    /**
     * [values] index 0 is probe 1; null means no reading. Rows must arrive in
     * time order; an older row is ignored (returns false) so the chart never
     * runs backwards.
     */
    fun append(timeMs: Long, values: List<Double?>): Boolean {
        if (size > 0 && timeMs <= times[size - 1]) return false
        if (size == times.size) grow()
        // A long silence (probes in the case, thermometer off) starts a new cook.
        if (size > 0 && timeMs - times[size - 1] > SESSION_GAP_MS) sessionStart = size
        times[size] = timeMs
        for (p in 0 until probeCount) {
            temps[p][size] = values.getOrNull(p)?.takeIf { it in MIN_PLAUSIBLE_C..MAX_PLAUSIBLE_C }?.toFloat() ?: Float.NaN
        }
        size++
        return true
    }

    /** Forgets rows older than [cutoffMs]. Returns true if anything was dropped. */
    fun dropBefore(cutoffMs: Long): Boolean {
        var first = 0
        while (first < size && times[first] < cutoffMs) first++
        if (first == 0) return false
        val keep = size - first
        val capacity = maxOf(INITIAL_CAPACITY, keep * 2)
        times = times.copyOfRange(first, first + keep).copyOf(capacity)
        temps = Array(probeCount) { p -> temps[p].copyOfRange(first, first + keep).copyOf(capacity) }
        size = keep
        sessionStart = (sessionStart - first).coerceAtLeast(0)
        return true
    }

    /** The same history for a thermometer with [newCount] probes; extra probes stay empty. */
    fun resized(newCount: Int): TempLog {
        val copy = TempLog(newCount)
        val v = view()
        for (row in 0 until v.size) {
            copy.append(v.time(row), (1..newCount).map { p ->
                if (p <= v.probeCount) v.temp(p, row).takeUnless { it.isNaN() }?.toDouble() else null
            })
        }
        return copy
    }

    fun clear() {
        times = LongArray(INITIAL_CAPACITY)
        temps = Array(probeCount) { FloatArray(INITIAL_CAPACITY) }
        size = 0
        sessionStart = 0
    }

    private fun grow() {
        val capacity = times.size * 2
        times = times.copyOf(capacity)
        temps = Array(probeCount) { p -> temps[p].copyOf(capacity) }
    }

    companion object {
        private const val INITIAL_CAPACITY = 1024

        /** Readings outside this range are sensor glitches; they are stored as "no reading". */
        const val MIN_PLAUSIBLE_C = -50.0
        const val MAX_PLAUSIBLE_C = 350.0

        /** Silence longer than this separates two cooks on the chart. */
        const val SESSION_GAP_MS = 2 * 60 * 60_000L
    }
}

/** Read-only snapshot of [TempLog]. Probe numbers are 1-based, as on the thermometer. */
class LogView internal constructor(
    private val times: LongArray,
    private val temps: Array<FloatArray>,
    val size: Int,
    /** First row of the current cook. */
    val sessionStart: Int,
) {
    val probeCount: Int get() = temps.size

    fun time(row: Int): Long = times[row]

    /** NaN when the probe had no reading at that moment. */
    fun temp(probe: Int, row: Int): Float = temps[probe - 1][row]

    /** Index of the first row at or after [timeMs], or [size] if there is none. */
    fun firstRowAtOrAfter(timeMs: Long): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] < timeMs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Row closest in time to [timeMs], or -1 for an empty log. */
    fun nearestRow(timeMs: Long): Int {
        if (size == 0) return -1
        val i = firstRowAtOrAfter(timeMs)
        if (i == 0) return 0
        if (i == size) return size - 1
        return if (timeMs - times[i - 1] <= times[i] - timeMs) i - 1 else i
    }

    val sessionStartMs: Long? get() = if (sessionStart < size) times[sessionStart] else null

    companion object {
        val EMPTY = LogView(LongArray(0), emptyArray(), 0, 0)
    }
}

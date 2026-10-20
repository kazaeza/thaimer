package com.thaimer.bfour.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendAndChartTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val t0 = 1_800_000_000_000L

    /** One probe heating at [ratePerMin] for [minutes], sampled every 5 s like the app does. */
    private fun heating(from: Double, ratePerMin: Double, minutes: Int): TempLog {
        val log = TempLog(1)
        for (i in 0..minutes * 12) {
            log.append(t0 + i * 5_000L, listOf(from + ratePerMin * i / 12.0))
        }
        return log
    }

    @Test
    fun `rate and minutes to target`() {
        val log = heating(20.0, 1.0, 20) // 40 °C after 20 min
        val now = t0 + 20 * minute
        val rate = Trend.ratePerMinute(log.view(), 1, now)!!
        assertEquals(1.0, rate, 1e-6)
        assertEquals(23, Trend.minutesTo(63.0, 40.0, rate))
        assertEquals(0, Trend.minutesTo(63.0, 63.5, rate))
    }

    @Test
    fun `no forecast when flat, cooling, stale or too short`() {
        val flat = heating(40.0, 0.0, 20)
        assertNull(Trend.minutesTo(63.0, 40.0, Trend.ratePerMinute(flat.view(), 1, t0 + 20 * minute)))
        val cooling = heating(60.0, -0.5, 20)
        assertNull(Trend.minutesTo(63.0, 50.0, Trend.ratePerMinute(cooling.view(), 1, t0 + 20 * minute)))
        val fresh = heating(20.0, 1.0, 20)
        // Last reading 30 minutes ago: nothing to go on.
        assertNull(Trend.ratePerMinute(fresh.view(), 1, t0 + 50 * minute))
        val short = heating(20.0, 1.0, 1)
        assertNull(Trend.ratePerMinute(short.view(), 1, t0 + minute))
        assertNull(Trend.ratePerMinute(fresh.view(), 2, t0 + 20 * minute))
    }

    @Test
    fun `temperature ticks are round and cover the data`() {
        val (lo, hi, step) = ChartTicks.tempRange(21.3, 58.9)
        assertEquals(10.0, step, 0.0)
        assertEquals(20.0, lo, 0.0)
        assertEquals(60.0, hi, 0.0)
        // A nearly flat line still gets a readable span.
        val (flo, fhi, _) = ChartTicks.tempRange(50.0, 50.4)
        assertTrue(fhi - flo >= 10.0)
        assertTrue(flo <= 50.0 && fhi >= 50.4)
    }

    @Test
    fun `absurd data cannot produce an endless grid`() {
        val (lo, hi, step) = ChartTicks.tempRange(0.0, 5.0e13)
        assertTrue((hi - lo) / step <= 12)
        assertEquals(1.0, ChartTicks.tempStep(Double.NaN), 0.0)
        assertEquals(1.0, ChartTicks.tempStep(0.0), 0.0)
    }

    @Test
    fun `time ticks land on round clock times`() {
        assertEquals(5 * minute, ChartTicks.timeStep(20 * minute))
        assertEquals(hour, ChartTicks.timeStep(4 * hour))
        // UTC+3: ticks at whole local quarter hours.
        val offset = 3 * hour
        val from = 1_700_000_123_000L
        val ticks = ChartTicks.timeTicks(from, from + hour, 15 * minute) { offset.toInt() }
        assertEquals(4, ticks.size)
        ticks.forEach { assertEquals(0L, (it + offset) % (15 * minute)) }
        assertTrue(ticks.first() >= from)
    }

    @Test
    fun `window follows the current cook and leaves room for the forecast`() {
        val log = heating(20.0, 1.0, 60)
        val now = t0 + 60 * minute
        val (from, to) = ChartWindow.of(ChartRange.ALL, log.view(), now)
        assertEquals(t0, from)
        assertEquals(now + 12 * minute, to)
        val (f15, t15) = ChartWindow.of(ChartRange.MIN_15, log.view(), now)
        assertEquals(now - 15 * minute, f15)
        assertEquals(now + 3 * minute, t15)
        val cook = ChartWindow.cook(log.view(), now)!!
        assertTrue(cook.ongoing)
        assertEquals(60 * minute, cook.durationMs)
    }

    @Test
    fun `a finished cook is shown as it was`() {
        val log = heating(20.0, 1.0, 60)
        val now = t0 + 60 * minute + TempLog.SESSION_GAP_MS + hour
        val cook = ChartWindow.cook(log.view(), now)!!
        assertFalse(cook.ongoing)
        assertEquals(60 * minute, cook.durationMs)
        val (from, to) = ChartWindow.of(ChartRange.ALL, log.view(), now)
        assertEquals(t0, from)
        assertTrue(to < now)
        assertNull(ChartWindow.cook(LogView.EMPTY, now))
    }
}

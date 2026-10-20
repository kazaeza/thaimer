package com.thaimer.bfour.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TempLogTest {
    private val minute = 60_000L

    @Test
    fun `rows keep every probe and mark missing readings`() {
        val log = TempLog(2)
        log.append(1_000, listOf(20.5, null))
        val v = log.view()
        assertEquals(1, v.size)
        assertEquals(20.5f, v.temp(1, 0))
        assertTrue(v.temp(2, 0).isNaN())
    }

    @Test
    fun `resizing keeps the history for a model with another probe count`() {
        val log = TempLog(2)
        log.append(1_000, listOf(20.0, 30.0))
        log.append(2_000, listOf(21.0, null))
        val one = log.resized(1).view()
        assertEquals(1, one.probeCount)
        assertEquals(2, one.size)
        assertEquals(21f, one.temp(1, 1))
        val three = log.resized(3).view()
        assertEquals(30f, three.temp(2, 0))
        assertTrue(three.temp(3, 0).isNaN())
    }

    @Test
    fun `sensor glitches are stored as missing readings`() {
        val log = TempLog(1)
        log.append(1_000, listOf(3276.7))
        log.append(2_000, listOf(-300.0))
        assertTrue(log.view().temp(1, 0).isNaN())
        assertTrue(log.view().temp(1, 1).isNaN())
    }

    @Test
    fun `older rows are refused so time only moves forward`() {
        val log = TempLog(1)
        assertTrue(log.append(5_000, listOf(20.0)))
        assertFalse(log.append(5_000, listOf(21.0)))
        assertFalse(log.append(4_000, listOf(21.0)))
        assertEquals(1, log.view().size)
    }

    @Test
    fun `a view does not change when the log grows, trims or clears`() {
        val log = TempLog(1)
        repeat(10) { log.append(it * 5_000L, listOf(it.toDouble())) }
        val before = log.view()
        repeat(5000) { log.append(100_000L + it * 5_000L, listOf(99.0)) }
        log.dropBefore(200_000L)
        log.clear()
        assertEquals(10, before.size)
        assertEquals(9f, before.temp(1, 9))
        assertEquals(45_000L, before.time(9))
    }

    @Test
    fun `a long silence starts a new cook`() {
        val log = TempLog(1)
        log.append(0, listOf(5.0))
        log.append(minute, listOf(6.0))
        assertEquals(0L, log.view().sessionStartMs)
        val later = minute + TempLog.SESSION_GAP_MS + 1
        log.append(later, listOf(7.0))
        assertEquals(later, log.view().sessionStartMs)
        // A short break (lost connection) stays in the same cook.
        log.append(later + 30 * minute, listOf(8.0))
        assertEquals(later, log.view().sessionStartMs)
    }

    @Test
    fun `trimming keeps the newest rows and the current cook`() {
        val log = TempLog(1)
        (0 until 100).forEach { log.append(it * minute, listOf(it.toDouble())) }
        assertTrue(log.dropBefore(40 * minute))
        val v = log.view()
        assertEquals(60, v.size)
        assertEquals(40 * minute, v.time(0))
        assertEquals(40f, v.temp(1, 0))
        assertEquals(40 * minute, v.sessionStartMs)
        assertFalse(log.dropBefore(0))
    }

    @Test
    fun `binary searches by time`() {
        val log = TempLog(1)
        listOf(0L, 10L, 20L, 30L).forEach { log.append(it, listOf(1.0)) }
        val v = log.view()
        assertEquals(0, v.firstRowAtOrAfter(-5))
        assertEquals(1, v.firstRowAtOrAfter(10))
        assertEquals(2, v.firstRowAtOrAfter(11))
        assertEquals(4, v.firstRowAtOrAfter(31))
        assertEquals(1, v.nearestRow(14))
        assertEquals(2, v.nearestRow(16))
        assertEquals(3, v.nearestRow(1_000))
        assertEquals(-1, LogView.EMPTY.nearestRow(5))
        assertNull(LogView.EMPTY.sessionStartMs)
    }
}

package com.thaimer.bfour.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EtaAndUnitTest {
    @Test
    fun `steady rise predicts the remaining minutes`() {
        // 1 °C per minute for 4 minutes, sampled every 5 s; 40 °C now, target 60 °C.
        val history = (0..48).map { i -> Sample(i * 5_000L, 36.0 + i * 5.0 / 60.0) }
        assertEquals(20, Eta.minutesToTarget(history, 60.0))
    }

    @Test
    fun `flat or falling temperature gives no estimate`() {
        val flat = (0..48).map { i -> Sample(i * 5_000L, 40.0) }
        assertNull(Eta.minutesToTarget(flat, 60.0))
        val falling = (0..48).map { i -> Sample(i * 5_000L, 50.0 - i * 0.1) }
        assertNull(Eta.minutesToTarget(falling, 60.0))
    }

    @Test
    fun `too little history gives no estimate`() {
        val short = (0..5).map { i -> Sample(i * 5_000L, 36.0 + i) }
        assertNull(Eta.minutesToTarget(short, 60.0))
    }

    @Test
    fun `unit conversion for targets`() {
        assertEquals(63, TempUnit.C.wholeDegrees(63.0))
        assertEquals(145, TempUnit.F.wholeDegrees(63.0))
        assertEquals(146, TempUnit.F.wholeDegrees(TempUnit.F.toCelsius(146.0)))
        assertEquals(212.0, TempUnit.F.fromCelsius(100.0), 1e-9)
    }
}

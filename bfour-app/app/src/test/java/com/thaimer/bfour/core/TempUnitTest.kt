package com.thaimer.bfour.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TempUnitTest {
    @Test
    fun `unit conversion for targets`() {
        assertEquals(63, TempUnit.C.wholeDegrees(63.0))
        assertEquals(145, TempUnit.F.wholeDegrees(63.0))
        assertEquals(146, TempUnit.F.wholeDegrees(TempUnit.F.toCelsius(146.0)))
        assertEquals(212.0, TempUnit.F.fromCelsius(100.0), 1e-9)
    }
}

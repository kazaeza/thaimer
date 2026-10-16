package com.kazaeza.thaimer.ui

import com.kazaeza.thaimer.data.Workouts
import com.kazaeza.thaimer.ui.detail.PlanRow
import com.kazaeza.thaimer.ui.detail.planRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRowsTest {
    @Test
    fun `tabata cycles fold into a repeat`() {
        val rows = planRows(Workouts.byId("tabata")!!)
        val repeats = rows.filterIsInstance<PlanRow.Repeat>()
        assertEquals(4, repeats.size)
        assertTrue(repeats.all { it.times == 4 })
    }

    @Test
    fun `identical circuits fold into one header`() {
        val rows = planRows(Workouts.byId("camp")!!)
        val header = rows.filterIsInstance<PlanRow.Header>().single { it.times > 1 }
        assertEquals("Раунды 1–4 · Круговая", header.title)
        assertEquals(4, header.times)
        assertEquals(60, header.restBetween)
    }

    @Test
    fun `sparring rounds show their random part as one row`() {
        val rows = planRows(Workouts.byId("sparring")!!)
        assertEquals(5, rows.count { it is PlanRow.Sparring })
        assertEquals(listOf("Раунд 1 · Разведка", "Раунд 5 · Финал"), rows.filterIsInstance<PlanRow.Header>().map { it.title }.filter { it.startsWith("Раунд") }.let { listOf(it.first(), it.last()) })
    }

    @Test
    fun `every workout's plan adds up`() {
        for (w in Workouts.all) {
            val headers = planRows(w).filterIsInstance<PlanRow.Header>()
            val total = headers.sumOf { it.seconds * it.times + it.restBetween * (it.times - 1) }
            assertEquals(w.id, w.seconds, total)
        }
    }

    @Test
    fun `russian plurals`() {
        assertEquals("1 раунд", rounds(1))
        assertEquals("3 раунда", rounds(3))
        assertEquals("5 раундов", rounds(5))
        assertEquals("11 раундов", rounds(11))
        assertEquals("22 раунда", rounds(22))
        assertEquals("6 ч 20 мин", hoursMinutes(6 * 3600 + 20 * 60))
        assertEquals("00:59", remainingClock(58_100))
        assertEquals("00:58", elapsedClock(58_900))
    }
}

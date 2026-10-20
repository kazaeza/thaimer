package com.thaimer.bfour.alarm

import com.thaimer.bfour.alarm.AlarmRules.Event
import com.thaimer.bfour.alarm.AlarmRules.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRulesTest {
    private fun run(temps: List<Double?>, target: Double?): Pair<State, List<Event>> {
        var s = State()
        val events = mutableListOf<Event>()
        temps.forEach { t ->
            val r = AlarmRules.evaluate(s, t, target)
            s = r.state
            r.event?.let { events += it }
        }
        return s to events
    }

    @Test
    fun `almost then done, each once`() {
        val (s, events) = run(listOf(40.0, 55.0, 58.5, 60.0, 63.0, 64.0, 65.0), 63.0)
        assertEquals(listOf(Event.ALMOST, Event.DONE), events)
        assertTrue(s.ringing)
    }

    @Test
    fun `no target, no alarm`() {
        val (s, events) = run(listOf(40.0, 90.0), null)
        assertTrue(events.isEmpty())
        assertFalse(s.ringing)
    }

    @Test
    fun `lost probe keeps ringing until acknowledged`() {
        var (s, _) = run(listOf(63.5), 63.0)
        s = AlarmRules.evaluate(s, null, 63.0).state
        assertTrue(s.ringing)
        s = AlarmRules.acknowledge(s)
        assertFalse(s.ringing)
    }

    @Test
    fun `acknowledged alarm stays quiet while still hot`() {
        var (s, _) = run(listOf(63.5), 63.0)
        s = AlarmRules.acknowledge(s)
        val r = AlarmRules.evaluate(s, 70.0, 63.0)
        assertNull(r.event)
        assertFalse(r.state.ringing)
    }

    @Test
    fun `re-arms after the probe goes into fresh meat`() {
        var (s, _) = run(listOf(63.5), 63.0)
        s = AlarmRules.acknowledge(s)
        val events = mutableListOf<Event>()
        listOf(20.0, 59.0, 63.0).forEach { t ->
            val r = AlarmRules.evaluate(s, t, 63.0)
            s = r.state
            r.event?.let { events += it }
        }
        assertEquals(listOf(Event.ALMOST, Event.DONE), events)
    }

    @Test
    fun `changing the target starts over`() {
        val (s, _) = run(listOf(63.5), 63.0)
        val r = AlarmRules.evaluate(s, 67.0, 71.0)
        assertEquals(Event.ALMOST, r.event)
        assertFalse(r.state.ringing)
    }
}

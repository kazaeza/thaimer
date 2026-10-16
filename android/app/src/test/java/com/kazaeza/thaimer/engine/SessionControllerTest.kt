package com.kazaeza.thaimer.engine

import com.kazaeza.thaimer.data.Workouts
import com.kazaeza.thaimer.model.Cue
import com.kazaeza.thaimer.model.Sound
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionControllerTest {
    private class RecordingSink : CueSink {
        val played = mutableListOf<Pair<Long, Cue>>()
        var silenced = 0
        var now: () -> Long = { 0 }
        override fun play(cue: Cue) {
            played += now() to cue
        }
        override fun silence() {
            silenced++
        }
    }

    private val classic = Workouts.byId("classic")!!

    private fun TestScope.controller(onFinished: (Session) -> Unit = {}): Pair<SessionController, RecordingSink> {
        val sink = RecordingSink().also { it.now = { testScheduler.currentTime } }
        return SessionController(backgroundScope, sink, { testScheduler.currentTime }, onFinished) to sink
    }

    @Test
    fun `cues fire on time through the whole workout`() = runTest {
        var finished: Session? = null
        val (c, sink) = controller { finished = it }
        c.start(classic, prepSeconds = 10, tenSecondWarning = true, seed = 1)
        runCurrent()
        assertEquals(0L, sink.played.single().first)

        val total = c.state.value!!.timeline.totalMs
        advanceTimeBy(total + 1)
        runCurrent()

        sink.played.forEach { (playedAt, cue) -> assertEquals(cue.atMs, playedAt) }
        assertTrue(sink.played.last().second.finish)
        val s = c.state.value!!
        assertTrue(s.finished)
        assertFalse(s.running)
        assertEquals(total, s.elapsedAt(testScheduler.currentTime))
        assertNotNull(finished)
        assertEquals(total, finished!!.workedMs)
    }

    @Test
    fun `pause freezes the clock and the cues`() = runTest {
        val (c, sink) = controller()
        c.start(classic, 10, true, seed = 1)
        advanceTimeBy(5_000); runCurrent()
        c.pause()
        val heard = sink.played.size
        advanceTimeBy(60_000); runCurrent()
        assertEquals(heard, sink.played.size)
        assertEquals(5_000, c.state.value!!.elapsedAt(testScheduler.currentTime))

        c.resume()
        advanceTimeBy(5_000); runCurrent()
        val s = c.state.value!!
        assertEquals(10_000, s.elapsedAt(testScheduler.currentTime))
        assertEquals(1, s.segment)
        assertEquals(Sound.GO, sink.played.last().second.sound)
        assertEquals(10_000, s.workedMs + (testScheduler.currentTime - s.anchorMs))
    }

    @Test
    fun `next and previous jump between intervals and announce them`() = runTest {
        val (c, sink) = controller()
        c.start(classic, 10, true, seed = 1)
        runCurrent()
        c.next()
        val t = c.state.value!!.timeline
        assertEquals(t.segments[1].startMs, c.state.value!!.elapsedAt(testScheduler.currentTime))
        assertEquals(t.segments[1].startMs, sink.played.last().second.atMs)

        advanceTimeBy(1_000); runCurrent()
        c.previous() // within the first 3 s: back to the previous interval
        assertEquals(0, t.indexAt(c.state.value!!.elapsedAt(testScheduler.currentTime)))

        c.next(); advanceTimeBy(10_000); runCurrent()
        c.previous() // deep into it: restart the same interval
        assertEquals(t.segments[1].startMs, c.state.value!!.elapsedAt(testScheduler.currentTime))
    }

    @Test
    fun `skipping while paused announces on resume`() = runTest {
        val (c, sink) = controller()
        c.start(classic, 10, true, seed = 1)
        runCurrent()
        c.pause()
        c.next()
        val heard = sink.played.size
        c.resume()
        assertEquals(heard + 1, sink.played.size)
        assertEquals(c.state.value!!.timeline.segments[1].startMs, sink.played.last().second.atMs)
    }

    @Test
    fun `next from the last interval finishes`() = runTest {
        var done = false
        val (c, sink) = controller { done = true }
        c.start(classic, 10, true, seed = 1)
        c.jumpTo(c.state.value!!.timeline.segments.lastIndex)
        c.next()
        assertTrue(done)
        assertTrue(c.state.value!!.finished)
        assertTrue(sink.played.last().second.finish)
    }

    @Test
    fun `stop clears the session`() = runTest {
        val (c, sink) = controller()
        c.start(classic, 10, true, seed = 1)
        advanceTimeBy(3_000); runCurrent()
        c.stop()
        assertNull(c.state.value)
        assertEquals(1, sink.silenced)
        val heard = sink.played.size
        advanceTimeBy(600_000); runCurrent()
        assertEquals(heard, sink.played.size)
    }
}

package com.kazaeza.thaimer.model

import com.kazaeza.thaimer.data.Workouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CueBuilderTest {
    private fun timeline(id: String, seed: Long = 7) = TimelineBuilder.build(Workouts.byId(id)!!, 10, Random(seed))

    @Test
    fun `session opens with the workout name and closes with the finish bell`() {
        val t = timeline("classic")
        val cues = CueBuilder.build(t)
        assertEquals(0L, cues.first().atMs)
        assertEquals("Тайская классика. Приготовься.", cues.first().speech)
        val last = cues.last()
        assertTrue(last.finish)
        assertEquals(t.totalMs, last.atMs)
        assertEquals(Sound.BELL_END, last.sound)
        assertEquals(cues.sortedBy { it.atMs }, cues)
        assertEquals(cues.size, cues.map { it.atMs }.toSet().size)
    }

    @Test
    fun `bell opens each round, triple bell closes it, clapper warns 10 seconds before the end`() {
        val t = timeline("classic")
        val byTime = CueBuilder.build(t).associateBy { it.atMs }
        val rounds = t.blocks.filter { it.type == BlockType.ROUND }
        rounds.forEach { r ->
            assertEquals(Sound.BELL, byTime.getValue(r.startMs).sound)
            assertEquals(Sound.CLAPPER, byTime.getValue(r.endMs - 10_000).sound)
            val endSound = byTime.getValue(r.endMs).sound
            assertTrue(endSound == Sound.BELL_END || endSound == Sound.BELL)
        }
        assertEquals("Раунд 1. Скакалка. Ровный темп.", byTime.getValue(rounds[0].startMs).speech)
        assertTrue(byTime.getValue(rounds.last().startMs).speech!!.startsWith("Последний раунд!"))
    }

    @Test
    fun `clapper can be switched off`() {
        val cues = CueBuilder.build(timeline("classic"), tenSecondWarning = false)
        assertTrue(cues.none { it.sound == Sound.CLAPPER })
    }

    @Test
    fun `bursts in sparring arrive without countdown or preview`() {
        repeat(50) { seed ->
            val t = timeline("sparring", seed.toLong())
            val cues = CueBuilder.build(t).associateBy { it.atMs }
            t.segments.filter { it.step.surprise }.forEach { burst ->
                for (n in 1..3) assertTrue(cues[burst.startMs - n * 1000L]?.sound != Sound.PIP)
                assertTrue(cues.getValue(burst.startMs).sound in setOf(Sound.BURST, Sound.CLAPPER))
            }
            t.segments.filter { it.step.kind == Kind.REST }.forEach { rest ->
                val headsUp = cues[rest.endMs - 10_000]?.speech.orEmpty()
                assertTrue(headsUp.startsWith("Приготовься."))
            }
        }
    }

    @Test
    fun `sparring rounds are introduced by name and announced ahead during rest`() {
        val t = timeline("sparring")
        val byTime = CueBuilder.build(t).associateBy { it.atMs }
        val rounds = t.blocks.filter { it.type == BlockType.ROUND }
        assertEquals("Раунд 1. Разведка. Жди взрывов.", byTime.getValue(rounds[0].startMs).speech)
        assertEquals("Последний раунд! Финал. Жди взрывов.", byTime.getValue(rounds.last().startMs).speech)
        assertEquals("Приготовься. Дальше — раунд 2, разгон.", byTime.getValue(rounds[1].startMs - 10_000).speech)
        assertEquals("Приготовься. Дальше — последний раунд, финал.", byTime.getValue(rounds.last().startMs - 10_000).speech)
    }

    @Test
    fun `pips count down into announced intervals`() {
        val t = timeline("tabata")
        val cues = CueBuilder.build(t).associateBy { it.atMs }
        t.segments.filter { it.durationMs >= 6_000 }.dropLast(1).forEach { s ->
            for (n in 1..3) {
                val sound = cues[s.endMs - n * 1000L]?.sound
                assertTrue("${s.step.name} at -$n: $sound", sound == Sound.PIP || sound == Sound.CLAPPER)
            }
        }
        val breath = t.segments.first { it.step.name == "Вдох" }
        assertNull(cues.getValue(breath.startMs).speech)
    }
}

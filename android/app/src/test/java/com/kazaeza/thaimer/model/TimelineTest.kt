package com.kazaeza.thaimer.model

import com.kazaeza.thaimer.data.Workouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TimelineTest {
    private val sparring = Workouts.byId("sparring")!!

    @Test
    fun `timeline covers the workout exactly, with prep first`() {
        for (w in Workouts.all) for (seed in 0L until 50L) {
            val t = TimelineBuilder.build(w, prepSeconds = 10, Random(seed))
            assertEquals(w.id, (w.seconds + 10) * 1000L, t.totalMs)
            assertEquals(Kind.PREP, t.segments.first().step.kind)
            assertEquals(BlockType.PREP, t.blocks.first().type)
            t.segments.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
            t.blocks.forEach { b ->
                assertEquals(b.startMs, t.segments[b.segments.first].startMs)
                assertEquals(b.endMs, t.segments[b.segments.last].endMs)
            }
            assertEquals(w.rounds, t.totalRounds)
        }
    }

    @Test
    fun `no prep block when prep is off`() {
        val t = TimelineBuilder.build(sparring, prepSeconds = 0, Random(1))
        assertEquals(BlockType.WARMUP, t.blocks.first().type)
        assertEquals(sparring.seconds * 1000L, t.totalMs)
    }

    @Test
    fun `sparring rounds roll bursts that ambush, then pressure, never the same twice in a row`() {
        repeat(500) { seed ->
            val t = TimelineBuilder.build(sparring, prepSeconds = 0, Random(seed))
            for (block in t.blocks.filter { it.type == BlockType.ROUND }) {
                val steps = block.segments.map { t.segments[it].step }
                assertEquals(180, steps.sumOf { it.seconds })
                assertTrue("seed $seed: tiny step in $steps", steps.all { it.seconds >= 5 })
                val bursts = steps.filter { it.surprise }
                assertTrue("seed $seed: ${bursts.size} bursts", bursts.size in 2..8)
                assertTrue(bursts.all { it.kind == Kind.BURST })
                steps.forEachIndexed { i, s ->
                    if (s.surprise) assertEquals(Kind.PRESSURE, steps[i + 1].kind)
                }
                bursts.zipWithNext().forEach { (a, b) -> assertNotEquals(a.name, b.name) }
                assertEquals("Добивай!", steps.last().name)
                assertTrue(steps.zipWithNext().none { (a, b) -> a.kind == Kind.FIGHT && b.kind == Kind.FIGHT })
            }
        }
    }

    @Test
    fun `later sparring rounds get busier`() {
        val counts = IntArray(5)
        repeat(300) { seed ->
            val t = TimelineBuilder.build(sparring, prepSeconds = 0, Random(seed))
            t.blocks.filter { it.type == BlockType.ROUND }.forEachIndexed { i, b ->
                counts[i] += b.segments.count { t.segments[it].step.surprise }
            }
        }
        assertTrue(counts.toList().toString(), counts[0] < counts[2] && counts[2] < counts[4])
    }

    @Test
    fun `every session rolls a different sparring layout`() {
        val layouts = (0L until 20L).map { seed ->
            TimelineBuilder.build(sparring, 10, Random(seed)).segments.map { it.step.name to it.durationMs }
        }
        assertEquals(20, layouts.toSet().size)
    }

    @Test
    fun `indexAt finds the running segment`() {
        val t = TimelineBuilder.build(Workouts.byId("classic")!!, prepSeconds = 10, Random(0))
        assertEquals(0, t.indexAt(0))
        assertEquals(0, t.indexAt(9_999))
        assertEquals(1, t.indexAt(10_000))
        t.segments.forEach { s ->
            assertEquals(s.index, t.indexAt(s.startMs))
            assertEquals(s.index, t.indexAt(s.endMs - 1))
        }
        assertEquals(t.segments.lastIndex, t.indexAt(t.totalMs))
        assertEquals(t.segments.lastIndex, t.indexAt(t.totalMs + 5_000))
    }
}

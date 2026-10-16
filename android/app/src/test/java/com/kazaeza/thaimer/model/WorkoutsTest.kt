package com.kazaeza.thaimer.model

import com.kazaeza.thaimer.data.Workouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutsTest {
    private val all = Workouts.all

    @Test
    fun `ten workouts with unique ids`() {
        assertEquals(10, all.size)
        assertEquals(10, all.map { it.id }.toSet().size)
    }

    @Test
    fun `at least one workout simulates sparring with random bursts`() {
        assertTrue(all.any { it.hasRandomBursts })
    }

    @Test
    fun `every workout is a sensible home session`() {
        for (w in all) {
            assertTrue("${w.id} too short/long: ${w.seconds}", w.seconds in 15 * 60..45 * 60)
            assertTrue("${w.id} needs rounds", w.rounds >= 2)
            assertTrue(w.intensity in 1..5)
            assertTrue(w.title.isNotBlank() && w.tagline.isNotBlank() && w.description.isNotBlank())
            assertEquals("${w.id} should open with a warm-up", BlockType.WARMUP, w.blocks.first().type)
            assertEquals("${w.id} should end with a cool-down", BlockType.COOLDOWN, w.blocks.last().type)
        }
    }

    @Test
    fun `every step is long enough and fits on screen`() {
        for (w in all) for (b in w.blocks) for (p in b.parts) {
            val steps = when (p) {
                is Part.Fixed -> listOf(p.step)
                is Part.Sparring -> listOfNotNull(p.finish)
            }
            for (s in steps) {
                assertTrue("${w.id}/${s.name}: ${s.seconds}s", s.seconds >= 10)
                assertTrue("${w.id}/${s.name} name too long", s.name.length <= 28)
                assertTrue("${w.id}/${s.name} hint too long: ${s.hint.length}", s.hint.length <= 80)
            }
        }
    }

    @Test
    fun `the voice never reads symbols out loud`() {
        val spoken = all.flatMap { w ->
            w.blocks.flatMap { b ->
                listOf(b.intro) + b.parts.flatMap { p ->
                    when (p) {
                        is Part.Fixed -> listOf(p.step.say)
                        is Part.Sparring -> p.bursts.map { it.say } + listOfNotNull(p.finish?.say)
                    }
                }
            }
        }
        spoken.forEach { assertTrue("\"$it\" would be read as a symbol", it.none { ch -> ch in "+·/()%" }) }
    }

    @Test
    fun `rounds are three minutes where the format says so`() {
        val threeMinuteFormats = setOf("classic", "sparring", "exhausted", "championship", "kicks", "clinch", "recovery")
        for (w in all.filter { it.id in threeMinuteFormats }) {
            for (b in w.blocks.filter { it.type == BlockType.ROUND }) {
                assertEquals("${w.id}/${b.title}", 180, b.seconds)
            }
        }
    }
}

package com.thaimer.bfour.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class TempLogFileTest {
    @get:Rule
    val tmp = TemporaryFolder()

    // Runs "background" writes immediately so the test can read the file.
    private val direct = Executor { it.run() }
    private val hour = 60 * 60_000L
    private val now = 1_800_000_000_000L

    private fun file() = File(tmp.root, "log.csv")

    private fun load(f: File, probes: Int = 2): TempLog {
        var result: TempLog? = null
        TempLogFile(f, direct).load(probes, now, 48 * hour) { result = it }
        return result!!
    }

    @Test
    fun `rows survive a restart`() {
        val f = file()
        val writer = TempLogFile(f, direct)
        writer.append(now - 10_000, listOf(48.6, null))
        writer.append(now - 5_000, listOf(48.7, 22.0))

        val v = load(f).view()
        assertEquals(2, v.size)
        assertEquals(48.6f, v.temp(1, 0))
        assertTrue(v.temp(2, 0).isNaN())
        assertEquals(22.0f, v.temp(2, 1))
    }

    @Test
    fun `damaged, old and future lines are dropped and the file is cleaned`() {
        val f = file()
        f.writeText(
            "${now - 50 * hour},10.0,\n" + // too old
                "garbage\n" +
                "${now - hour},50.0,51.0\n" +
                "${now - hour + 5_000},5\n" + // cut off mid-write: a probe is missing
                "${now + hour},60.0,\n" + // from the future
                "${now - hour + 10_000},abc,\n",
        )
        assertEquals(1, load(f).view().size)
        val lines = f.readLines()
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("${now - hour},50.0,51.0"))
    }

    @Test
    fun `a cut-off line glued to the next one is dropped, not read as a huge temperature`() {
        val f = file()
        // Disk was full mid-write; the next append continued the broken line.
        f.writeText("${now - hour},50.0,51.0\n${now - 10_000},5${now - 5_000},50.4,51.1\n")
        val v = load(f).view()
        assertEquals(1, v.size)
        assertEquals(50.0f, v.temp(1, 0))
    }

    @Test
    fun `a last line without a newline is repaired so the next append starts on its own line`() {
        val f = file()
        f.writeText("${now - hour},50.0,51.0\n${now - 5_000},50.4,51.1")
        assertEquals(2, load(f).view().size)
        TempLogFile(f, direct).append(now, listOf(50.5, 51.2))
        assertEquals(3, f.readLines().size)
        assertEquals(3, load(f).view().size)
    }

    @Test
    fun `implausible readings are stored as missing`() {
        val f = file()
        f.writeText("${now - 5_000},3000.0,20.0\n")
        val v = load(f).view()
        assertTrue(v.temp(1, 0).isNaN())
        assertEquals(20.0f, v.temp(2, 0))
    }

    @Test
    fun `format keeps one decimal and empty fields`() {
        assertEquals("1000,53.7,\n", TempLogFile.format(1000, listOf(53.71, null)))
        assertEquals(1000L to listOf(53.7, null), TempLogFile.parse("1000,53.7,", 2))
        assertEquals(1000L to listOf(null, null), TempLogFile.parse("1000,,", 2))
        // Wrong number of columns is damage, whichever way.
        assertNull(TempLogFile.parse("1000,", 2))
        assertNull(TempLogFile.parse("1000,1,2,3", 2))
    }

    @Test
    fun `delete removes the file`() {
        val f = file()
        TempLogFile(f, direct).append(now, listOf(1.0))
        assertTrue(f.exists())
        TempLogFile(f, direct).delete()
        assertFalse(f.exists())
    }
}

package com.thaimer.bfour.core

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Keeps [TempLog] on disk so the chart survives the app being closed or killed.
 * One CSV line per row: `time,probe1,probe2`, an empty field for "no reading".
 * All file access runs on one background thread, in the order it was requested.
 */
class TempLogFile(
    private val file: File,
    private val io: Executor = Executors.newSingleThreadExecutor(),
) {
    /**
     * Reads the saved history on the background thread and passes it to [done]
     * (also on that thread). Damaged lines, rows older than [retentionMs] and
     * rows from the future (the phone clock was moved back) are skipped, and
     * the file is rewritten without them. Appends requested after this call
     * run after the read, so they are never lost.
     */
    fun load(probeCount: Int, nowMs: Long, retentionMs: Long, done: (TempLog) -> Unit) {
        io.execute {
            val log = TempLog(probeCount)
            var dirty = false
            if (file.exists()) {
                runCatching {
                    file.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val row = parse(line, probeCount)
                            val keep = row != null &&
                                row.first >= nowMs - retentionMs &&
                                row.first <= nowMs + FUTURE_SLACK_MS &&
                                log.append(row.first, row.second)
                            if (!keep) dirty = true
                        }
                    }
                    // A cut-off last line would swallow the next append.
                    if (!dirty && !endsWithNewline()) dirty = true
                }.onFailure { dirty = true }
                if (dirty) write(log.view())
            }
            done(log)
        }
    }

    fun append(timeMs: Long, values: List<Double?>) {
        val line = format(timeMs, values)
        io.execute {
            runCatching { file.appendText(line) }
        }
    }

    /** Replaces the file with [view], atomically. A view never changes, so it is safe to read off-thread. */
    fun rewrite(view: LogView) {
        io.execute { write(view) }
    }

    fun delete() {
        io.execute { runCatching { file.delete() } }
    }

    private fun write(view: LogView) {
        runCatching {
            val text = buildString {
                for (row in 0 until view.size) {
                    append(format(view.time(row), (1..view.probeCount).map { p ->
                        view.temp(p, row).takeUnless { it.isNaN() }?.toDouble()
                    }))
                }
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private fun endsWithNewline(): Boolean {
        if (file.length() == 0L) return true
        return RandomAccessFile(file, "r").use { f ->
            f.seek(f.length() - 1)
            f.read() == '\n'.code
        }
    }

    internal companion object {
        private const val FUTURE_SLACK_MS = 60_000L

        fun format(timeMs: Long, values: List<Double?>): String = buildString {
            append(timeMs)
            values.forEach { v ->
                append(',')
                // One decimal is the sensor resolution; keeps the file small.
                if (v != null) append(Math.round(v * 10) / 10.0)
            }
            append('\n')
        }

        /** Exactly `time` plus one field per probe; anything else is a damaged line. */
        fun parse(line: String, probeCount: Int): Pair<Long, List<Double?>>? {
            val parts = line.split(',')
            if (parts.size != probeCount + 1) return null
            val time = parts[0].toLongOrNull() ?: return null
            val values = (1..probeCount).map { p ->
                val field = parts[p]
                if (field.isEmpty()) null else field.toDoubleOrNull() ?: return null
            }
            return time to values
        }
    }
}

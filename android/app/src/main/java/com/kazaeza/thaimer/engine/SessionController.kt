package com.kazaeza.thaimer.engine

import com.kazaeza.thaimer.model.Cue
import com.kazaeza.thaimer.model.CueBuilder
import com.kazaeza.thaimer.model.Timeline
import com.kazaeza.thaimer.model.TimelineBuilder
import com.kazaeza.thaimer.model.Workout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Plays cues out loud. */
interface CueSink {
    fun play(cue: Cue)

    /** Cut whatever is still sounding. */
    fun silence()
}

/**
 * A running workout. The clock is stored as an anchor so the UI can interpolate smoothly
 * between engine updates: elapsed = [elapsedAtAnchorMs] + (now - [anchorMs]) while running.
 */
data class Session(
    val timeline: Timeline,
    val elapsedAtAnchorMs: Long,
    val anchorMs: Long,
    val running: Boolean,
    val finished: Boolean,
    /** Segment the engine last saw; the UI recomputes it from the clock every frame. */
    val segment: Int,
    /** Time actually spent with the clock running. */
    val workedMs: Long,
) {
    fun elapsedAt(nowMs: Long): Long {
        val raw = if (running) elapsedAtAnchorMs + (nowMs - anchorMs) else elapsedAtAnchorMs
        return raw.coerceIn(0, timeline.totalMs)
    }
}

/**
 * Owns the session clock and fires cues on time. Everything runs on [scope]'s (single) thread;
 * the loop sleeps until the next cue instead of polling.
 */
class SessionController(
    private val scope: CoroutineScope,
    private val sink: CueSink,
    private val clock: () -> Long,
    private val onFinished: (Session) -> Unit = {},
) {
    private val _state = MutableStateFlow<Session?>(null)
    val state: StateFlow<Session?> = _state.asStateFlow()

    private var cues: List<Cue> = emptyList()
    private var cursor = 0
    private var loop: Job? = null

    /** Set when the user skips while paused, so the new interval is announced on resume. */
    private var announceOnResume = false

    fun start(workout: Workout, prepSeconds: Int, tenSecondWarning: Boolean, seed: Long = Random.nextLong()) {
        loop?.cancel()
        val timeline = TimelineBuilder.build(workout, prepSeconds, Random(seed))
        cues = CueBuilder.build(timeline, tenSecondWarning)
        cursor = 0
        announceOnResume = false
        _state.value = Session(
            timeline = timeline,
            elapsedAtAnchorMs = 0,
            anchorMs = clock(),
            running = true,
            finished = false,
            segment = 0,
            workedMs = 0,
        )
        if (tick() != null) launchLoop()
    }

    fun togglePause() {
        val session = _state.value ?: return
        if (session.running) pause() else resume()
    }

    fun pause() {
        val session = _state.value ?: return
        if (!session.running || session.finished) return
        loop?.cancel()
        loop = null
        val now = clock()
        _state.value = session.copy(
            elapsedAtAnchorMs = session.elapsedAt(now),
            anchorMs = now,
            running = false,
            workedMs = session.workedMs + (now - session.anchorMs),
        )
    }

    fun resume() {
        val session = _state.value ?: return
        if (session.running || session.finished) return
        _state.value = session.copy(anchorMs = clock(), running = true)
        if (announceOnResume) {
            announceOnResume = false
            announce(session.timeline, session.segment)
        }
        launchLoop()
    }

    /** Skip to the next interval (or finish from the last one). */
    fun next() {
        val session = _state.value ?: return
        if (session.finished) return
        val index = session.timeline.indexAt(session.elapsedAt(clock()))
        if (index >= session.timeline.segments.lastIndex) complete(playFinishCue = true) else jumpTo(index + 1)
    }

    /** Restart the current interval, or go back one if it only just started. */
    fun previous() {
        val session = _state.value ?: return
        if (session.finished) return
        val elapsed = session.elapsedAt(clock())
        val index = session.timeline.indexAt(elapsed)
        val intoSegment = elapsed - session.timeline.segments[index].startMs
        jumpTo(if (intoSegment > RESTART_WINDOW_MS || index == 0) index else index - 1)
    }

    fun jumpTo(index: Int) {
        val session = _state.value ?: return
        if (session.finished) return
        val segments = session.timeline.segments
        val target = segments[index.coerceIn(0, segments.lastIndex)]
        val now = clock()
        _state.value = session.copy(
            elapsedAtAnchorMs = target.startMs,
            anchorMs = now,
            segment = target.index,
            workedMs = if (session.running) session.workedMs + (now - session.anchorMs) else session.workedMs,
        )
        cursor = cues.indexOfFirst { it.atMs > target.startMs }.let { if (it < 0) cues.size else it }
        if (session.running) {
            sink.silence()
            announce(session.timeline, target.index)
            launchLoop()
        } else {
            announceOnResume = true
        }
    }

    /** Abandon the session (or dismiss the finished one). */
    fun stop() {
        loop?.cancel()
        loop = null
        cues = emptyList()
        cursor = 0
        announceOnResume = false
        if (_state.value?.finished != true) sink.silence()
        _state.value = null
    }

    private fun announce(timeline: Timeline, index: Int) {
        val at = timeline.segments[index].startMs
        cues.firstOrNull { it.atMs == at }?.let(sink::play)
    }

    private fun launchLoop() {
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                val elapsed = tick() ?: break
                val session = _state.value ?: break
                val nextAt = cues.getOrNull(cursor)?.atMs ?: session.timeline.totalMs
                delay((nextAt - elapsed).coerceIn(MIN_SLEEP_MS, MAX_SLEEP_MS))
            }
        }
    }

    /** Fires every cue that is due. Returns the elapsed time, or null once the session is over. */
    private fun tick(): Long? {
        val session = _state.value ?: return null
        if (!session.running) return null
        val elapsed = session.elapsedAt(clock())
        while (cursor < cues.size && cues[cursor].atMs <= elapsed) {
            val cue = cues[cursor++]
            if (cue.finish) {
                complete(playFinishCue = elapsed - cue.atMs <= STALE_MS)
                return null
            }
            // After a long stall (should not happen with the wake lock) skip stale cues
            // rather than firing a burst of old bells.
            if (elapsed - cue.atMs <= STALE_MS) sink.play(cue)
        }
        val index = session.timeline.indexAt(elapsed)
        if (index != session.segment) _state.value = session.copy(segment = index)
        if (elapsed >= session.timeline.totalMs) {
            complete(playFinishCue = false)
            return null
        }
        return elapsed
    }

    private fun complete(playFinishCue: Boolean) {
        val session = _state.value ?: return
        if (session.finished) return
        loop?.cancel()
        loop = null
        if (playFinishCue) {
            sink.silence()
            cues.lastOrNull { it.finish }?.let(sink::play)
        }
        cursor = cues.size
        val now = clock()
        val done = session.copy(
            elapsedAtAnchorMs = session.timeline.totalMs,
            anchorMs = now,
            running = false,
            finished = true,
            segment = session.timeline.segments.lastIndex,
            workedMs = if (session.running) session.workedMs + (now - session.anchorMs) else session.workedMs,
        )
        _state.value = done
        onFinished(done)
    }

    private companion object {
        const val RESTART_WINDOW_MS = 3_000L
        const val STALE_MS = 2_500L
        const val MIN_SLEEP_MS = 5L
        const val MAX_SLEEP_MS = 500L
    }
}

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
 * An anchor in the future is a head start: the session is running but the clock waits for it.
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
        val raw = if (running) elapsedAtAnchorMs + (nowMs - anchorMs).coerceAtLeast(0) else elapsedAtAnchorMs
        return raw.coerceIn(0, timeline.totalMs)
    }

    /** What is left of the head start after a start or resume; 0 once the clock moves. */
    fun startsInMs(nowMs: Long): Long = if (running) (anchorMs - nowMs).coerceAtLeast(0) else 0
}

/**
 * Owns the session clock and fires cues on time. Everything runs on [scope]'s (single) thread;
 * the loop sleeps until the next cue instead of polling.
 */
class SessionController(
    private val scope: CoroutineScope,
    private val sink: CueSink,
    private val clock: () -> Long,
    /** Silent delay after start or resume, to get from the phone to the bag. */
    private val headStartMs: Long = HEAD_START_MS,
    private val onFinished: (Session) -> Unit = {},
) {
    private val _state = MutableStateFlow<Session?>(null)
    val state: StateFlow<Session?> = _state.asStateFlow()

    private var cues: List<Cue> = emptyList()
    private var cursor = 0
    private var loop: Job? = null

    /** Set when the user skips while the clock stands, so the new interval is announced once it moves. */
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
            anchorMs = clock() + headStartMs,
            running = true,
            finished = false,
            segment = 0,
            workedMs = 0,
        )
        launchLoop()
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
            workedMs = session.workedMs + (now - session.anchorMs).coerceAtLeast(0),
        )
    }

    fun resume() {
        val session = _state.value ?: return
        if (session.running || session.finished) return
        _state.value = session.copy(anchorMs = clock() + headStartMs, running = true)
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
        val ticking = session.running && now >= session.anchorMs
        _state.value = session.copy(
            elapsedAtAnchorMs = target.startMs,
            // A head start in progress keeps its anchor; otherwise the clock restarts from here.
            anchorMs = if (session.running && !ticking) session.anchorMs else now,
            segment = target.index,
            workedMs = if (ticking) session.workedMs + (now - session.anchorMs) else session.workedMs,
        )
        cursor = cues.indexOfFirst { it.atMs > target.startMs }.let { if (it < 0) cues.size else it }
        if (ticking) {
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
            val headStart = (_state.value ?: return@launch).anchorMs - clock()
            if (headStart > 0) delay(headStart)
            if (announceOnResume) {
                announceOnResume = false
                _state.value?.let { announce(it.timeline, it.segment) }
            }
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
        if (clock() < session.anchorMs) return session.elapsedAtAnchorMs
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
            workedMs = if (session.running) session.workedMs + (now - session.anchorMs).coerceAtLeast(0) else session.workedMs,
        )
        _state.value = done
        onFinished(done)
    }

    companion object {
        const val HEAD_START_MS = 2_000L
        private const val RESTART_WINDOW_MS = 3_000L
        private const val STALE_MS = 2_500L
        private const val MIN_SLEEP_MS = 5L
        private const val MAX_SLEEP_MS = 500L
    }
}

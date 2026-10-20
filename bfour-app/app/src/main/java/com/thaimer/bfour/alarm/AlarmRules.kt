package com.thaimer.bfour.alarm

/**
 * Per-probe alarm decisions, kept free of Android so they can be unit-tested.
 *
 * - "Almost" fires once when the probe gets within [ALMOST_MARGIN_C] of the target.
 * - "Done" fires once when the target is reached and keeps ringing until acknowledged.
 * - Both re-arm when the temperature falls well below the target again, e.g. the
 *   probe went into a new piece of meat.
 */
object AlarmRules {
    const val ALMOST_MARGIN_C = 5.0
    const val REARM_MARGIN_C = 8.0

    data class State(
        val target: Double? = null,
        val almostSent: Boolean = false,
        val done: Boolean = false,
        val acknowledged: Boolean = false,
    ) {
        val ringing: Boolean get() = done && !acknowledged
    }

    enum class Event { ALMOST, DONE }

    data class Result(val state: State, val event: Event?)

    fun evaluate(prev: State, tempC: Double?, targetC: Double?): Result {
        // A new or removed target starts from scratch.
        val state = if (prev.target != targetC) State(target = targetC) else prev
        if (targetC == null || tempC == null) return Result(state, null)

        if (!state.done && tempC >= targetC) {
            return Result(state.copy(done = true, acknowledged = false, almostSent = true), Event.DONE)
        }
        if (!state.done && !state.almostSent && tempC >= targetC - ALMOST_MARGIN_C) {
            return Result(state.copy(almostSent = true), Event.ALMOST)
        }
        if (state.done && state.acknowledged && tempC < targetC - REARM_MARGIN_C) {
            return Result(State(target = targetC), null)
        }
        if (!state.done && state.almostSent && tempC < targetC - REARM_MARGIN_C) {
            return Result(state.copy(almostSent = false), null)
        }
        return Result(state, null)
    }

    fun acknowledge(state: State): State = if (state.done) state.copy(acknowledged = true) else state
}

package com.kazaeza.thaimer.model

/** Sound effects, in the order they win when two land on the same moment. */
enum class Sound { BELL_END, BELL, CLAPPER, BURST, GO, PIP }

/** Vibration patterns, weakest first. */
enum class Buzz { SHORT, DOUBLE, LONG }

/** Something to play at [atMs] on the session clock. */
data class Cue(
    val atMs: Long,
    val sound: Sound? = null,
    val speech: String? = null,
    val buzz: Buzz? = null,
    /** The end-of-workout cue. */
    val finish: Boolean = false,
)

object CueBuilder {
    const val FINISH_SPEECH = "Тренировка окончена. Красавчик!"

    /**
     * Gym-timer conventions: a bell opens a round, a triple bell closes it, a wooden clapper
     * warns 10 seconds before the end, pips count down the last 3 seconds of an interval.
     * Surprise intervals get no countdown, so the burst really does come out of nowhere.
     */
    fun build(timeline: Timeline, tenSecondWarning: Boolean = true): List<Cue> {
        val segments = timeline.segments
        val raw = mutableListOf<Cue>()
        segments.forEachIndexed { i, segment ->
            val next = segments.getOrNull(i + 1)
            raw += startCue(timeline, segment, segments.getOrNull(i - 1))
            if (segment.durationMs >= 6_000 && next?.step?.surprise != true) {
                for (n in 3 downTo 1) raw += Cue(segment.endMs - n * 1000L, sound = Sound.PIP)
            }
            // Heads-up only in proper rests; a short transition would hear the same name twice.
            if (segment.step.kind == Kind.REST && segment.durationMs >= 30_000 && next != null) {
                val upcoming = upcomingName(timeline, next, opensBlock = next.block != segment.block)
                    ?.let { " Дальше — $it." }.orEmpty()
                raw += Cue(segment.endMs - 10_000, speech = "Приготовься.$upcoming")
            }
        }
        if (tenSecondWarning) {
            timeline.blocks
                .filter { it.type == BlockType.ROUND && it.durationMs >= 40_000 }
                .filter { segments[it.segments.last].step.kind != Kind.REST }
                .forEach { raw += Cue(it.endMs - 10_000, sound = Sound.CLAPPER) }
        }
        raw += Cue(timeline.totalMs, Sound.BELL_END, FINISH_SPEECH, Buzz.LONG, finish = true)
        return raw.groupBy { it.atMs }.toSortedMap().map { (at, cues) -> merge(at, cues) }
    }

    private fun startCue(timeline: Timeline, segment: Segment, previous: Segment?): Cue {
        val block = timeline.blockOf(segment)
        val opensBlock = previous == null || previous.block != segment.block
        val closesRound = opensBlock && previous != null && timeline.blockOf(previous).type == BlockType.ROUND
        val opensRound = opensBlock && block.type == BlockType.ROUND
        val sound = when {
            previous == null -> null // the session opens with the voice alone, no beep in the face
            opensRound -> Sound.BELL
            closesRound -> Sound.BELL_END
            segment.step.kind == Kind.BURST -> Sound.BURST
            else -> Sound.GO
        }
        val lead = when {
            previous == null -> timeline.workout.title
            opensRound && isLastRound(timeline, block) -> "Последний раунд!"
            opensRound -> "Раунд ${block.round}"
            else -> ""
        }
        val body = if (opensBlock && block.intro.isNotBlank()) block.intro else segment.step.say
        val speech = listOf(lead, body)
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.sentence() }
            .ifBlank { null }
        val buzz = when {
            segment.step.kind == Kind.BURST -> Buzz.DOUBLE
            opensBlock -> Buzz.LONG
            else -> Buzz.SHORT
        }
        return Cue(segment.startMs, sound, speech, buzz)
    }

    /** "раунд 3, пик" / "тайская ОФП" / "лоу-кики"; null when it must stay a surprise. */
    private fun upcomingName(timeline: Timeline, next: Segment, opensBlock: Boolean): String? {
        if (next.step.surprise) return null
        val block = timeline.blockOf(next)
        val name = (if (opensBlock && block.title.isNotBlank()) block.title else next.step.name).lowercaseFirst()
        if (!opensBlock || block.type != BlockType.ROUND) return name
        val round = if (isLastRound(timeline, block)) "последний раунд" else "раунд ${block.round}"
        return "$round, $name"
    }

    private fun isLastRound(timeline: Timeline, block: BlockSpan) =
        block.type == BlockType.ROUND && block.round == timeline.totalRounds && timeline.totalRounds > 1

    private fun merge(at: Long, cues: List<Cue>) = Cue(
        atMs = at,
        sound = cues.mapNotNull { it.sound }.minOrNull(),
        speech = cues.mapNotNull { it.speech }.distinct().joinToString(" ").ifBlank { null },
        buzz = cues.mapNotNull { it.buzz }.maxOrNull(),
        finish = cues.any { it.finish },
    )
}

private fun String.sentence(): String = trim().let { if (it.last() in ".!?…") it else "$it." }

internal fun String.lowercaseFirst(): String = replaceFirstChar { it.lowercaseChar() }

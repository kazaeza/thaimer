package com.kazaeza.thaimer.model

import kotlin.math.min
import kotlin.random.Random
import kotlin.random.nextInt

/** A concrete interval on the session clock. */
data class Segment(
    val index: Int,
    val startMs: Long,
    val durationMs: Long,
    val step: Step,
    /** Index into [Timeline.blocks]. */
    val block: Int,
) {
    val endMs: Long get() = startMs + durationMs
}

data class BlockSpan(
    val index: Int,
    val title: String,
    val type: BlockType,
    /** 1-based round number for [BlockType.ROUND] blocks, 0 otherwise. */
    val round: Int,
    val startMs: Long,
    val durationMs: Long,
    val segments: IntRange,
    /** Colour of the block on the whole-workout ring. */
    val kind: Kind,
    val intro: String = "",
) {
    val endMs: Long get() = startMs + durationMs
}

/** A workout laid out on a single clock, with sparring rounds already rolled. */
class Timeline(val workout: Workout, val segments: List<Segment>, val blocks: List<BlockSpan>) {
    init {
        require(segments.isNotEmpty()) { "empty workout ${workout.id}" }
    }

    val totalMs: Long = segments.last().endMs
    val totalRounds: Int = blocks.count { it.type == BlockType.ROUND }

    /** The segment running at [elapsedMs]; the last one once the clock has run out. */
    fun indexAt(elapsedMs: Long): Int {
        var lo = 0
        var hi = segments.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (segments[mid].startMs <= elapsedMs) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun blockOf(segment: Segment): BlockSpan = blocks[segment.block]

    /**
     * True when the next interval is an ambush. Then the interval's own end must not be shown
     * anywhere (it would give the burst away), so the clocks count down to the bell instead.
     */
    fun isMasked(segment: Segment): Boolean = segments.getOrNull(segment.index + 1)?.step?.surprise == true

    /** Where the big countdown is heading: this interval's end, or the round's end when masked. */
    fun countdownTarget(segment: Segment): Long = if (isMasked(segment)) blockOf(segment).endMs else segment.endMs

    /** Blocks whose future is kept secret (they contain surprise intervals). */
    val secretBlocks: Set<Int> = segments.filter { it.step.surprise }.map { it.block }.toSet()
}

object TimelineBuilder {
    const val PREP_TITLE = "Приготовься"

    fun build(workout: Workout, prepSeconds: Int, random: Random): Timeline {
        val blocks = buildList {
            if (prepSeconds > 0) add(Block("", BlockType.PREP, listOf(Part.Fixed(prepStep(prepSeconds)))))
            addAll(workout.blocks)
        }
        val segments = mutableListOf<Segment>()
        val spans = mutableListOf<BlockSpan>()
        var clock = 0L
        var round = 0
        blocks.forEachIndexed { blockIndex, block ->
            val first = segments.size
            val start = clock
            for (part in block.parts) {
                val steps = when (part) {
                    is Part.Fixed -> listOf(part.step)
                    is Part.Sparring -> expandSparring(part, random)
                }
                for (step in steps) {
                    val duration = step.seconds * 1000L
                    segments += Segment(segments.size, clock, duration, step, blockIndex)
                    clock += duration
                }
            }
            if (block.type == BlockType.ROUND) round++
            spans += BlockSpan(
                index = blockIndex,
                title = block.title,
                type = block.type,
                round = if (block.type == BlockType.ROUND) round else 0,
                startMs = start,
                durationMs = clock - start,
                segments = first until segments.size,
                kind = representativeKind(block),
                intro = block.intro,
            )
        }
        return Timeline(workout, segments, spans)
    }

    fun prepStep(seconds: Int) = Step(
        name = PREP_TITLE,
        seconds = seconds,
        kind = Kind.PREP,
        hint = "Встань в стойку: подбородок вниз, руки у лица",
        say = PREP_TITLE,
    )

    /** The kind that fills most of the block, so the overview ring reads at a glance. */
    fun representativeKind(block: Block): Kind {
        if (block.parts.any { it is Part.Sparring }) return Kind.FIGHT
        val steps = block.parts.filterIsInstance<Part.Fixed>().map { it.step }
        val working = steps.filter { it.kind != Kind.REST }.ifEmpty { steps }
        return working.groupBy { it.kind }
            .maxByOrNull { (_, s) -> s.sumOf { it.seconds } }
            ?.key ?: Kind.REST
    }
}

/** Room left after a burst + pressure pair, so the round never ends on a sliver of pace. */
private const val MIN_PACE = 8

/** Shortest breather between bursts: anything less flickers past before the voice finishes. */
private const val MIN_GAP = 5

fun paceStep(seconds: Int) = Step(
    name = "Темп спарринга",
    seconds = seconds,
    kind = Kind.FIGHT,
    hint = "Джеб, финты, движение. Работай на 60% — но будь готов",
    say = "Темп",
)

fun pressureStep(seconds: Int) = Step(
    name = "Под давлением",
    seconds = seconds,
    kind = Kind.PRESSURE,
    hint = "Отдыха нет: уходы, защита, тип, джеб. Дыши на ходу",
    say = "Не стой! Работай!",
)

/**
 * Rolls one sparring round: an opening at pace, then bursts at random moments, each followed by
 * pressure work, with pace in between. Always fills exactly [Part.Sparring.seconds].
 */
fun expandSparring(part: Part.Sparring, random: Random): List<Step> {
    val body = part.seconds - (part.finish?.seconds ?: 0)
    val out = mutableListOf<Step>()
    var clock = 0

    fun pace(seconds: Int) {
        if (seconds <= 0) return
        val last = out.lastOrNull()
        if (last != null && last.kind == Kind.FIGHT) {
            out[out.lastIndex] = last.copy(seconds = last.seconds + seconds)
        } else {
            out += paceStep(seconds)
        }
        clock += seconds
    }

    pace(min(random.nextInt(part.gap) + 6, body))
    var previous: Burst? = null
    while (true) {
        val burstSeconds = random.nextInt(part.burstLength)
        val pressureSeconds = random.nextInt(part.pressureLength)
        if (clock + burstSeconds + pressureSeconds + MIN_PACE > body) break
        val burst = part.bursts.filter { it != previous }.random(random)
        out += Step(burst.name, burstSeconds, Kind.BURST, burst.hint, burst.say, surprise = true)
        out += pressureStep(pressureSeconds)
        clock += burstSeconds + pressureSeconds
        previous = burst
        pace(min(random.nextInt(part.gap).coerceAtLeast(MIN_GAP), body - clock))
    }
    pace(body - clock)
    part.finish?.let { out += it }
    return out
}

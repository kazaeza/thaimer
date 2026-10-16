package com.kazaeza.thaimer.model

/** What an interval asks of you. Drives its colour, icon and the sound that announces it. */
enum class Kind {
    PREP, WARMUP, HANDS, LEGS, CLINCH, FIGHT, BURST, PRESSURE, CARDIO, STRENGTH, REST, COOLDOWN
}

enum class BlockType { PREP, WARMUP, ROUND, REST, FINISHER, COOLDOWN }

/** One timed interval. [say] is what the voice announces; empty means stay silent. */
data class Step(
    val name: String,
    val seconds: Int,
    val kind: Kind,
    val hint: String = "",
    val say: String = name,
    /** Hidden from "next up" and not counted down to: you only learn about it when it hits. */
    val surprise: Boolean = false,
)

/** A strike pattern that can be thrown at you during a sparring round. */
data class Burst(val name: String, val say: String, val hint: String)

sealed interface Part {
    val seconds: Int

    data class Fixed(val step: Step) : Part {
        override val seconds: Int get() = step.seconds
    }

    /**
     * A sparring round: steady pace broken by bursts at random moments, each followed by
     * "under pressure" work with no rest. Resolved into concrete steps when a session starts,
     * so the timing is different every time.
     */
    data class Sparring(
        override val seconds: Int,
        val bursts: List<Burst>,
        /** Pace seconds between the end of one burst sequence and the next burst. */
        val gap: IntRange,
        val burstLength: IntRange = 6..12,
        val pressureLength: IntRange = 8..14,
        /** Fixed all-out finish at the end of the round (lines up with the 10-second clapper). */
        val finish: Step? = null,
    ) : Part {
        init {
            require(bursts.size >= 2) { "need at least two burst types to avoid repeats" }
        }
    }
}

data class Block(
    val title: String,
    val type: BlockType,
    val parts: List<Part>,
    /** Spoken when the block opens, instead of the first step's own announcement. */
    val intro: String = "",
) {
    val seconds: Int get() = parts.sumOf { it.seconds }
}

data class Workout(
    val id: String,
    val title: String,
    val tagline: String,
    val description: String,
    val emoji: String,
    /** 1 (easy) … 5 (brutal). */
    val intensity: Int,
    val blocks: List<Block>,
) {
    val seconds: Int get() = blocks.sumOf { it.seconds }
    val rounds: Int get() = blocks.count { it.type == BlockType.ROUND }
    val hasRandomBursts: Boolean get() = blocks.any { b -> b.parts.any { it is Part.Sparring } }
}

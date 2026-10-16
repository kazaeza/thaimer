package com.kazaeza.thaimer.ui.detail

import com.kazaeza.thaimer.model.Block
import com.kazaeza.thaimer.model.BlockType
import com.kazaeza.thaimer.model.Part
import com.kazaeza.thaimer.model.Step
import com.kazaeza.thaimer.model.Workout

/** A workout's plan, folded so repeated circuits and Tabata cycles read as "×N". */
sealed interface PlanRow {
    data class Header(val title: String, val seconds: Int, val times: Int = 1, val restBetween: Int = 0) : PlanRow
    data class Item(val step: Step) : PlanRow
    data class Sparring(val seconds: Int, val burstMin: Int, val burstMax: Int) : PlanRow
    data class Repeat(val times: Int) : PlanRow
}

fun planRows(workout: Workout): List<PlanRow> {
    val rows = mutableListOf<PlanRow>()
    val blocks = workout.blocks
    var round = 0
    var i = 0
    while (i < blocks.size) {
        val block = blocks[i]
        // Identical rounds separated by identical rests fold into one.
        var times = 1
        var restBetween = 0
        if (block.type == BlockType.ROUND) {
            while (i + 2 * times < blocks.size &&
                blocks[i + 2 * times - 1].type == BlockType.REST &&
                blocks[i + 2 * times] == block &&
                (restBetween == 0 || blocks[i + 2 * times - 1].seconds == restBetween)
            ) {
                restBetween = blocks[i + 2 * times - 1].seconds
                times++
            }
        }
        val title = when (block.type) {
            BlockType.ROUND -> {
                val first = round + 1
                round += times
                val label = if (times > 1) "Раунды $first–$round" else "Раунд $first"
                label + if (block.title.isNotBlank()) " · ${block.title}" else ""
            }
            BlockType.REST -> "Отдых"
            else -> block.title
        }
        rows += PlanRow.Header(title, block.seconds, times, restBetween)
        if (block.type != BlockType.REST) rows += blockRows(block)
        i += if (times > 1) 2 * times - 1 else 1
    }
    return rows
}

private fun blockRows(block: Block): List<PlanRow> = buildList {
    val fixed = mutableListOf<Step>()
    fun flush() {
        addAll(folded(fixed))
        fixed.clear()
    }
    for (part in block.parts) when (part) {
        is Part.Fixed -> fixed += part.step
        is Part.Sparring -> {
            flush()
            add(PlanRow.Sparring(part.seconds - (part.finish?.seconds ?: 0), part.burstLength.first, part.burstLength.last))
            part.finish?.let { add(PlanRow.Item(it)) }
        }
    }
    flush()
}

/** Shortest repeating cycle shown once with "×N" (a trailing partial cycle is allowed). */
private fun folded(steps: List<Step>): List<PlanRow> {
    val n = steps.size
    for (period in 1..n / 2) {
        if ((0 until n).all { steps[it] == steps[it % period] }) {
            val times = (n + period - 1) / period
            return steps.take(period).map { PlanRow.Item(it) } + PlanRow.Repeat(times)
        }
    }
    return steps.map { PlanRow.Item(it) }
}

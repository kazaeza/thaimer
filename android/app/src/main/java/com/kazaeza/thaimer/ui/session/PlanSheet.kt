package com.kazaeza.thaimer.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kazaeza.thaimer.model.BlockSpan
import com.kazaeza.thaimer.model.BlockType
import com.kazaeza.thaimer.model.Kind
import com.kazaeza.thaimer.model.Segment
import com.kazaeza.thaimer.model.Timeline
import com.kazaeza.thaimer.ui.components.DurationPill
import com.kazaeza.thaimer.ui.components.KindDot
import com.kazaeza.thaimer.ui.components.SectionLabel
import com.kazaeza.thaimer.ui.shortDuration
import com.kazaeza.thaimer.ui.theme.Thaimer
import com.kazaeza.thaimer.ui.theme.Type
import com.kazaeza.thaimer.ui.theme.color
import com.kazaeza.thaimer.ui.theme.emoji

private sealed interface PlanRow {
    data class Header(val block: BlockSpan, val title: String) : PlanRow
    data class Interval(val segment: Segment) : PlanRow
    data class Secret(val block: BlockSpan, val seconds: Int, val fromIndex: Int) : PlanRow
}

/** The session's full plan; tap an interval to jump to it. Sparring futures stay hidden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(timeline: Timeline, currentIndex: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = Thaimer.palette
    val rows = planRows(timeline, currentIndex)
    val start = rows.indexOfFirst { it is PlanRow.Interval && it.segment.index == currentIndex }.coerceAtLeast(0)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surface,
    ) {
        SectionLabel("План тренировки", Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(
            state = rememberLazyListState((start - 2).coerceAtLeast(0)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(rows) { row ->
                when (row) {
                    is PlanRow.Header -> Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
                    ) {
                        Text(row.title, style = Type.cardTitle, color = p.text, modifier = Modifier.weight(1f))
                        Text(shortDuration((row.block.durationMs / 1000).toInt()), style = Type.cardTitle, color = p.muted)
                    }
                    is PlanRow.Interval -> IntervalRow(
                        kind = row.segment.step.kind,
                        name = row.segment.step.name,
                        seconds = row.segment.step.seconds,
                        current = row.segment.index == currentIndex,
                        done = row.segment.index < currentIndex,
                        onClick = { onJump(row.segment.index) },
                    )
                    is PlanRow.Secret -> IntervalRow(
                        kind = Kind.FIGHT,
                        name = "Взрывы в случайные моменты",
                        emojiOverride = "🎲",
                        seconds = row.seconds,
                        current = false,
                        done = false,
                        onClick = { onJump(row.fromIndex) },
                    )
                }
            }
            item { Spacer(Modifier.padding(bottom = 32.dp)) }
        }
    }
}

private fun planRows(timeline: Timeline, currentIndex: Int): List<PlanRow> = buildList {
    timeline.blocks.forEach { block ->
        val title = when (block.type) {
            BlockType.PREP -> "Старт"
            BlockType.ROUND -> "Раунд ${block.round}" + if (block.title.isNotBlank()) " · ${block.title}" else ""
            BlockType.REST -> "Отдых"
            else -> block.title
        }
        if (block.type != BlockType.REST) add(PlanRow.Header(block, title))
        val segments = block.segments.map { timeline.segments[it] }
        val secret = block.index in timeline.secretBlocks
        val hideFrom = if (!secret) null else segments.firstOrNull { it.index > currentIndex || timeline.isMasked(it) && it.index >= currentIndex }
        segments.forEach { s ->
            if (hideFrom == null || s.index < hideFrom.index) add(PlanRow.Interval(s))
        }
        if (hideFrom != null) {
            add(PlanRow.Secret(block, ((block.endMs - hideFrom.startMs) / 1000).toInt(), hideFrom.index))
        }
    }
}

@Composable
private fun IntervalRow(
    kind: Kind,
    name: String,
    seconds: Int,
    current: Boolean,
    done: Boolean,
    onClick: () -> Unit,
    emojiOverride: String? = null,
) {
    val p = Thaimer.palette
    val c = kind.color(p.dark)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (current) c.copy(alpha = 0.14f) else p.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KindDot(kind, 38.dp, emoji = emojiOverride ?: kind.emoji)
        Text(
            name,
            style = Type.body.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
            color = if (done) p.muted else p.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DurationPill(shortDuration(seconds), if (done) c.copy(alpha = 0.45f) else c)
        Spacer(Modifier.width(2.dp))
    }
}

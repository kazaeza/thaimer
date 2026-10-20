package com.thaimer.bfour.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thaimer.bfour.R
import com.thaimer.bfour.core.Presets
import com.thaimer.bfour.core.ProbeState
import com.thaimer.bfour.core.Target
import com.thaimer.bfour.core.ThermoState
import com.thaimer.bfour.core.probeName

private const val DEFAULT_TARGET_C = 63.0

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TargetSheet(
    probe: ProbeState,
    state: ThermoState,
    onSet: (Target?) -> Unit,
    onDismiss: () -> Unit,
) {
    val unit = state.unit
    val min = unit.wholeDegrees(Presets.MIN_C.toDouble())
    val max = unit.wholeDegrees(Presets.MAX_C.toDouble())
    var value by remember {
        mutableIntStateOf(unit.wholeDegrees(probe.target?.celsius ?: DEFAULT_TARGET_C).coerceIn(min, max))
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val name = probeName(LocalContext.current, probe.number, state.probes.size)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(stringResource(R.string.sheet_title, name), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Presets.groups.forEach { group ->
                Text(
                    group.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.presets.forEach { preset ->
                        SuggestionChip(
                            onClick = {
                                val short = group.title.substringBefore(',')
                                onSet(Target(preset.celsius.toDouble(), "$short · ${preset.label}"))
                            },
                            label = { Text("${preset.label}  ${unit.formatWhole(preset.celsius.toDouble())}") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.sheet_custom),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { value = (value - 1).coerceAtLeast(min) },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.Remove, stringResource(R.string.minus)) }
                Text(
                    "$value${unit.symbol}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { value = (value + 1).coerceAtMost(max) },
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Rounded.Add, stringResource(R.string.plus)) }
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.toInt() },
                valueRange = min.toFloat()..max.toFloat(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (probe.target != null) {
                    TextButton(onClick = { onSet(null) }) { Text(stringResource(R.string.clear_target)) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSet(Target(unit.toCelsius(value.toDouble()), null)) },
                    modifier = Modifier.height(48.dp),
                ) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.fillMaxWidth().height(8.dp))
        }
    }
}

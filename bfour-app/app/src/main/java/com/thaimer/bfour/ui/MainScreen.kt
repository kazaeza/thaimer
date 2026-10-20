package com.thaimer.bfour.ui

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thaimer.bfour.R
import com.thaimer.bfour.core.Link
import com.thaimer.bfour.core.LogView
import com.thaimer.bfour.core.Target
import com.thaimer.bfour.core.TempUnit
import com.thaimer.bfour.core.Thermo
import com.thaimer.bfour.core.ThermoState
import com.thaimer.bfour.core.linkText
import com.thaimer.bfour.core.probeName
import com.thaimer.bfour.service.ThermoService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: ThermoState, log: LogView, onOpenChart: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val now = rememberNow()
    val ringing = state.ringingProbes.isNotEmpty()

    // The alarm screen must not hide behind the target sheet.
    LaunchedEffect(ringing) { if (ringing) editing = null }

    // Every target change can be taken back for a few seconds.
    fun changeTarget(probe: Int, target: Target?) {
        if (state.probes.firstOrNull { it.number == probe }?.target == target) return
        val before = Thermo.setTarget(probe, target)
        val name = probeName(context, probe, state.probes.size)
        val message = if (target == null) {
            context.getString(R.string.target_cleared, name)
        } else {
            context.getString(R.string.target_changed, name, state.unit.formatWhole(target.celsius))
        }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message,
                actionLabel = context.getString(R.string.undo),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) Thermo.restore(before)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Header(state) },
                actions = {
                    IconButton(onClick = onOpenChart) {
                        Icon(Icons.AutoMirrored.Rounded.ShowChart, stringResource(R.string.open_chart))
                    }
                    Menu(state)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusBanner(state)
            if (state.probes.size <= 2) {
                // Two probes share the screen so the numbers stay huge.
                state.probes.forEach { p ->
                    ProbeCard(
                        p, state, log, now,
                        onEditTarget = { editing = p.number },
                        onOpenChart = onOpenChart,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.probes.forEach { p ->
                        ProbeCard(
                            p, state, log, now,
                            onEditTarget = { editing = p.number },
                            onOpenChart = onOpenChart,
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                        )
                    }
                }
            }
        }
    }

    val probe = editing?.let { n -> state.probes.firstOrNull { it.number == n } }
    if (probe != null) {
        TargetSheet(
            probe = probe,
            state = state,
            onSet = { target ->
                editing = null
                changeTarget(probe.number, target)
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Header(state: ThermoState) {
    val cook = LocalCookColors.current
    val dot = when (state.link) {
        Link.CONNECTED -> cook.ok
        Link.SEARCHING, Link.CONNECTING -> cook.wait
        Link.IDLE, Link.BLUETOOTH_OFF -> cook.off
    }
    Column {
        Text(state.model, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                linkText(LocalContext.current, state.link),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Menu(state: ThermoState) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var askForget by remember { mutableStateOf(false) }
    // Menus and dialogs are separate windows and would sit on top of the alarm screen.
    val ringing = state.ringingProbes.isNotEmpty()
    LaunchedEffect(ringing) {
        if (ringing) {
            open = false
            askForget = false
        }
    }
    // One-tap unit switch, the most used setting.
    TextButton(onClick = { Thermo.setUnit(if (state.unit == TempUnit.C) TempUnit.F else TempUnit.C) }) {
        Text(state.unit.symbol, style = MaterialTheme.typography.titleMedium)
    }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, stringResource(R.string.menu))
        }
        DropdownMenu(expanded = open && !ringing, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (state.unit == TempUnit.C) R.string.menu_unit_f else R.string.menu_unit_c))
                },
                onClick = {
                    open = false
                    Thermo.setUnit(if (state.unit == TempUnit.C) TempUnit.F else TempUnit.C)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_forget)) },
                onClick = {
                    open = false
                    askForget = true
                },
            )
            if (state.link != Link.IDLE) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_shutdown)) },
                    onClick = {
                        open = false
                        ThermoService.shutdown(context)
                    },
                )
            }
        }
    }
    if (askForget && !ringing) {
        AlertDialog(
            onDismissRequest = { askForget = false },
            title = { Text(stringResource(R.string.forget_title)) },
            text = { Text(stringResource(R.string.forget_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askForget = false
                    Thermo.forgetDevice()
                }) { Text(stringResource(R.string.action_forget)) }
            },
            dismissButton = {
                TextButton(onClick = { askForget = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun StatusBanner(state: ThermoState) {
    val context = LocalContext.current
    if (state.link == Link.CONNECTED) return
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.link) {
                Link.SEARCHING, Link.CONNECTING -> {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(if (state.lostWhileCooking) R.string.hint_lost else R.string.hint_searching),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Link.BLUETOOTH_OFF -> {
                    Text(
                        stringResource(R.string.link_bt_off),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        try {
                            context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (_: ActivityNotFoundException) {
                        } catch (_: SecurityException) {
                        }
                    }) { Text(stringResource(R.string.action_enable_bt)) }
                }
                Link.IDLE -> {
                    Text(
                        stringResource(R.string.link_idle),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { ThermoService.start(context) }) {
                        Text(stringResource(R.string.action_start))
                    }
                }
                Link.CONNECTED -> Unit
            }
        }
    }
}

package com.shuttermute.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shuttermute.R
import com.shuttermute.ShutterSetting
import com.shuttermute.ShutterUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSheet(
    state: ShutterUiState,
    onDismiss: () -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onManualEndpointChange: (String) -> Unit,
    onPair: () -> Unit,
    onCopyCommand: () -> Unit,
    onOpenWirelessDebugging: (Intent) -> Unit,
    wirelessDebuggingIntent: Intent,
    onStartDiscovery: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { onStartDiscovery() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedCard(colors = CardDefaults.outlinedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.setup_phone_title),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(stringResource(R.string.setup_phone_steps), style = MaterialTheme.typography.bodyMedium)
                    val discovered = state.pairingTargets.firstOrNull()
                    Text(
                        text = if (discovered != null) {
                            stringResource(R.string.setup_found_pairing, "${discovered.host}:${discovered.port}")
                        } else {
                            stringResource(R.string.setup_waiting_pairing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = state.pairingCode,
                        onValueChange = onPairingCodeChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.setup_pairing_code)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.manualEndpoint,
                        onValueChange = onManualEndpointChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.setup_manual_endpoint)) },
                        placeholder = { Text("192.168.0.12:37123") },
                        singleLine = true,
                    )
                    FilledTonalButton(
                        onClick = { onOpenWirelessDebugging(wirelessDebuggingIntent) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.open_wireless_debugging))
                    }
                    Button(
                        onClick = onPair,
                        enabled = !state.setupBusy && state.pairingCode.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.setupBusy) stringResource(R.string.setup_pairing) else stringResource(R.string.setup_pair_cta))
                    }
                }
            }

            OutlinedCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.setup_pc_title),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(stringResource(R.string.setup_pc_body), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = ShutterSetting.adbPutCommand(allowed = true),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    FilledTonalButton(onClick = onCopyCommand, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Text(stringResource(R.string.copy_command), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

package com.shuttermute

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shuttermute.ui.HomeScreen
import com.shuttermute.ui.SetupSheet
import com.shuttermute.ui.ShutterMuteTheme

class SettingsActivity : ComponentActivity() {

    private val viewModel: ShutterMuteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ShutterMuteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val nearbyPermission = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* discovery still best-effort if denied */ }
                    LaunchedEffect(state.showSetup) {
                        if (state.showSetup && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            nearbyPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }
                    }
                    HomeScreen(
                        state = state,
                        onToggle = viewModel::setMuteAllowed,
                        onOpenSetup = viewModel::openSetup,
                        onRefresh = viewModel::refresh,
                        onOpenDeveloperOptions = ::startActivity,
                        developerOptionsIntent = viewModel.developerOptionsIntent(),
                    )
                    if (state.showSetup) {
                        SetupSheet(
                            state = state,
                            onDismiss = viewModel::dismissSetup,
                            onPairingCodeChange = viewModel::onPairingCodeChange,
                            onManualEndpointChange = viewModel::onManualEndpointChange,
                            onPair = viewModel::pairAndApply,
                            onCopyCommand = viewModel::copyAdbCommand,
                            onOpenWirelessDebugging = ::startActivity,
                            wirelessDebuggingIntent = viewModel.wirelessDebuggingIntent(),
                            onStartDiscovery = viewModel::startDiscovery,
                        )
                    }
                    LaunchedEffect(Unit) { viewModel.refresh() }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

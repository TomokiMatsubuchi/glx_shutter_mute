package com.shuttermute

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shuttermute.privilege.AdbClient
import com.shuttermute.privilege.AdbDiscovery
import com.shuttermute.privilege.AdbEndpoint
import com.shuttermute.privilege.PrivilegeEngine
import com.shuttermute.privilege.WriteChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShutterUiState(
    val muteAllowed: Boolean = false,
    val canWrite: Boolean = false,
    val writeChannel: WriteChannel? = null,
    val busy: Boolean = false,
    val setupBusy: Boolean = false,
    val pairingCode: String = "",
    val manualEndpoint: String = "",
    val pairingTargets: List<AdbEndpoint> = emptyList(),
    val connectTargets: List<AdbEndpoint> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val showSetup: Boolean = false,
)

class ShutterMuteViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val discovery = AdbDiscovery(appContext)
    private val _state = MutableStateFlow(ShutterUiState())
    val state: StateFlow<ShutterUiState> = _state.asStateFlow()
    private var discoveryJob: Job? = null

    init {
        AdbClient.ensureIdentity(appContext)
        refresh()
        viewModelScope.launch {
            discovery.pairing.collect { endpoints ->
                _state.update { it.copy(pairingTargets = endpoints) }
            }
        }
        viewModelScope.launch {
            discovery.connect.collect { endpoints ->
                _state.update { it.copy(connectTargets = endpoints) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val muteAllowed = ShutterSetting.isMuteAllowed(appContext)
            val status = PrivilegeEngine.probe(appContext, _state.value.connectTargets)
            _state.update {
                it.copy(
                    muteAllowed = muteAllowed,
                    canWrite = status.canWrite,
                    writeChannel = status.preferred,
                    busy = false,
                    showSetup = it.showSetup,
                )
            }
        }
    }

    fun setMuteAllowed(allowed: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            val channel = PrivilegeEngine.write(
                appContext,
                if (allowed) ShutterSetting.VALUE_ALLOW_MUTE else ShutterSetting.VALUE_FORCED_ON,
                _state.value.connectTargets,
            )
            val muteAllowed = ShutterSetting.isMuteAllowed(appContext)
            _state.update {
                it.copy(
                    muteAllowed = muteAllowed,
                    canWrite = channel != null,
                    writeChannel = channel ?: it.writeChannel,
                    busy = false,
                    error = if (channel == null) appContext.getString(R.string.error_write_failed) else null,
                    message = if (channel != null) appContext.getString(R.string.message_updated) else null,
                    showSetup = channel == null,
                )
            }
        }
    }

    fun onPairingCodeChange(value: String) {
        _state.update { it.copy(pairingCode = value.filter(Char::isDigit).take(6)) }
    }

    fun onManualEndpointChange(value: String) {
        _state.update { it.copy(manualEndpoint = value.trim()) }
    }

    fun openSetup() {
        _state.update { it.copy(showSetup = true, error = null) }
        startDiscovery()
    }

    fun dismissSetup() {
        _state.update { it.copy(showSetup = false) }
        stopDiscovery()
    }

    fun startDiscovery() {
        discovery.start()
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            delay(400)
        }
    }

    fun pairAndApply() {
        viewModelScope.launch {
            val pairingCode = _state.value.pairingCode
            if (pairingCode.length != 6) {
                _state.update { it.copy(error = appContext.getString(R.string.error_pairing_code)) }
                return@launch
            }
            val target = resolvePairingTarget()
            if (target == null) {
                _state.update { it.copy(error = appContext.getString(R.string.error_pairing_port)) }
                return@launch
            }
            _state.update { it.copy(setupBusy = true, error = null, message = null) }
            val pairResult = runCatching {
                AdbClient.ensureIdentity(appContext)
                AdbClient.pair(appContext, target.host, target.port, pairingCode)
            }
            if (pairResult.isFailure) {
                _state.update {
                    it.copy(
                        setupBusy = false,
                        error = appContext.getString(R.string.error_pair_failed),
                    )
                }
                return@launch
            }
            delay(800)
            val channel = PrivilegeEngine.write(
                appContext,
                ShutterSetting.VALUE_ALLOW_MUTE,
                _state.value.connectTargets,
            )
            _state.update {
                it.copy(
                    setupBusy = false,
                    muteAllowed = ShutterSetting.isMuteAllowed(appContext),
                    canWrite = channel != null,
                    writeChannel = channel,
                    showSetup = channel == null,
                    message = if (channel != null) appContext.getString(R.string.message_paired) else null,
                    error = if (channel == null) appContext.getString(R.string.error_write_after_pair) else null,
                )
            }
        }
    }

    fun copyAdbCommand() {
        val command = ShutterSetting.adbPutCommand(allowed = true)
        val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("adb", command))
        _state.update { it.copy(message = appContext.getString(R.string.message_copied)) }
    }

    fun developerOptionsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun wirelessDebuggingIntent(): Intent {
        val wireless = Intent("android.settings.ADB_WIRELESS_SETTINGS")
        return if (wireless.resolveActivity(appContext.packageManager) != null) {
            wireless.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            developerOptionsIntent()
        }
    }

    private fun resolvePairingTarget(): AdbEndpoint? {
        val discovered = _state.value.pairingTargets.firstOrNull()
        if (discovered != null) return discovered
        val raw = _state.value.manualEndpoint
        if (raw.isBlank()) return null
        val normalized = raw.replace("：", ":").trim()
        val parts = normalized.split(":")
        if (parts.size != 2) return null
        val port = parts[1].toIntOrNull() ?: return null
        return AdbEndpoint(host = parts[0].ifBlank { "127.0.0.1" }, port = port, pairing = true)
    }

    private fun stopDiscovery() {
        discovery.stop()
    }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}

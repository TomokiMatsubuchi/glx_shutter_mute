package com.shuttermute.privilege

import android.content.Context
import com.shuttermute.ShutterSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdbClient {

    private const val PREFS = "adb_session"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_PAIRED = "paired"

    fun ensureIdentity(context: Context) {
        LocalAdbManager.get(context)
    }

    fun hasPaired(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PAIRED, false)

    fun lastEndpoint(context: Context): AdbEndpoint? {
        val stored = prefs(context)
        val host = stored.getString(KEY_HOST, null) ?: return null
        val port = stored.getInt(KEY_PORT, -1)
        if (port <= 0) return null
        return AdbEndpoint(host, port, pairing = false)
    }

    suspend fun pair(context: Context, host: String, port: Int, pairingCode: String) {
        withContext(Dispatchers.IO) {
            val manager = LocalAdbManager.get(context)
            val ok = manager.pair(host, port, pairingCode.trim())
            check(ok) { "pairing rejected" }
        }
    }

    suspend fun write(context: Context, value: Int, endpoints: List<AdbEndpoint>): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val manager = LocalAdbManager.get(context)
                val connected = connect(context, manager, endpoints) ?: return@runCatching false
                val allowed = value == ShutterSetting.VALUE_ALLOW_MUTE
                val command = buildString {
                    append(ShutterSetting.settingsPutCommand(allowed))
                    append("; settings put global ${ShutterSetting.KEY} $value")
                    append("; settings get system ${ShutterSetting.KEY}")
                }
                val output = shell(manager, command)
                val ok = output.contains(value.toString())
                if (ok) remember(context, connected)
                ok
            }.getOrDefault(false)
        }
    }

    suspend fun probe(context: Context, endpoints: List<AdbEndpoint>): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                val manager = LocalAdbManager.get(context)
                val connected = connect(context, manager, endpoints)
                if (connected != null) remember(context, connected)
                connected != null
            }.getOrDefault(false)
        }
    }

    private fun connect(
        context: Context,
        manager: LocalAdbManager,
        live: List<AdbEndpoint>,
    ): AdbEndpoint? {
        if (runCatching { manager.autoConnect(context, 3_000) }.getOrDefault(false)) {
            return lastEndpoint(context) ?: live.firstOrNull { !it.pairing } ?: AdbEndpoint("127.0.0.1", 5555, false)
        }
        val candidates = candidateHosts(context, live)
        for (endpoint in candidates) {
            val ok = runCatching { manager.connect(endpoint.host, endpoint.port) }.getOrDefault(false)
            if (ok) return endpoint
        }
        return null
    }

    private fun shell(manager: LocalAdbManager, command: String): String {
        val stream = manager.openStream("shell:$command")
        stream.openInputStream().use { input ->
            return input.bufferedReader().readText()
        }
    }

    private fun candidateHosts(context: Context, live: List<AdbEndpoint>): List<AdbEndpoint> {
        val remembered = lastEndpoint(context)
        val fromDiscovery = live.filter { !it.pairing }.flatMap { endpoint ->
            listOf(endpoint) + loopbackVariants(endpoint)
        }
        val extras = remembered?.let { loopbackVariants(it) + it }.orEmpty()
        return (fromDiscovery + extras).distinctBy { "${it.host}:${it.port}" }
    }

    private fun loopbackVariants(endpoint: AdbEndpoint): List<AdbEndpoint> =
        listOf("127.0.0.1", "::1").map { host ->
            endpoint.copy(host = host, pairing = false)
        }

    private fun remember(context: Context, endpoint: AdbEndpoint) {
        prefs(context).edit()
            .putString(KEY_HOST, endpoint.host)
            .putInt(KEY_PORT, endpoint.port)
            .putBoolean(KEY_PAIRED, true)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

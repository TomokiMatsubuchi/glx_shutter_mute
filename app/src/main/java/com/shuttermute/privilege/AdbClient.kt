package com.shuttermute.privilege

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbCertPolicy
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import com.shuttermute.ShutterSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import java.io.File

object AdbClient {

    private const val PREFS = "adb_session"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_PAIRED = "paired"

    @Volatile
    private var configured = false

    fun ensureIdentity(context: Context) {
        if (configured) return
        synchronized(this) {
            if (configured) return
            val keyFile = File(context.applicationContext.filesDir, "adb/adbkey.pem")
            keyFile.parentFile?.mkdirs()
            KadbCert.configure(
                store = OkioFilePrivateKeyStore(keyFile.absolutePath.toPath()),
                policy = KadbCertPolicy(),
            )
            KadbCert.ensureReady()
            configured = true
        }
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

    suspend fun pair(host: String, port: Int, pairingCode: String) {
        withContext(Dispatchers.IO) {
            Kadb.pair(host, port, pairingCode.trim())
        }
    }

    suspend fun write(context: Context, value: Int, endpoints: List<AdbEndpoint>): Boolean {
        ensureIdentity(context)
        val candidates = candidateHosts(context, endpoints)
        for (endpoint in candidates) {
            val ok = runCatching { writeOn(endpoint, value) }.getOrDefault(false)
            if (ok) {
                remember(context, endpoint)
                return true
            }
        }
        return false
    }

    suspend fun probe(context: Context, endpoints: List<AdbEndpoint>): Boolean {
        ensureIdentity(context)
        val candidates = candidateHosts(context, endpoints)
        for (endpoint in candidates) {
            val ok = runCatching {
                Kadb.create(endpoint.host, endpoint.port, connectTimeout = 2_000, socketTimeout = 3_000).use { kadb ->
                    kadb.shell("echo ok").allOutput.contains("ok")
                }
            }.getOrDefault(false)
            if (ok) {
                remember(context, endpoint)
                return true
            }
        }
        return false
    }

    private fun writeOn(endpoint: AdbEndpoint, value: Int): Boolean {
        Kadb.create(endpoint.host, endpoint.port, connectTimeout = 3_000, socketTimeout = 5_000).use { kadb ->
            val command = buildString {
                append(ShutterSetting.settingsPutCommand(value == ShutterSetting.VALUE_ALLOW_MUTE))
                append("; settings put global ${ShutterSetting.KEY} $value")
                append("; settings get system ${ShutterSetting.KEY}")
            }
            val response = kadb.shell(command)
            val output = response.allOutput
            return output.contains(value.toString())
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

package com.shuttermute.privilege

import android.content.Context
import com.shuttermute.ShutterSetting

object PrivilegeEngine {

    data class Status(
        val canWrite: Boolean,
        val preferred: WriteChannel?,
        val rootAvailable: Boolean,
        val adbReady: Boolean,
    )

    suspend fun probe(context: Context, adbEndpoints: List<AdbEndpoint> = emptyList()): Status {
        val current = ShutterSetting.readRaw(context)
        val direct = current != null && DirectSettingsWriter.write(context, current)
        val root = RootShell.isAvailable()
        val adb = AdbClient.hasPaired(context) && AdbClient.probe(context, adbEndpoints)
        val preferred = when {
            direct -> WriteChannel.DIRECT
            root -> WriteChannel.ROOT
            adb -> WriteChannel.ADB
            else -> null
        }
        return Status(
            canWrite = preferred != null,
            preferred = preferred,
            rootAvailable = root,
            adbReady = adb,
        )
    }

    suspend fun write(
        context: Context,
        value: Int,
        adbEndpoints: List<AdbEndpoint> = emptyList(),
    ): WriteChannel? {
        if (DirectSettingsWriter.write(context, value)) return WriteChannel.DIRECT
        if (writeViaRoot(value) && ShutterSetting.readRaw(context) == value) return WriteChannel.ROOT
        if (AdbClient.write(context, value, adbEndpoints) && ShutterSetting.readRaw(context) == value) {
            return WriteChannel.ADB
        }
        return if (ShutterSetting.readRaw(context) == value) WriteChannel.DIRECT else null
    }

    private fun writeViaRoot(value: Int): Boolean {
        if (!RootShell.isAvailable()) return false
        val key = ShutterSetting.KEY
        val command = "settings put system $key $value; settings put global $key $value"
        return RootShell.exec(command) != null
    }
}

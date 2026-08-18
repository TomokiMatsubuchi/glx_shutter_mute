package com.shuttermute.privilege

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.shuttermute.ShutterSetting

internal object DirectSettingsWriter {

    fun write(context: Context, value: Int): Boolean {
        val resolver = context.contentResolver
        var accepted = false
        for (key in listOf(ShutterSetting.KEY, ShutterSetting.LEGACY_KEY)) {
            accepted = put(resolver, key, value) || accepted
        }
        return accepted && ShutterSetting.readRaw(context) == value
    }

    private fun put(resolver: ContentResolver, key: String, value: Int): Boolean {
        val systemOk = runCatching { Settings.System.putInt(resolver, key, value) }.getOrDefault(false)
        val globalOk = runCatching { Settings.Global.putInt(resolver, key, value) }.getOrDefault(false)
        return systemOk || globalOk
    }
}

package com.shuttermute

import android.content.Context
import android.provider.Settings
import com.shuttermute.privilege.PrivilegeEngine
import com.shuttermute.privilege.WriteChannel

/**
 * Galaxy CSC flag that forces the camera shutter sound.
 *
 * The real key lives in Settings.System (not Global):
 *   csc_pref_camera_forced_shuttersound_key
 *   1 = always play shutter sound
 *   0 = follow silent / vibrate mode
 *
 * Reading works for a normal app. Writing needs a privileged channel:
 * Settings API (older One UI), root, or an in-app wireless ADB session.
 * Shizuku is not required.
 */
object ShutterSetting {

    const val KEY = "csc_pref_camera_forced_shuttersound_key"
    const val LEGACY_KEY = "csc_pref_camera_forced_shutter_sound_key"

    const val VALUE_FORCED_ON = 1
    const val VALUE_ALLOW_MUTE = 0

    private val KEYS = listOf(KEY, LEGACY_KEY)

    fun isMuteAllowed(context: Context): Boolean =
        readRaw(context) == VALUE_ALLOW_MUTE

    fun readRaw(context: Context): Int? {
        val resolver = context.contentResolver
        for (key in KEYS) {
            val system = Settings.System.getInt(resolver, key, Int.MIN_VALUE)
            if (system != Int.MIN_VALUE) return system
            val global = Settings.Global.getInt(resolver, key, Int.MIN_VALUE)
            if (global != Int.MIN_VALUE) return global
        }
        return null
    }

    suspend fun setMuteAllowed(context: Context, allowed: Boolean): WriteChannel? =
        PrivilegeEngine.write(context, if (allowed) VALUE_ALLOW_MUTE else VALUE_FORCED_ON)

    suspend fun toggle(context: Context): WriteChannel? =
        setMuteAllowed(context, !isMuteAllowed(context))

    fun settingsPutCommand(allowed: Boolean): String {
        val value = if (allowed) VALUE_ALLOW_MUTE else VALUE_FORCED_ON
        return "settings put system $KEY $value"
    }

    fun adbPutCommand(allowed: Boolean): String =
        "adb shell ${settingsPutCommand(allowed)}"
}

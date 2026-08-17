package com.shuttermute

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Controls the "forced shutter sound" Global setting:
 *   csc_pref_camera_forced_shutter_sound_key
 *   value = 1  -> shutter sound is always forced on (carrier / CSC default)
 *   value = 0  -> shutter sound respects the mute / vibrate mode
 *
 * Writes use the Settings.Global API, which requires the WRITE_SECURE_SETTINGS
 * permission. That permission cannot be self-granted by a normal app; it is
 * granted once via adb and then persists, so the app works standalone after
 * that one-time setup.
 */
object ShutterSetting {

    private const val KEY = "csc_pref_camera_forced_shutter_sound_key"
    private const val VALUE_FORCED_ON = 1
    private const val VALUE_ALLOW_MUTE = 0

    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    /** True when the app holds the permission needed to write the setting. */
    fun hasPermission(context: Context): Boolean =
        context.checkCallingOrSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun readRaw(context: Context): Int? {
        val v = Settings.Global.getInt(context.contentResolver, KEY, Int.MIN_VALUE)
        return if (v == Int.MIN_VALUE) null else v
    }

    private fun writeRaw(context: Context, value: Int): Boolean = try {
        Settings.Global.putInt(context.contentResolver, KEY, value)
    } catch (e: SecurityException) { false }

    /** True when the shutter sound is currently allowed to be muted in silent mode. */
    fun isMuteAllowed(context: Context): Boolean = readRaw(context) == VALUE_ALLOW_MUTE

    /** Sets whether the shutter sound should be muted in silent mode. */
    fun setMuteAllowed(context: Context, allowed: Boolean): Boolean =
        writeRaw(context, if (allowed) VALUE_ALLOW_MUTE else VALUE_FORCED_ON)

    fun toggle(context: Context): Boolean = setMuteAllowed(context, !isMuteAllowed(context))
}

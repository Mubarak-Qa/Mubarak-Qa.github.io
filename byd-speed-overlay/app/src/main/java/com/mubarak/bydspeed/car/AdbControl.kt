package com.mubarak.bydspeed.car

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Keeps wireless ADB enabled without a PC, the way OneHub does, but only after the user grants
 * WRITE_SECURE_SETTINGS once (a privileged permission a sideloaded app cannot self-grant):
 *
 *   adb shell pm grant com.mubarak.bydspeed android.permission.WRITE_SECURE_SETTINGS
 *
 * With it held, the app can turn on `adb_enabled` so Shizuku's wireless start keeps working
 * across reboots. It can't restart adbd or change the TCP port (that needs shell), so starting
 * the Shizuku service itself still happens through Shizuku after a cold boot.
 */
object AdbControl {
    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    const val GRANT_COMMAND = "adb shell pm grant com.mubarak.bydspeed android.permission.WRITE_SECURE_SETTINGS"

    fun hasSecureWrite(ctx: Context) =
        ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun isAdbEnabled(ctx: Context) =
        runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)

    /** Returns null on success, or an error message. */
    fun enableAdb(ctx: Context): String? = try {
        Settings.Global.putInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 1)
        if (isAdbEnabled(ctx)) null else "no-effect"
    } catch (t: Throwable) { t.javaClass.simpleName + ": " + t.message }
}

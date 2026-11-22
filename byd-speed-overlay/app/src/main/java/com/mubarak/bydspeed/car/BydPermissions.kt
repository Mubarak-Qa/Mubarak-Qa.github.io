package com.mubarak.bydspeed.car

/** Generated from the Leopard 8 scan (DiLink 5.1). */
val BYD_PERMISSIONS = listOf(
    "android.permission.BYDAUTO_AC_GET",
    "android.permission.BYDAUTO_AC_SET",
    "android.permission.BYDAUTO_ADAS_GET",
    "android.permission.BYDAUTO_ADAS_SET",
    "android.permission.BYDAUTO_AUDIO_GET",
    "android.permission.BYDAUTO_AUDIO_SET",
    "android.permission.BYDAUTO_AUX_GET",
    "android.permission.BYDAUTO_AUX_SET",
    "android.permission.BYDAUTO_BIGDATA_GET",
    "android.permission.BYDAUTO_CHARGING_GET",
    "android.permission.BYDAUTO_CHARGING_SET",
    "android.permission.BYDAUTO_COLLISION_GET",
    "android.permission.BYDAUTO_COLLISION_SET",
    "android.permission.BYDAUTO_CPUTEMPRATURE_SET",
    "android.permission.BYDAUTO_DOOR_LOCK_GET",
    "android.permission.BYDAUTO_DOOR_LOCK_SET",
    "android.permission.BYDAUTO_DTC_GET",
    "android.permission.BYDAUTO_DTC_SET",
    "android.permission.BYDAUTO_ENERGY_GET",
    "android.permission.BYDAUTO_ENERGY_SET",
    "android.permission.BYDAUTO_ENGINE_GET",
    "android.permission.BYDAUTO_ENGINE_SET",
    "android.permission.BYDAUTO_FUNCNOTICE_GET",
    "android.permission.BYDAUTO_FUNCNOTICE_SET",
    "android.permission.BYDAUTO_GB_GET",
    "android.permission.BYDAUTO_GB_SET",
    "android.permission.BYDAUTO_GEARBOX_GET",
    "android.permission.BYDAUTO_GEARBOX_SET",
    "android.permission.BYDAUTO_INSTRUMENT_GET",
    "android.permission.BYDAUTO_INSTRUMENT_SET",
    "android.permission.BYDAUTO_LIGHT_GET",
    "android.permission.BYDAUTO_LIGHT_SET",
    "android.permission.BYDAUTO_LOCATION_GET",
    "android.permission.BYDAUTO_LOCATION_SET",
    "android.permission.BYDAUTO_MOTOR_GET",
    "android.permission.BYDAUTO_MOTOR_SET",
    "android.permission.BYDAUTO_MQTT_GET",
    "android.permission.BYDAUTO_MQTT_SET",
    "android.permission.BYDAUTO_OTA_GET",
    "android.permission.BYDAUTO_OTA_SET",
    "android.permission.BYDAUTO_PANORAMA_GET",
    "android.permission.BYDAUTO_PANORAMA_SET",
    "android.permission.BYDAUTO_PHONE_GET",
    "android.permission.BYDAUTO_PHONE_SET",
    "android.permission.BYDAUTO_PM2P5_GET",
    "android.permission.BYDAUTO_PM2P5_SET",
    "android.permission.BYDAUTO_POWER_GET",
    "android.permission.BYDAUTO_POWER_SET",
    "android.permission.BYDAUTO_QCFS_GET",
    "android.permission.BYDAUTO_QCFS_SET",
    "android.permission.BYDAUTO_RADAR_GET",
    "android.permission.BYDAUTO_RADAR_SET",
    "android.permission.BYDAUTO_RADIO_GET",
    "android.permission.BYDAUTO_RADIO_SET",
    "android.permission.BYDAUTO_REAR_VIEW_MIRROR_GET",
    "android.permission.BYDAUTO_REAR_VIEW_MIRROR_SET",
    "android.permission.BYDAUTO_REMINDER_GET",
    "android.permission.BYDAUTO_REMINDER_SET",
    "android.permission.BYDAUTO_RESCUE_GET",
    "android.permission.BYDAUTO_RESCUE_SET",
    "android.permission.BYDAUTO_RSE_GET",
    "android.permission.BYDAUTO_RSE_SET",
    "android.permission.BYDAUTO_SAFETY_BELT_GET",
    "android.permission.BYDAUTO_SAFETY_BELT_SET",
    "android.permission.BYDAUTO_SECURITY_GET",
    "android.permission.BYDAUTO_SECURITY_SET",
    "android.permission.BYDAUTO_SENSOR_GET",
    "android.permission.BYDAUTO_SENSOR_SET",
    "android.permission.BYDAUTO_SETTING_GET",
    "android.permission.BYDAUTO_SETTING_SET",
    "android.permission.BYDAUTO_SIGNAL_SET",
    "android.permission.BYDAUTO_SPECIAL_GET",
    "android.permission.BYDAUTO_SPECIAL_SET",
    "android.permission.BYDAUTO_STATISTIC_GET",
    "android.permission.BYDAUTO_STATISTIC_SET",
    "android.permission.BYDAUTO_TEST_GET",
    "android.permission.BYDAUTO_TEST_SET",
    "android.permission.BYDAUTO_TIME_GET",
    "android.permission.BYDAUTO_TIME_SET",
    "android.permission.BYDAUTO_TYRE_GET",
    "android.permission.BYDAUTO_TYRE_SET",
    "android.permission.BYDAUTO_VEHICLE_DATA_GET",
    "android.permission.BYDAUTO_VEHICLE_DATA_SET",
    "android.permission.BYDAUTO_VERSION_GET",
    "android.permission.BYDAUTO_VERSION_SET",
    "android.permission.BYDAUTO_WIPER_GET",
    "android.permission.BYDAUTO_WIPER_SET",
    "android.permission.BYDAUTO_YUN_GET",
)

object CarPermissions {
    data class Status(val name: String, val defined: Boolean, val protection: String, val granted: Boolean) {
        /** Android lets the user grant this one at runtime. */
        val requestable get() = defined && protection.startsWith("dangerous")
    }

    fun status(context: android.content.Context): List<Status> {
        val pm = context.packageManager
        return BYD_PERMISSIONS.map { name ->
            val granted = context.checkSelfPermission(name) == android.content.pm.PackageManager.PERMISSION_GRANTED
            try {
                val info = pm.getPermissionInfo(name, 0)
                Status(name, true, protectionLabel(info), granted)
            } catch (e: Exception) {
                Status(name, false, "undefined", granted)
            }
        }
    }

    private fun protectionLabel(info: android.content.pm.PermissionInfo): String {
        @Suppress("DEPRECATION")
        val level = info.protectionLevel
        val base = when (level and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE) {
            android.content.pm.PermissionInfo.PROTECTION_NORMAL -> "normal"
            android.content.pm.PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
            android.content.pm.PermissionInfo.PROTECTION_SIGNATURE -> "signature"
            else -> "internal"
        }
        val flags = mutableListOf<String>()
        val f = level and android.content.pm.PermissionInfo.PROTECTION_MASK_FLAGS
        if (f and android.content.pm.PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0) flags += "privileged"
        if (f and android.content.pm.PermissionInfo.PROTECTION_FLAG_DEVELOPMENT != 0) flags += "development"
        if (f and android.content.pm.PermissionInfo.PROTECTION_FLAG_APPOP != 0) flags += "appop"
        if (f and android.content.pm.PermissionInfo.PROTECTION_FLAG_PRE23 != 0) flags += "pre23"
        if (f and android.content.pm.PermissionInfo.PROTECTION_FLAG_INSTALLER != 0) flags += "installer"
        if (f and 0x8000 != 0) flags += "vendor"
        return (listOf(base) + flags).joinToString("|")
    }

    fun summary(list: List<Status>): String = buildString {
        val granted = list.count { it.granted }
        append("Permissions: ${granted}/${list.size} granted\n")
        list.groupBy { it.protection }.forEach { (prot, items) ->
            append("  [$prot] ${items.size}: ")
            append(items.joinToString(" ") { (if (it.granted) "✔" else "✘") + it.name.removePrefix("android.permission.BYDAUTO_") })
            append('\n')
        }
    }
}

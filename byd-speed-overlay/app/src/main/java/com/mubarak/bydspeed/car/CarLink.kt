package com.mubarak.bydspeed.car

/** Chooses the active car channel: embedded ADB first, then Shizuku, else nothing. */
object CarLink {
    val connected get() = AdbCar.connected || ShizukuCar.connected

    val mode: String get() = when {
        AdbCar.connected -> "adb"
        ShizukuCar.connected -> "shizuku"
        else -> "local"
    }

    fun get(cls: String?, methods: List<String>, args: List<Int>): Double? = when {
        AdbCar.connected -> AdbCar.get(cls, methods, args)
        ShizukuCar.connected -> ShizukuCar.get(cls, methods, args)
        else -> null
    }

    fun set(cls: String?, method: String, args: List<Double>): String? = when {
        AdbCar.connected -> AdbCar.set(cls, method, args)
        ShizukuCar.connected -> ShizukuCar.set(cls, method, args)
        else -> "offline"
    }

    fun scan(): String? = when {
        AdbCar.connected -> AdbCar.scan()
        ShizukuCar.connected -> ShizukuCar.scan()
        else -> null
    }
}

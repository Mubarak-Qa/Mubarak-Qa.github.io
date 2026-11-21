package com.mubarak.bydspeed

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var speedLimit: Int
        get() = sp.getInt("limit", 120)
        set(v) = sp.edit().putInt("limit", v).apply()

    var sizeSp: Int
        get() = sp.getInt("size", 56)
        set(v) = sp.edit().putInt("size", v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("autostart", false)
        set(v) = sp.edit().putBoolean("autostart", v).apply()

    var overlayX: Int
        get() = sp.getInt("x", 40)
        set(v) = sp.edit().putInt("x", v).apply()

    var overlayY: Int
        get() = sp.getInt("y", 200)
        set(v) = sp.edit().putInt("y", v).apply()
}

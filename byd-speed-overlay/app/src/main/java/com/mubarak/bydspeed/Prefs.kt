package com.mubarak.bydspeed

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int) = sp.getInt(key, def)
    private fun str(key: String, def: String) = sp.getString(key, def) ?: def

    var speedLimit: Int
        get() = int("limit", 120)
        set(v) = sp.edit().putInt("limit", v).apply()

    var sizeSp: Int
        get() = int("size", 56)
        set(v) = sp.edit().putInt("size", v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("autostart", false)
        set(v) = sp.edit().putBoolean("autostart", v).apply()

    var overlayX: Int
        get() = int("x", 40)
        set(v) = sp.edit().putInt("x", v).apply()

    var overlayY: Int
        get() = int("y", 200)
        set(v) = sp.edit().putInt("y", v).apply()

    /** "system" | "light" | "dark" */
    var theme: String
        get() = str("theme", "system")
        set(v) = sp.edit().putString("theme", v).apply()

    var radioCountry: String
        get() = str("radio_country", "QA")
        set(v) = sp.edit().putString("radio_country", v).apply()

    /** Favorite stations as a JSON array string. */
    var radioFavorites: String
        get() = str("radio_favs", "[]")
        set(v) = sp.edit().putString("radio_favs", v).apply()

    /** User-editable JSON overriding how car features map to BYD framework methods. */
    var carMapping: String
        get() = str("car_mapping", "")
        set(v) = sp.edit().putString("car_mapping", v).apply()

    var splitLeft: String
        get() = str("split_left", "")
        set(v) = sp.edit().putString("split_left", v).apply()

    var splitRight: String
        get() = str("split_right", "")
        set(v) = sp.edit().putString("split_right", v).apply()

    /** Saved split pairs, "pkgA|pkgB" separated by ';'. */
    var splitPresets: String
        get() = str("split_presets", "")
        set(v) = sp.edit().putString("split_presets", v).apply()
}

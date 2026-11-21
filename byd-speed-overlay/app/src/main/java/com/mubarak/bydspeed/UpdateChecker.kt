package com.mubarak.bydspeed

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads version.json published with the app, e.g.
 * {"versionCode":3,"versionName":"0.3.0","apkUrl":"https://…/app.apk","notes":"…"}
 */
object UpdateChecker {
    data class Release(val code: Int, val name: String, val apkUrl: String, val notes: String)

    /** Blocking; call off the main thread. */
    fun latest(): Release {
        val conn = (URL(BuildConfig.UPDATE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 8000; useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        return Release(o.getInt("versionCode"), o.optString("versionName"), o.getString("apkUrl"), o.optString("notes"))
    }
}

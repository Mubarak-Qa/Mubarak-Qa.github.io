package com.mubarak.bydspeed.radio

import android.content.Context
import com.mubarak.bydspeed.Prefs
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gulf radio stations from the open radio-browser.info directory (community maintained),
 * cached on disk so the list still shows without internet.
 */
class StationRepository(private val context: Context) {

    private val hosts = listOf("all.api.radio-browser.info", "de1.api.radio-browser.info", "nl1.api.radio-browser.info")

    private fun cacheFile(cc: String) = File(context.cacheDir, "stations_$cc.json")

    fun cached(cc: String): List<Station> =
        runCatching { parse(cacheFile(cc).readText()) }.getOrDefault(emptyList())

    /** Blocking network fetch; call off the main thread. */
    fun fetch(cc: String): List<Station> {
        var last: Exception? = null
        for (h in hosts) {
            try {
                val url = URL("https://$h/json/stations/bycountrycodeexact/$cc?hidebroken=true&order=clickcount&reverse=true&limit=300")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000; readTimeout = 12000
                    setRequestProperty("User-Agent", "CarHub/1.0")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val list = parse(body)
                cacheFile(cc).writeText(body)
                return list
            } catch (e: Exception) { last = e }
        }
        throw last ?: IllegalStateException("no host")
    }

    private fun parse(json: String): List<Station> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { Station.fromJson(arr.getJSONObject(it)) }
            .filter { it.url.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.name.lowercase() to it.url }
    }

    fun favorites(): List<Station> =
        runCatching { parse(Prefs(context).radioFavorites) }.getOrDefault(emptyList())

    fun isFavorite(s: Station) = favorites().any { it.id == s.id }

    fun toggleFavorite(s: Station) {
        val favs = favorites().toMutableList()
        if (favs.removeAll { it.id == s.id }.not()) favs += s
        Prefs(context).radioFavorites = JSONArray().apply { favs.forEach { put(it.toJson()) } }.toString()
    }
}

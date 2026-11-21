package com.mubarak.bydspeed.radio

import org.json.JSONObject

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val country: String,
    val codec: String,
    val bitrate: Int,
    val tags: String,
    val hls: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("stationuuid", id).put("name", name).put("url_resolved", url)
        .put("countrycode", country).put("codec", codec).put("bitrate", bitrate)
        .put("tags", tags).put("hls", if (hls) 1 else 0)

    companion object {
        /** Parses a radio-browser.info station object (also our own saved format). */
        fun fromJson(o: JSONObject) = Station(
            id = o.optString("stationuuid"),
            name = o.optString("name").trim(),
            url = o.optString("url_resolved").ifEmpty { o.optString("url") },
            country = o.optString("countrycode"),
            codec = o.optString("codec"),
            bitrate = o.optInt("bitrate"),
            tags = o.optString("tags"),
            hls = o.optInt("hls") == 1 || o.optString("url_resolved").contains(".m3u8"),
        )
    }
}

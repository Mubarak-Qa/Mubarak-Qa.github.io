package com.mubarak.bydspeed.speed

/** A source of vehicle speed in km/h. */
interface SpeedProvider {
    val name: String
    /** Starts delivering speed updates. Returns false if this source is unavailable. */
    fun start(onSpeed: (Float) -> Unit): Boolean
    fun stop()
}

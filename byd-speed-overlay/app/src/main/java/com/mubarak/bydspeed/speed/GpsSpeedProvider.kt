package com.mubarak.bydspeed.speed

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/** Fallback: speed from GPS. Works everywhere except tunnels / poor reception. */
class GpsSpeedProvider(context: Context) : SpeedProvider {
    override val name = "GPS"

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun start(onSpeed: (Float) -> Unit): Boolean {
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
        val l = LocationListener { loc: Location ->
            onSpeed(if (loc.hasSpeed()) loc.speed * 3.6f else 0f)
        }
        return try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, l, Looper.getMainLooper())
            listener = l
            true
        } catch (e: SecurityException) {
            false
        }
    }

    override fun stop() {
        listener?.let(lm::removeUpdates)
        listener = null
    }
}

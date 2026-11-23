package com.mubarak.bydspeed

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme(Prefs(this).theme)
        installConscrypt()
        com.mubarak.bydspeed.car.ShizukuCar.autoStart()
        // Try the no-Shizuku ADB path automatically (works once paired).
        com.mubarak.bydspeed.car.AdbCar.connect(this)
    }

    private fun installConscrypt() {
        // Ensure Conscrypt is the top TLS provider so libadb's ADB TLS (STLS) handshake works,
        // like the working BYD ADB apps. Without this, the platform provider may fail the
        // ADB TLSv1.3 upgrade on this head unit.
        try {
            val cls = Class.forName("org.conscrypt.Conscrypt")
            val provider = cls.getMethod("newProvider").invoke(null) as java.security.Provider
            java.security.Security.insertProviderAt(provider, 1)
        } catch (t: Throwable) { /* fall back to platform TLS */ }
    }

    companion object {
        fun applyTheme(theme: String) {
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}

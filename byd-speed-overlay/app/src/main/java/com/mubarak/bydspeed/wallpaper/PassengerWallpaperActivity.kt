package com.mubarak.bydspeed.wallpaper

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView

/**
 * Fullscreen picture shown on the passenger screen. Android offers no public API to set the
 * system wallpaper of a secondary display, so the image is displayed by this activity instead.
 * Long-press to close.
 */
class PassengerWallpaperActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val iv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(Wallpapers.load(this@PassengerWallpaperActivity, intent.getStringExtra(EXTRA_SLOT) ?: "passenger"))
            setOnLongClickListener { finish(); true }
        }
        setContentView(iv)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    companion object { const val EXTRA_SLOT = "slot" }
}

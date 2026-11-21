package com.mubarak.bydspeed

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display

/** The car's screens (center display, passenger display, …) as Android sees them. */
object Displays {
    data class Screen(val id: Int, val name: String, val width: Int, val height: Int) {
        val label get() = "$name  (#$id · ${width}×$height)"
    }

    fun list(context: Context): List<Screen> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.displays.map { d ->
            @Suppress("DEPRECATION")
            val m = android.util.DisplayMetrics().also { d.getRealMetrics(it) }
            Screen(d.displayId, d.name ?: "Display", m.widthPixels, m.heightPixels)
        }
    }

    /** Best guess for the passenger screen: the first non-default display. */
    fun passenger(context: Context): Screen? = list(context).firstOrNull { it.id != Display.DEFAULT_DISPLAY }

    /** Starts [intent] on display [displayId]. Returns null on success, else the error. */
    fun launchOn(context: Context, intent: Intent, displayId: Int): String? = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val opts = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        context.startActivity(intent, opts.toBundle())
        null
    } catch (t: Throwable) {
        "${t.javaClass.simpleName}: ${t.message}"
    }
}

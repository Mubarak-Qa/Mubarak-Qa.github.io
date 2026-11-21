package com.mubarak.bydspeed.split

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper

object Apps {
    data class App(val pkg: String, val label: String, val icon: Drawable)

    /** All launchable apps, sorted by name. Call off the main thread. */
    fun list(context: Context): List<App> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun launchIntent(context: Context, pkg: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(pkg)

    /**
     * Opens [first] and then [second] next to it. Uses FLAG_ACTIVITY_LAUNCH_ADJACENT (system
     * split-screen) plus half-screen launch bounds, which freeform-window systems honour.
     * Returns null on success, else an error.
     */
    fun launchSplit(context: Context, first: String, second: String): String? {
        val app = context.applicationContext
        val a = launchIntent(app, first) ?: return first
        val b = launchIntent(app, second) ?: return second
        val dm = app.resources.displayMetrics
        val w = dm.widthPixels; val h = dm.heightPixels
        val half = if (w >= h) Rect(0, 0, w / 2, h) else Rect(0, 0, w, h / 2)
        val other = if (w >= h) Rect(w / 2, 0, w, h) else Rect(0, h / 2, w, h)
        return try {
            a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(a, ActivityOptions.makeBasic().setLaunchBounds(half).toBundle())
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching {
                    b.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    app.startActivity(b, ActivityOptions.makeBasic().setLaunchBounds(other).toBundle())
                }
            }, 900)
            null
        } catch (t: Throwable) {
            "${t.javaClass.simpleName}: ${t.message}"
        }
    }
}

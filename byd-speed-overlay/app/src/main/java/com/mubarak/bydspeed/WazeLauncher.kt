package com.mubarak.bydspeed

import android.content.Context
import android.content.Intent
import android.net.Uri

object WazeLauncher {
    const val PACKAGE = "com.waze"

    /** Opens the preinstalled Waze app. Returns false if Waze isn't installed. */
    fun open(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("waze://"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent); true
        } catch (e: Exception) {
            false
        }
    }
}

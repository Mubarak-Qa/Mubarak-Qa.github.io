package com.mubarak.bydspeed.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import java.io.File

object Wallpapers {
    /** Built-in gradient presets (start, end colors). */
    val presets = listOf(
        0xFF0F2027.toInt() to 0xFF2C5364.toInt(),
        0xFF141E30.toInt() to 0xFF243B55.toInt(),
        0xFF000000.toInt() to 0xFF434343.toInt(),
        0xFF3A1C71.toInt() to 0xFFD76D77.toInt(),
        0xFF134E5E.toInt() to 0xFF71B280.toInt(),
        0xFF8E0E00.toInt() to 0xFF1F1C18.toInt(),
        0xFFC9A227.toInt() to 0xFF3E2C0F.toInt(),
    )

    fun gradient(start: Int, end: Int, w: Int = 1920, h: Int = 1080): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), start, end, Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }

    fun file(context: Context, slot: String) = File(context.filesDir, "wallpaper_$slot.png")

    /** Copies a picked image into app storage, downscaled, so it survives permission loss. */
    fun store(context: Context, slot: String, uri: Uri): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / (sample * 2) >= 1920 || opts.outHeight / (sample * 2) >= 1920) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        store(context, slot, bmp)
        return bmp
    }

    fun store(context: Context, slot: String, bmp: Bitmap) {
        file(context, slot).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun load(context: Context, slot: String): Bitmap? =
        file(context, slot).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** Sets the system wallpaper of the center (default) screen. Returns null or the error. */
    fun applyToCenter(context: Context, bmp: Bitmap, lock: Boolean): String? = try {
        val flags = WallpaperManager.FLAG_SYSTEM or (if (lock) WallpaperManager.FLAG_LOCK else 0)
        WallpaperManager.getInstance(context).setBitmap(bmp, null, true, flags)
        null
    } catch (t: Throwable) {
        "${t.javaClass.simpleName}: ${t.message}"
    }
}

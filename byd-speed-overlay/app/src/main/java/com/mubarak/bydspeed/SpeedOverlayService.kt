package com.mubarak.bydspeed

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mubarak.bydspeed.speed.BydSpeedProvider
import com.mubarak.bydspeed.speed.GpsSpeedProvider
import com.mubarak.bydspeed.speed.SpeedProvider
import kotlin.math.roundToInt

/** Foreground service that draws a draggable floating speedometer over other apps (e.g. Waze). */
class SpeedOverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var root: LinearLayout? = null
    private lateinit var speedText: TextView
    private lateinit var sourceText: TextView
    private lateinit var background: GradientDrawable
    private var provider: SpeedProvider? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        goForeground()
        if (Settings.canDrawOverlays(this)) showOverlay() else stopSelf()
        startSpeedSource()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        provider?.stop()
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        super.onDestroy()
    }

    private fun startSpeedSource() {
        val candidates = listOf(BydSpeedProvider(this), GpsSpeedProvider(this))
        for (p in candidates) {
            if (p.start(::onSpeed)) {
                provider = p
                sourceText.text = "km/h · ${p.name}"
                return
            }
        }
        sourceText.text = "لا يوجد مصدر للسرعة"
    }

    private fun onSpeed(kmh: Float) {
        val v = kmh.roundToInt()
        speedText.text = v.toString()
        val over = v > prefs.speedLimit
        background.setColor(if (over) 0xE6C62828.toInt() else 0xCC000000.toInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics) }

        background = GradientDrawable().apply {
            cornerRadius = dp(24f)
            setColor(0xCC000000.toInt())
        }
        speedText = TextView(this).apply {
            text = "--"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.sizeSp.toFloat())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        sourceText = TextView(this).apply {
            text = "km/h"
            setTextColor(0xFFCFD8DC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, (prefs.sizeSp / 4f).coerceAtLeast(11f))
            gravity = Gravity.CENTER
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = dp(14f).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            this.background = this@SpeedOverlayService.background
            addView(speedText)
            addView(sourceText)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.overlayX
            y = prefs.overlayY
        }

        // Drag to move; position is remembered.
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        layout.setOnTouchListener { v: View, e: MotionEvent ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(v, params); true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.overlayX = params.x; prefs.overlayY = params.y; true
                }
                else -> false
            }
        }

        wm.addView(layout, params)
        root = layout
    }

    private fun goForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, SpeedOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = builder
            .setSmallIcon(R.drawable.ic_speed)
            .setContentTitle(getString(R.string.overlay_running))
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stopIntent).build())
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val CHANNEL = "speed_overlay"
        private const val NOTIF_ID = 1
        private const val ACTION_STOP = "stop"

        @Volatile var isRunning = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SpeedOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SpeedOverlayService::class.java))
        }
    }
}

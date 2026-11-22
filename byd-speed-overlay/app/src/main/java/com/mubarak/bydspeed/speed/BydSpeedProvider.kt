package com.mubarak.bydspeed.speed

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Method

/**
 * Reads speed from the car's own sensors through BYD's internal DiLink framework
 * (android.hardware.bydauto.*). This API is not public or documented: class and method
 * names come from the DiLink developer community and may differ between firmware
 * versions, so everything is resolved by reflection and failures are reported, not thrown.
 */
class BydSpeedProvider(private val context: Context) : SpeedProvider {
    override val name = "حساسات السيارة (BYD)"

    private val handler = Handler(Looper.getMainLooper())
    private var device: Any? = null
    private var getter: Method? = null
    private var poller: Runnable? = null

    /** Last error, for the diagnostics screen. */
    var lastError: String? = null
        private set

    private fun resolve(): Boolean {
        if (getter != null) return true
        for (className in CANDIDATE_CLASSES) {
            try {
                val cls = Class.forName(className)
                val dev = cls.getMethod("getInstance", Context::class.java).invoke(null, context)
                    ?: continue
                val m = CANDIDATE_METHODS.firstNotNullOfOrNull { n ->
                    runCatching { cls.getMethod(n) }.getOrNull()
                } ?: continue
                device = dev
                getter = m
                return true
            } catch (t: Throwable) {
                lastError = "$className: ${(t.cause ?: t).javaClass.simpleName}: ${(t.cause ?: t).message}"
            }
        }
        if (lastError == null) lastError = "BYD auto framework not found on this device"
        return false
    }

    fun readOnce(): Float? {
        if (!resolve()) return null
        return try {
            val v = (getter!!.invoke(device) as? Number)?.toFloat()
            if (v == null || v < 0f || v > 400f) null else v
        } catch (t: Throwable) {
            val c = t.cause ?: t
            lastError = "${c.javaClass.simpleName}: ${c.message}"
            null
        }
    }

    override fun start(onSpeed: (Float) -> Unit): Boolean {
        if (readOnce() == null) return false
        val r = object : Runnable {
            override fun run() {
                readOnce()?.let(onSpeed)
                handler.postDelayed(this, POLL_MS)
            }
        }
        poller = r
        handler.post(r)
        return true
    }

    override fun stop() {
        poller?.let(handler::removeCallbacks)
        poller = null
    }

    companion object {
        private const val POLL_MS = 200L
        val CANDIDATE_CLASSES = listOf(
            "android.hardware.bydauto.speed.BYDAutoSpeedDevice",
            // Leopard 8 / DiLink 5.1 has no speed device; wheel speed lives here.
            "android.hardware.bydauto.special.BYDAutoSpecialDevice",
        )
        val CANDIDATE_METHODS = listOf("getCurrentSpeed", "getWheelSpeed", "getSpeed", "getVehicleSpeed")

        /** Human-readable report of what the BYD framework exposes on this car. */
        fun diagnose(context: Context): String = buildString {
            for (className in CANDIDATE_CLASSES) {
                append("• ").append(className).append('\n')
                try {
                    val cls = Class.forName(className)
                    append("  ✔ الكلاس موجود\n")
                    cls.declaredMethods
                        .filter { it.name.startsWith("get") || it.name.contains("Listener") }
                        .sortedBy { it.name }
                        .forEach { append("    - ").append(it.name).append('\n') }
                    val p = BydSpeedProvider(context)
                    val v = p.readOnce()
                    if (v != null) append("  ✔ السرعة الحالية: $v km/h\n")
                    else append("  ✘ تعذرت القراءة: ${p.lastError}\n")
                } catch (t: Throwable) {
                    append("  ✘ غير موجود (${t.javaClass.simpleName})\n")
                }
            }
        }
    }
}

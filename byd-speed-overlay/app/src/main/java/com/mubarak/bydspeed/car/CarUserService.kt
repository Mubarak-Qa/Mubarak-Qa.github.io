package com.mubarak.bydspeed.car

import android.content.Context

/**
 * Runs inside a Shizuku-spawned process as the shell user (UID 2000). That user is on BYD's
 * whitelist for the BYDAUTO permissions, so the reflective calls that fail from the normal app
 * UID ("permission deny") succeed here. The app talks to this over the [ICarBridge] binder.
 *
 * Same technique OneHub uses: the heavy lifting is just "call the BYD framework from a shell
 * context". All reflection is reused from [CarBridge]; only the caller's identity differs.
 */
class CarUserService : ICarBridge.Stub {

    private val context: Context

    @Suppress("unused")
    constructor() : super() { context = systemContext() }
    constructor(context: Context) : super() { this.context = context }

    private fun systemContext(): Context {
        val at = Class.forName("android.app.ActivityThread")
        val thread = at.getMethod("systemMain").invoke(null)
        return at.getMethod("getSystemContext").invoke(thread) as Context
    }

    override fun destroy() { kotlin.system.exitProcess(0) }

    override fun invokeGet(className: String?, methods: Array<out String>?, args: IntArray?): String {
        val cls = className?.ifEmpty { null }
        val argList = (args?.toList() ?: emptyList()).map { it as Number }
        var err = "no-method"
        for (m in methods.orEmpty()) {
            val p = CarBridge.find(context, cls, m, argList.size) ?: run { err = "missing $m"; null } ?: continue
            try {
                val v = CarBridge.invoke(p.first, p.second, argList)
                return "OK:" + ((v as? Number)?.toDouble() ?: 0.0)
            } catch (t: Throwable) { err = CarBridge.describe(t) }
        }
        return "ERR:$err"
    }

    override fun invokeSet(className: String?, method: String?, args: DoubleArray?): String {
        val name = method ?: return "no-method"
        val argList = (args?.toList() ?: emptyList()).map { it as Number }
        val p = CarBridge.find(context, className?.ifEmpty { null }, name, argList.size)
            ?: return "missing $name/${argList.size}"
        return try {
            val r = CarBridge.invoke(p.first, p.second, argList)
            if (r is Int && r < 0) "code $r" else "OK"
        } catch (t: Throwable) { CarBridge.describe(t) }
    }

    override fun scanReport(): String =
        try { CarBridge.scanReport(context) } catch (t: Throwable) { "ERR:" + CarBridge.describe(t) }
}

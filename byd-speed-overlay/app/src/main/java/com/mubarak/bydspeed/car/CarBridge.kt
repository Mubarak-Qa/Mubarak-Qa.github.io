package com.mubarak.bydspeed.car

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Access to BYD's internal DiLink vehicle framework (android.hardware.bydauto.*).
 *
 * The framework is not public. Device class names below come from the DiLink developer
 * community and are unverified on the Leopard 8, so everything goes through reflection
 * and never throws. [scanReport] dumps what the car really exposes so the mapping in
 * [CarFeatures] can be corrected (also at runtime, via a JSON override).
 */
object CarBridge {
    private const val PKG = "android.hardware.bydauto"

    private val KNOWN_DEVICES = listOf(
        "ac.BYDAutoAcDevice", "bodywork.BYDAutoBodyworkDevice", "speed.BYDAutoSpeedDevice",
        "statistic.BYDAutoStatisticDevice", "energy.BYDAutoEnergyDevice", "tyre.BYDAutoTyreDevice",
        "gearbox.BYDAutoGearboxDevice", "engine.BYDAutoEngineDevice", "setting.BYDAutoSettingDevice",
        "instrument.BYDAutoInstrumentDevice", "sensor.BYDAutoSensorDevice",
        "charging.BYDAutoChargingDevice", "light.BYDAutoLightDevice", "doorlock.BYDAutoDoorLockDevice",
        "safetybelt.BYDAutoSafetyBeltDevice", "seat.BYDAutoSeatDevice", "special.BYDAutoSpecialDevice",
        "multimedia.BYDAutoMultimediaDevice", "panorama.BYDAutoPanoramaDevice", "radar.BYDAutoRadarDevice",
    ).map { "$PKG.$it" }

    private var devices: Map<String, Any>? = null
    private val errors = linkedMapOf<String, String>()

    /** Class name → device instance, for every BYD device that could be instantiated. */
    @Synchronized
    fun devices(context: Context): Map<String, Any> {
        devices?.let { return it }
        val found = linkedMapOf<String, Any>()
        val names = (KNOWN_DEVICES + discoverClassNames()).distinct()
        for (name in names) {
            try {
                val cls = Class.forName(name)
                val inst = cls.getMethod("getInstance", Context::class.java)
                    .invoke(null, context.applicationContext)
                if (inst != null) found[name] = inst
            } catch (t: Throwable) {
                errors[name] = describe(t)
            }
        }
        devices = found
        return found
    }

    val isAvailable get() = devices?.isNotEmpty() == true

    fun describe(t: Throwable): String {
        val c = generateSequence(t) { it.cause }.last()
        return "${c.javaClass.simpleName}: ${c.message ?: ""}".trim()
    }

    /** Finds a public method by exact name (and optional parameter count) on any device. */
    fun find(context: Context, className: String?, name: String, params: Int? = null): Pair<Any, Method>? {
        val devs = devices(context)
        val targets = if (className != null) listOfNotNull(devs[className]?.let { className to it })
        else devs.entries.map { it.key to it.value }
        for ((_, inst) in targets) {
            val m = inst.javaClass.methods.firstOrNull {
                it.name == name && (params == null || it.parameterTypes.size == params)
            }
            if (m != null) return inst to m
        }
        return null
    }

    /** Finds a method whose name contains all [keywords] (case-insensitive). */
    fun findByKeywords(context: Context, prefix: String, keywords: List<String>, params: Int): Pair<Any, Method>? {
        for ((_, inst) in devices(context)) {
            val m = inst.javaClass.methods.firstOrNull { m ->
                m.name.startsWith(prefix) && m.parameterTypes.size == params &&
                    keywords.all { m.name.contains(it, ignoreCase = true) }
            }
            if (m != null) return inst to m
        }
        return null
    }

    /** Invokes [m] converting numeric args to the declared parameter types. */
    fun invoke(target: Any, m: Method, args: List<Number>): Any? {
        val converted = m.parameterTypes.mapIndexed { i, type ->
            val n = args.getOrElse(i) { 0 }
            when (type) {
                Int::class.javaPrimitiveType, Int::class.javaObjectType -> n.toInt()
                Double::class.javaPrimitiveType, Double::class.javaObjectType -> n.toDouble()
                Float::class.javaPrimitiveType, Float::class.javaObjectType -> n.toFloat()
                Long::class.javaPrimitiveType, Long::class.javaObjectType -> n.toLong()
                Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> n.toInt() != 0
                else -> n
            }
        }
        return m.invoke(target, *converted.toTypedArray())
    }

    /** Best effort: list bydauto classes from boot classpath jars that still contain dex. */
    @Suppress("DEPRECATION")
    private fun discoverClassNames(): List<String> {
        val jars = (System.getenv("BOOTCLASSPATH")?.split(':').orEmpty() +
            System.getenv("SYSTEMSERVERCLASSPATH")?.split(':').orEmpty()).filter { it.endsWith(".jar") }
        val out = mutableListOf<String>()
        for (jar in jars) {
            try {
                val dex = dalvik.system.DexFile(jar)
                for (e in dex.entries()) {
                    if (e.startsWith(PKG) && e.endsWith("Device") && '$' !in e) out += e
                }
            } catch (_: Throwable) { }
        }
        return out
    }

    /** Full diagnostic report of the car framework, for sharing with the developer. */
    fun scanReport(context: Context): String = buildString {
        devices = null; errors.clear()
        val devs = devices(context)
        append("=== BYD framework scan ===\n")
        append("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
        append("Model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} / ${android.os.Build.DISPLAY}\n")
        append("Devices found: ${devs.size}\n\n")
        for ((name, inst) in devs) {
            append("■ ").append(name.removePrefix("$PKG.")).append('\n')
            inst.javaClass.methods
                .filter { it.declaringClass.name.startsWith(PKG) && !Modifier.isStatic(it.modifiers) }
                .sortedBy { it.name }
                .forEach { m ->
                    append("   ").append(m.returnType.simpleName).append(' ').append(m.name).append('(')
                    append(m.parameterTypes.joinToString(",") { it.simpleName }).append(")\n")
                }
            // Public int constants (area / mode codes) are essential to call setters correctly.
            val consts = inst.javaClass.fields.filter {
                Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType
            }
            if (consts.isNotEmpty()) {
                append("   consts: ")
                append(consts.joinToString(", ") { f -> "${f.name}=${runCatching { f.getInt(null) }.getOrNull()}" })
                append('\n')
            }
        }
        if (errors.isNotEmpty()) {
            append("\nNot available:\n")
            errors.forEach { (k, v) -> append(" - ${k.removePrefix("$PKG.")}: $v\n") }
        }
        append("\nBYD permissions:\n")
        try {
            val pi: PackageInfo = context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            pi.requestedPermissions?.forEachIndexed { i, p ->
                if (p.contains("BYDAUTO")) {
                    val granted = (pi.requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (granted) append(" ✔ ").append(p.removePrefix("android.permission.")).append('\n')
                }
            }
        } catch (_: Throwable) { }
        append("\nFeature bindings:\n")
        CarFeatures.all.forEach { f -> append(" - ${f.id}: ${CarFeatures.bindingInfo(context, f)}\n") }
    }
}

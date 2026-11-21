package com.mubarak.bydspeed.car

import android.content.Context
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

    /** Methods every BYD device inherits; they add noise to the report. */
    private val COMMON = setOf(
        "arrayToStr", "get", "set", "getDevicetype", "getFeatureList", "getGetPermission",
        "getSetPermission", "getType", "onError", "onPostEvent", "postEvent", "registerListener",
        "unregisterListener",
    )

    /** Devices most relevant to the controls, reported first. */
    private val PRIORITY = listOf("ac", "seat", "setting", "bodywork", "statistic", "speed",
        "energy", "tyre", "gearbox", "sensor", "engine", "charging", "light")

    private fun shortName(n: String) = n.removePrefix("$PKG.")

    private fun formatValue(v: Any?): String = when (v) {
        null -> "null"
        is IntArray -> v.joinToString(",", "[", "]")
        is DoubleArray -> v.joinToString(",", "[", "]")
        is FloatArray -> v.joinToString(",", "[", "]")
        is ByteArray -> "byte[${v.size}]"
        is Number, is Boolean -> v.toString()
        is String -> "\"$v\""
        else -> v.javaClass.simpleName
    }

    /**
     * Compact diagnostic report: relevant devices first, inherited boilerplate removed,
     * live values of simple getters, and constants only where they matter (area/level codes).
     */
    fun scanReport(context: Context): String = buildString {
        devices = null; errors.clear()
        val devs = devices(context)
        append("=== BYD scan v2 ===\n")
        append("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ")
        append("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · ${android.os.Build.DISPLAY}\n")
        append("Devices: ${devs.keys.joinToString(", ") { shortName(it).substringBefore('.') }}\n")

        val ordered = devs.entries.sortedBy { e ->
            PRIORITY.indexOf(shortName(e.key).substringBefore('.')).let { if (it < 0) 99 else it }
        }
        for ((name, inst) in ordered) {
            val group = shortName(name).substringBefore('.')
            append("\n■ ").append(shortName(name))
            runCatching {
                val g = inst.javaClass.getMethod("getGetPermission").invoke(inst)
                val st = inst.javaClass.getMethod("getSetPermission").invoke(inst)
                append("  [get=$g set=$st]")
            }
            append('\n')
            inst.javaClass.methods
                .filter { it.declaringClass.name.startsWith(PKG) && !Modifier.isStatic(it.modifiers) && it.name !in COMMON }
                .sortedBy { it.name }
                .forEach { m ->
                    append("  ").append(m.name).append('(')
                    append(m.parameterTypes.joinToString(",") { it.simpleName }).append(')')
                    val simpleGetter = m.name.startsWith("get") && m.parameterTypes.isEmpty() &&
                        m.returnType != Void.TYPE
                    if (simpleGetter) {
                        append(" = ").append(runCatching { formatValue(m.invoke(inst)) }
                            .getOrElse { "ERR " + describe(it).take(60) })
                    } else if (m.returnType != Void.TYPE && m.returnType != Int::class.javaPrimitiveType) {
                        append(':').append(m.returnType.simpleName)
                    }
                    append('\n')
                }
            // Constants only for control-related devices (instrument/navigation ones are huge).
            if (group in setOf("ac", "seat", "setting", "bodywork", "statistic", "gearbox", "tyre", "energy")) {
                val consts = inst.javaClass.fields.filter {
                    Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType &&
                        !it.name.contains("COMMAND_")
                }
                if (consts.isNotEmpty()) {
                    append("  consts: ")
                    append(consts.joinToString(", ") { f -> "${f.name}=${runCatching { f.getInt(null) }.getOrNull()}" })
                    append('\n')
                }
            }
        }
        if (errors.isNotEmpty()) {
            append("\nNot available: ")
            append(errors.keys.joinToString(", ") { shortName(it) }).append('\n')
        }
        append("\nBindings:\n")
        CarFeatures.all.forEach { f -> append(" ${f.id}: ${CarFeatures.bindingInfo(context, f)}\n") }
    }

    /** Splits a long report into chat-sized parts on line boundaries. */
    fun split(report: String, max: Int = 3500): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        for (line in report.lines()) {
            if (sb.length + line.length + 1 > max && sb.isNotEmpty()) { parts += sb.toString(); sb.clear() }
            sb.append(line).append('\n')
        }
        if (sb.isNotBlank()) parts += sb.toString()
        return parts.mapIndexed { i, p -> "[${i + 1}/${parts.size}]\n$p" }
    }
}

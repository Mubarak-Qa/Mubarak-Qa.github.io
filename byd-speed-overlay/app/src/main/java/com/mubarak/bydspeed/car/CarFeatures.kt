package com.mubarak.bydspeed.car

import android.content.Context
import com.mubarak.bydspeed.Prefs
import com.mubarak.bydspeed.R
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method

enum class Kind { READ, TOGGLE, LEVEL }

private const val AC = "android.hardware.bydauto.ac.BYDAutoAcDevice"
private const val SETTING = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
private const val STAT = "android.hardware.bydauto.statistic.BYDAutoStatisticDevice"

/**
 * A car indicator or control, mapped to methods confirmed by the Leopard 8 scan (DiLink 5.1).
 * Argument order for multi-arg setters is inferred from the framework's constants and may need
 * adjusting; any field can be overridden at runtime with JSON keyed by [id]:
 *
 *   {"ac_temp_driver": {"cls":"…AcDevice", "get":"getTemprature","getArgs":[1],
 *     "set":"setAcTemperature","setArgs":[1,"v",0,1], "on":"start","onArgs":[0],
 *     "off":"stop","offArgs":[0], "offset":0, "scale":1, "labels":{"1":"P"}}}
 *
 * Setters are only ever bound by exact name — never by keyword — so a guess can't
 * accidentally hit an unrelated car function.
 */
data class Feature(
    val id: String,
    val label: Int,
    val kind: Kind,
    val unit: String = "",
    val min: Int = 0,
    val max: Int = 1,
    val cls: String? = null,
    val get: List<String> = emptyList(),
    val getArgs: List<Int> = emptyList(),
    val getKeywords: List<String> = emptyList(),
    val set: String? = null,
    /** Setter args; the string "v" is replaced with the device value. */
    val setArgs: List<Any> = listOf("v"),
    val on: String? = null,
    val onArgs: List<Int> = emptyList(),
    val off: String? = null,
    val offArgs: List<Int> = emptyList(),
    /** device value = UI value + offset (BYD seat levels use 1 = off). */
    val offset: Int = 0,
    /** Alternate setter used when the UI value is 0 (e.g. massage off is a mode, not a level). */
    val zeroSet: String? = null,
    val zeroArgs: List<Int> = emptyList(),
    /** String resources naming each UI level, index = UI value. */
    val levelLabels: List<Int> = emptyList(),
)

class Binding(
    private val getter: Pair<Any, Method>?,
    private val getArgs: List<Number>,
    private val setter: Pair<Any, Method>?,
    private val setArgs: List<Any>,
    private val onM: Pair<Any, Method>?,
    private val onArgs: List<Number>,
    private val offM: Pair<Any, Method>?,
    private val offArgs: List<Number>,
    private val zeroM: Pair<Any, Method>?,
    private val zeroArgs: List<Number>,
    private val offset: Int,
    private val scale: Double,
    val labels: Map<String, String>,
) {
    val canRead get() = getter != null
    val canWrite get() = setter != null || (onM != null && offM != null)

    /** UI value, or null when unavailable (BYD returns sentinel codes on failure). */
    fun read(): Double? = try {
        getter?.let { (o, m) ->
            val raw = (CarBridge.invoke(o, m, getArgs) as? Number)?.toDouble() ?: return null
            if (raw <= -2147482000.0 || raw == 65535.0) null else raw * scale - offset
        }
    } catch (t: Throwable) { null }

    /** Returns null on success, or an error message. */
    fun write(value: Int): String? {
        return try {
            val result = when {
                value == 0 && zeroM != null -> CarBridge.invoke(zeroM.first, zeroM.second, zeroArgs)
                onM != null && offM != null && setter == null -> {
                    val (o, m) = if (value != 0) onM else offM
                    CarBridge.invoke(o, m, if (value != 0) onArgs else offArgs)
                }
                else -> {
                    val (o, m) = setter ?: return "unsupported"
                    CarBridge.invoke(o, m, setArgs.map { if (it == "v") value + offset else (it as Number) })
                }
            }
            // BYD setters return negative command codes on failure (e.g. -2147482648).
            if (result is Int && result < 0) "code $result" else null
        } catch (t: Throwable) { CarBridge.describe(t) }
    }

    override fun toString() = buildString {
        append("get=").append(getter?.second?.name ?: "—")
        append(" set=").append(setter?.second?.name
            ?: listOfNotNull(onM?.second?.name, offM?.second?.name).joinToString("/").ifEmpty { "—" })
    }
}

object CarFeatures {
    private val offLowHigh = listOf(R.string.lvl_off, R.string.lvl_low, R.string.lvl_high)
    private val massageLevels = listOf(R.string.lvl_off, R.string.lvl_1, R.string.lvl_2, R.string.lvl_3)

    val indicators = listOf(
        Feature("speed", R.string.ind_speed, Kind.READ, "km/h", get = listOf("getWheelSpeed")),
        Feature("battery", R.string.ind_battery, Kind.READ, "%", cls = STAT, get = listOf("getElecPercentageValue", "getSOCBatteryPercentage")),
        Feature("fuel", R.string.ind_fuel, Kind.READ, "%", cls = STAT, get = listOf("getFuelPercentageValue")),
        Feature("range_ev", R.string.ind_range_ev, Kind.READ, "km", cls = STAT, get = listOf("getElecDrivingRangeValue")),
        Feature("range_fuel", R.string.ind_range_fuel, Kind.READ, "km", cls = STAT, get = listOf("getFuelDrivingRangeValue")),
        Feature("odometer", R.string.ind_odometer, Kind.READ, "km", get = listOf("getTotalMileageValue", "getTotalMileage")),
        Feature("gear", R.string.ind_gear, Kind.READ, get = listOf("getGearboxAutoModeType")),
        Feature("temp_out", R.string.ind_temp_out, Kind.READ, "°C", cls = AC, get = listOf("getTemprature"), getArgs = listOf(4)),
        Feature("temp_in", R.string.ind_temp_in, Kind.READ, "°C", getKeywords = listOf("Inside", "Temp")),
    ) + listOf(
        "fl" to R.string.ind_tyre_fl, "fr" to R.string.ind_tyre_fr,
        "rl" to R.string.ind_tyre_rl, "rr" to R.string.ind_tyre_rr,
    ).mapIndexed { i, (k, label) ->
        Feature("tyre_$k", label, Kind.READ, "kPa", get = listOf("getTyrePressureValue"), getArgs = listOf(i + 1))
    }

    val frontAc = listOf(
        // start/stop(int source): AC_CTRL_SOURCE_UI_KEY = 0
        Feature("ac_power", R.string.ac_power, Kind.TOGGLE, cls = AC, get = listOf("getAcStartState"),
            on = "start", onArgs = listOf(0), off = "stop", offArgs = listOf(0)),
        // setAcTemperature(area, value, source, unit): MAIN=1, DEPUTY=2, REAR=3; UI_KEY=0; °C=1
        Feature("ac_temp_driver", R.string.ac_temp_driver, Kind.LEVEL, "°C", 17, 33, cls = AC,
            get = listOf("getTemprature"), getArgs = listOf(1), set = "setAcTemperature", setArgs = listOf(1, "v", 0, 1)),
        Feature("ac_temp_passenger", R.string.ac_temp_passenger, Kind.LEVEL, "°C", 17, 33, cls = AC,
            get = listOf("getTemprature"), getArgs = listOf(2), set = "setAcTemperature", setArgs = listOf(2, "v", 0, 1)),
        // setAcWindLevel(source, level): levels 0..7
        Feature("ac_fan", R.string.ac_fan, Kind.LEVEL, "", 0, 7, cls = AC,
            get = listOf("getAcWindLevel"), set = "setAcWindLevel", setArgs = listOf(0, "v")),
    )

    val rearAc = listOf(
        Feature("rear_power", R.string.rear_power, Kind.TOGGLE, cls = AC, get = listOf("getRearAcStartState"),
            on = "startRearAc", onArgs = listOf(0), off = "stopRearAc", offArgs = listOf(0)),
        Feature("rear_temp", R.string.rear_temp, Kind.LEVEL, "°C", 17, 33, cls = AC,
            get = listOf("getTemprature"), getArgs = listOf(3), set = "setAcTemperature", setArgs = listOf(3, "v", 0, 1)),
        Feature("rear_fan", R.string.rear_fan, Kind.LEVEL, "", 0, 7, cls = AC,
            get = listOf("getRearAcWindLevel"), set = "setRearAcWindLevel", setArgs = listOf(0, "v")),
    )

    /** Seat codes: DRIVER_SEAT=1, PASSENGER_SEAT=2, REAR_LEFT_SEAT=3, REAR_RIGHT_SEAT=4. */
    private val seatIds = listOf(
        Triple("driver", 1, listOf(R.string.seat_heat_driver, R.string.seat_vent_driver, R.string.seat_massage_driver)),
        Triple("passenger", 2, listOf(R.string.seat_heat_passenger, R.string.seat_vent_passenger, R.string.seat_massage_passenger)),
        Triple("rear_left", 3, listOf(R.string.seat_heat_rl, R.string.seat_vent_rl, 0)),
        Triple("rear_right", 4, listOf(R.string.seat_heat_rr, R.string.seat_vent_rr, 0)),
    )

    val seats = seatIds.flatMap { (who, seat, labels) ->
        listOfNotNull(
            // Heating/ventilating: OFF=1, LOW=2, HIGH=3 → UI 0..2 with offset 1
            Feature("seat_heat_$who", labels[0], Kind.LEVEL, "", 0, 2, cls = SETTING,
                get = listOf("getSeatHeatingState"), getArgs = listOf(seat),
                set = "setSeatHeatingState", setArgs = listOf(seat, "v"), offset = 1, levelLabels = offLowHigh),
            Feature("seat_vent_$who", labels[1], Kind.LEVEL, "", 0, 2, cls = SETTING,
                get = listOf("getSeatVentilatingState"), getArgs = listOf(seat),
                set = "setSeatVentilatingState", setArgs = listOf(seat, "v"), offset = 1, levelLabels = offLowHigh),
            // Massage: intensity 1..3; off = setMassageMode(seat, MASSAGE_MODE_CLOSE=1)
            if (labels[2] == 0) null else
                Feature("seat_massage_$who", labels[2], Kind.LEVEL, "", 0, 3, cls = SETTING,
                    get = listOf("getMassageLevel"), getArgs = listOf(seat),
                    set = "setMassageLevel", setArgs = listOf(seat, "v"),
                    zeroSet = "setMassageMode", zeroArgs = listOf(seat, 1), levelLabels = massageLevels),
        )
    } + listOf(
        // STEERING_WHEEL_HEATING_OFF=1, ON=2
        Feature("steering_heat", R.string.steering_heat, Kind.TOGGLE, cls = SETTING,
            get = listOf("getSteeringWheelHeatingState"), set = "setSteeringWheelHeatingState",
            setArgs = listOf("v"), offset = 1),
    )

    val all = indicators + frontAc + rearAc + seats

    private val cache = mutableMapOf<String, Binding>()

    fun reset() = cache.clear()

    private fun overrides(context: Context): JSONObject =
        runCatching { JSONObject(Prefs(context).carMapping) }.getOrElse { JSONObject() }

    fun bind(context: Context, f: Feature): Binding = cache.getOrPut(f.id) {
        val o = overrides(context).optJSONObject(f.id)
        val cls = o?.optString("cls")?.ifEmpty { null } ?: f.cls

        fun nums(a: JSONArray?) = a?.let { arr -> (0 until arr.length()).map { arr.optDouble(it) as Number } }
        fun anys(a: JSONArray?) = a?.let { arr -> (0 until arr.length()).map { i ->
            arr.get(i).let { v -> if (v == "v") "v" else (v as Number) } } }

        val getArgs = nums(o?.optJSONArray("getArgs")) ?: f.getArgs
        val setArgs = anys(o?.optJSONArray("setArgs")) ?: f.setArgs
        val getNames = o?.optString("get")?.ifEmpty { null }?.let(::listOf) ?: f.get
        val setName = o?.optString("set")?.ifEmpty { null } ?: f.set

        val getter = getNames.firstNotNullOfOrNull { CarBridge.find(context, cls, it, getArgs.size) }
            ?: getNames.firstNotNullOfOrNull { CarBridge.find(context, null, it, getArgs.size) }
            ?: if (o?.has("get") != true && f.getKeywords.isNotEmpty())
                CarBridge.findByKeywords(context, "get", f.getKeywords, getArgs.size) else null
        val setter = if (f.kind == Kind.READ) null else setName?.let { CarBridge.find(context, cls, it, setArgs.size) }

        val onArgs = nums(o?.optJSONArray("onArgs")) ?: f.onArgs
        val offArgs = nums(o?.optJSONArray("offArgs")) ?: f.offArgs
        val onM = (o?.optString("on")?.ifEmpty { null } ?: f.on)?.let { CarBridge.find(context, cls, it, onArgs.size) }
        val offM = (o?.optString("off")?.ifEmpty { null } ?: f.off)?.let { CarBridge.find(context, cls, it, offArgs.size) }
        val zeroM = f.zeroSet?.let { CarBridge.find(context, cls, it, f.zeroArgs.size) }

        val labels = mutableMapOf<String, String>()
        o?.optJSONObject("labels")?.let { l -> l.keys().forEach { k -> labels[k] = l.getString(k) } }
        if (f.id == "gear" && labels.isEmpty()) {
            // GEARBOX_AUTO_MODE_P=1, R=2, N=3, D=4, M=5, S=6
            labels.putAll(mapOf("1" to "P", "2" to "R", "3" to "N", "4" to "D", "5" to "M", "6" to "S"))
        }

        Binding(getter, getArgs, setter, setArgs, onM, onArgs, offM, offArgs, zeroM, f.zeroArgs,
            o?.optInt("offset", f.offset) ?: f.offset, o?.optDouble("scale", 1.0) ?: 1.0, labels)
    }

    fun bindingInfo(context: Context, f: Feature) = bind(context, f).toString()
}

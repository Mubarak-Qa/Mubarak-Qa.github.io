package com.mubarak.bydspeed.car

import android.content.Context
import com.mubarak.bydspeed.Prefs
import com.mubarak.bydspeed.R
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method

enum class Kind { READ, TOGGLE, LEVEL }

/**
 * A car indicator or control. Default method names / keywords / args are best guesses
 * (unverified on the Leopard 8). Any of them can be replaced at runtime by a JSON override
 * saved in Settings → Car developer tools, keyed by [id]:
 *
 *   {"ac_temp_driver": {"cls":"android.hardware.bydauto.ac.BYDAutoAcDevice",
 *     "get":"getTemprature","getArgs":[1], "set":"setAcTemperature","setArgs":[1,"v"],
 *     "on":"start","onArgs":[0], "off":"stop","offArgs":[0], "scale":1,
 *     "labels":{"1":"P","2":"R"}}}
 */
data class Feature(
    val id: String,
    val label: Int,
    val kind: Kind,
    val unit: String = "",
    val min: Int = 0,
    val max: Int = 1,
    val get: List<String> = emptyList(),
    val getArgs: List<Int> = emptyList(),
    val getKeywords: List<String> = emptyList(),
    val set: List<String> = emptyList(),
    /** Setter args; the string "v" is replaced with the new value. */
    val setArgs: List<Any> = listOf("v"),
    val setKeywords: List<String> = emptyList(),
    val on: String? = null,
    val off: String? = null,
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
    private val scale: Double,
    val labels: Map<String, String>,
) {
    val canRead get() = getter != null
    val canWrite get() = setter != null || (onM != null && offM != null)

    fun read(): Double? = try {
        getter?.let { (o, m) -> (CarBridge.invoke(o, m, getArgs) as? Number)?.toDouble()?.times(scale) }
    } catch (t: Throwable) { null }

    /** Returns null on success, or an error message. */
    fun write(value: Int): String? {
        return try {
            if (onM != null && offM != null && setter == null) {
                val (o, m) = if (value != 0) onM else offM
                CarBridge.invoke(o, m, if (value != 0) onArgs else offArgs)
            } else {
                val (o, m) = setter ?: return "unsupported"
                val args = setArgs.map { if (it == "v") value else (it as Number) }
                CarBridge.invoke(o, m, args)
            }
            null
        } catch (t: Throwable) { CarBridge.describe(t) }
    }

    override fun toString() = buildString {
        append("get=").append(getter?.second?.name ?: "—")
        append(" set=").append(setter?.second?.name ?: listOfNotNull(onM?.second?.name, offM?.second?.name).joinToString("/").ifEmpty { "—" })
    }
}

object CarFeatures {
    private val seatAreas = mapOf("driver" to 1, "passenger" to 2)

    val indicators = listOf(
        Feature("speed", R.string.ind_speed, Kind.READ, "km/h", get = listOf("getCurrentSpeed")),
        Feature("battery", R.string.ind_battery, Kind.READ, "%", get = listOf("getElecPercentageValue"), getKeywords = listOf("Elec", "Percent")),
        Feature("fuel", R.string.ind_fuel, Kind.READ, "%", get = listOf("getFuelPercentageValue"), getKeywords = listOf("Fuel", "Percent")),
        Feature("range_ev", R.string.ind_range_ev, Kind.READ, "km", get = listOf("getElecDrivingRangeValue"), getKeywords = listOf("Elec", "Range")),
        Feature("range_fuel", R.string.ind_range_fuel, Kind.READ, "km", get = listOf("getFuelDrivingRangeValue"), getKeywords = listOf("Fuel", "Range")),
        Feature("odometer", R.string.ind_odometer, Kind.READ, "km", get = listOf("getTotalMileageValue"), getKeywords = listOf("Total", "Mileage")),
        Feature("gear", R.string.ind_gear, Kind.READ, get = listOf("getGearboxAutoModeType"), getKeywords = listOf("Gear")),
        Feature("temp_out", R.string.ind_temp_out, Kind.READ, "°C", get = listOf("getTemperatureSensorValue"), getKeywords = listOf("Out", "Temp")),
        Feature("temp_in", R.string.ind_temp_in, Kind.READ, "°C", getKeywords = listOf("Inside", "Temp")),
    ) + listOf(
        "fl" to R.string.ind_tyre_fl, "fr" to R.string.ind_tyre_fr,
        "rl" to R.string.ind_tyre_rl, "rr" to R.string.ind_tyre_rr,
    ).mapIndexed { i, (k, label) ->
        Feature("tyre_$k", label, Kind.READ, "kPa", get = listOf("getTyrePressureValue"),
            getArgs = listOf(i + 1), getKeywords = listOf("Tyre", "Pressure"))
    }

    val frontAc = listOf(
        Feature("ac_power", R.string.ac_power, Kind.TOGGLE, get = listOf("getAcStartState"), on = "start", off = "stop"),
        Feature("ac_temp_driver", R.string.ac_temp_driver, Kind.LEVEL, "°C", 17, 32,
            get = listOf("getTemprature", "getTemperature"), getArgs = listOf(1),
            set = listOf("setAcTemperature"), setArgs = listOf(1, "v")),
        Feature("ac_temp_passenger", R.string.ac_temp_passenger, Kind.LEVEL, "°C", 17, 32,
            get = listOf("getTemprature", "getTemperature"), getArgs = listOf(2),
            set = listOf("setAcTemperature"), setArgs = listOf(2, "v")),
        Feature("ac_fan", R.string.ac_fan, Kind.LEVEL, "", 0, 7,
            get = listOf("getAcWindLevel"), set = listOf("setAcWindLevel"), setArgs = listOf("v")),
    )

    val rearAc = listOf(
        Feature("rear_power", R.string.rear_power, Kind.TOGGLE,
            getKeywords = listOf("Rear", "State"), setKeywords = listOf("Rear", "Switch")),
        Feature("rear_temp", R.string.rear_temp, Kind.LEVEL, "°C", 17, 32,
            getKeywords = listOf("Rear", "Temp"), setKeywords = listOf("Rear", "Temp")),
        Feature("rear_fan", R.string.rear_fan, Kind.LEVEL, "", 0, 7,
            getKeywords = listOf("Rear", "Wind"), setKeywords = listOf("Rear", "Wind")),
    )

    val seats = seatAreas.flatMap { (who, area) ->
        val d = who == "driver"
        listOf(
            Feature("seat_heat_$who", if (d) R.string.seat_heat_driver else R.string.seat_heat_passenger, Kind.LEVEL, "", 0, 3,
                getArgs = listOf(area), getKeywords = listOf("Seat", "Heat"),
                setArgs = listOf(area, "v"), setKeywords = listOf("Seat", "Heat")),
            Feature("seat_vent_$who", if (d) R.string.seat_vent_driver else R.string.seat_vent_passenger, Kind.LEVEL, "", 0, 3,
                getArgs = listOf(area), getKeywords = listOf("Seat", "Ventil"),
                setArgs = listOf(area, "v"), setKeywords = listOf("Seat", "Ventil")),
            Feature("seat_massage_$who", if (d) R.string.seat_massage_driver else R.string.seat_massage_passenger, Kind.LEVEL, "", 0, 3,
                getArgs = listOf(area), getKeywords = listOf("Massage"),
                setArgs = listOf(area, "v"), setKeywords = listOf("Massage")),
        )
    }

    val all = indicators + frontAc + rearAc + seats

    private val cache = mutableMapOf<String, Binding>()

    fun reset() = cache.clear()

    private fun overrides(context: Context): JSONObject =
        runCatching { JSONObject(Prefs(context).carMapping) }.getOrElse { JSONObject() }

    fun bind(context: Context, f: Feature): Binding = cache.getOrPut(f.id) {
        val o = overrides(context).optJSONObject(f.id)
        val cls = o?.optString("cls")?.ifEmpty { null }

        fun ints(a: JSONArray?) = a?.let { arr -> (0 until arr.length()).map { arr.optDouble(it) as Number } }
        fun anys(a: JSONArray?) = a?.let { arr -> (0 until arr.length()).map { i ->
            arr.get(i).let { v -> if (v == "v") "v" else (v as Number) } } }

        val getArgs = ints(o?.optJSONArray("getArgs")) ?: f.getArgs
        val setArgs = anys(o?.optJSONArray("setArgs")) ?: f.setArgs
        val getNames = o?.optString("get")?.ifEmpty { null }?.let(::listOf) ?: f.get
        val setNames = o?.optString("set")?.ifEmpty { null }?.let(::listOf) ?: f.set

        val getter = getNames.firstNotNullOfOrNull { CarBridge.find(context, cls, it, getArgs.size) }
            ?: if (o?.has("get") != true && f.getKeywords.isNotEmpty())
                CarBridge.findByKeywords(context, "get", f.getKeywords, getArgs.size) else null
        val setter = if (f.kind == Kind.READ) null else
            setNames.firstNotNullOfOrNull { CarBridge.find(context, cls, it, setArgs.size) }
                ?: if (o?.has("set") != true && f.setKeywords.isNotEmpty())
                    CarBridge.findByKeywords(context, "set", f.setKeywords, setArgs.size) else null

        val onName = o?.optString("on")?.ifEmpty { null } ?: f.on
        val offName = o?.optString("off")?.ifEmpty { null } ?: f.off
        val onArgs = ints(o?.optJSONArray("onArgs")) ?: emptyList()
        val offArgs = ints(o?.optJSONArray("offArgs")) ?: emptyList()
        val onM = onName?.let { CarBridge.find(context, cls, it, onArgs.size) ?: CarBridge.find(context, cls, it) }
        val offM = offName?.let { CarBridge.find(context, cls, it, offArgs.size) ?: CarBridge.find(context, cls, it) }

        val labels = mutableMapOf<String, String>()
        o?.optJSONObject("labels")?.let { l -> l.keys().forEach { k -> labels[k] = l.getString(k) } }

        Binding(getter, getArgs, setter, setArgs, onM, onArgs, offM, offArgs,
            o?.optDouble("scale", 1.0) ?: 1.0, labels)
    }

    fun bindingInfo(context: Context, f: Feature) = bind(context, f).toString()
}

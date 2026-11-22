package com.mubarak.bydspeed.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.mubarak.bydspeed.R
import com.mubarak.bydspeed.car.Binding
import com.mubarak.bydspeed.car.CarBridge
import com.mubarak.bydspeed.car.CarFeatures
import com.mubarak.bydspeed.car.ShizukuCar
import com.mubarak.bydspeed.car.CarLink
import androidx.activity.result.contract.ActivityResultContracts
import com.mubarak.bydspeed.car.Feature
import com.mubarak.bydspeed.car.Kind
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Main tab: live car indicators plus climate, rear climate and seat controls. */
class CarFragment : Fragment() {

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val refreshers = mutableListOf<() -> Unit>()
    private lateinit var content: LinearLayout

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        rebuild()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        content = vertical(ctx, ctx.dp(24))
        content.addView(ctx.bodyText(getString(R.string.car_loading)))
        return ScrollView(ctx).apply { addView(content) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rebuild()
    }

    fun rebuild() {
        val app = requireContext().applicationContext
        io.execute {
            CarFeatures.reset()
            CarBridge.devices(app)
            val bindings = CarFeatures.all.associate { it.id to CarFeatures.bind(app, it) }
            handler.post { if (isAdded) build(bindings) }
        }
    }

    private fun build(b: Map<String, Binding>) {
        val ctx = requireContext()
        content.removeAllViews()
        refreshers.clear()

        if (!CarBridge.isAvailable) {
            content.addView(MaterialCardView(ctx).apply {
                setCardBackgroundColor(0x33C62828)
                addView(ctx.bodyText(getString(R.string.car_not_found)).apply { setPadding(ctx.dp(20), ctx.dp(16), ctx.dp(20), ctx.dp(16)) })
            }, lp())
        }

        val state = ShizukuCar.status()
        if (CarBridge.isAvailable && !CarLink.connected) {
            val msg = when (state) {
                ShizukuCar.State.NO_SHIZUKU -> getString(R.string.shizuku_not_running)
                ShizukuCar.State.OLD -> getString(R.string.shizuku_old)
                ShizukuCar.State.NO_PERMISSION, ShizukuCar.State.BINDING -> getString(R.string.shizuku_connect_hint)
                else -> getString(R.string.shizuku_error)
            }
            val col = vertical(ctx, ctx.dp(20)).apply {
                addView(ctx.bodyText(getString(R.string.shizuku_title), 22f))
                addView(ctx.bodyText(msg, 16f).apply { alpha = 0.85f; setPadding(0, ctx.dp(6), 0, ctx.dp(6)) })
                addView(MaterialButton(ctx).apply {
                    text = getString(R.string.shizuku_connect); textSize = 20f; minHeight = ctx.dp(72)
                    setOnClickListener { ShizukuCar.connect(); rebuild() }
                })
            }
            content.addView(MaterialCardView(ctx).apply {
                setCardBackgroundColor(0x33FFA000); radius = ctx.dp(20).toFloat(); addView(col)
            }, lp())
        } else if (CarLink.connected) {
            content.addView(MaterialCardView(ctx).apply {
                setCardBackgroundColor(0x3343A047); radius = ctx.dp(20).toFloat()
                addView(ctx.bodyText("✔ " + getString(R.string.car_link_on, CarLink.mode.uppercase()), 18f).apply {
                    setPadding(ctx.dp(20), ctx.dp(14), ctx.dp(20), ctx.dp(14))
                })
            }, lp())
        }

        content.addView(ctx.sectionTitle(getString(R.string.sec_indicators)))
        val grid = GridLayout(ctx).apply { columnCount = 4 }
        CarFeatures.indicators.forEach { f -> grid.addView(indicatorCard(f, b.getValue(f.id))) }
        content.addView(grid, lp())

        content.addView(ctx.sectionTitle(getString(R.string.sec_ac_front)))
        CarFeatures.frontAc.forEach { content.addView(controlRow(it, b.getValue(it.id))) }

        content.addView(ctx.sectionTitle(getString(R.string.sec_ac_rear)))
        CarFeatures.rearAc.forEach { content.addView(controlRow(it, b.getValue(it.id))) }

        content.addView(ctx.sectionTitle(getString(R.string.sec_seats)))
        CarFeatures.seats.forEach { content.addView(controlRow(it, b.getValue(it.id))) }

        content.addView(ctx.bodyText(getString(R.string.car_footer), 14f).apply {
            setPadding(0, ctx.dp(24), 0, 0); alpha = 0.7f
        })
        startRefresh()
    }

    private fun format(f: Feature, b: Binding, v: Double?): String {
        if (v == null) return "—"
        b.labels[v.roundToInt().toString()]?.let { return it }
        f.levelLabels.getOrNull(v.roundToInt())?.let { return getString(it) }
        val n = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
        return if (f.unit.isEmpty()) n else "$n ${f.unit}"
    }

    private fun indicatorCard(f: Feature, b: Binding): View {
        val ctx = requireContext()
        val value = ctx.bodyText("—", 30f).apply { gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
        val label = ctx.bodyText(getString(f.label), 16f).apply { gravity = Gravity.CENTER; alpha = 0.75f }
        val card = MaterialCardView(ctx).apply {
            radius = ctx.dp(20).toFloat()
            addView(vertical(ctx, ctx.dp(16)).apply { addView(value); addView(label) })
            layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0; setMargins(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6)) }
            alpha = if (b.canRead) 1f else 0.45f
        }
        refreshers += { val v = b.read(); handler.post { value.text = format(f, b, v) } }
        return card
    }

    private fun controlRow(f: Feature, b: Binding): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ctx.dp(84)
        }
        val labelCol = vertical(ctx).apply {
            addView(ctx.bodyText(getString(f.label), 22f))
            if (!b.canWrite) addView(ctx.bodyText(getString(R.string.not_supported_yet), 14f).apply { alpha = 0.6f })
        }
        row.addView(labelCol, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val onError = { err: String? ->
            if (err != null) Toast.makeText(ctx, getString(R.string.car_cmd_failed, err), Toast.LENGTH_LONG).show()
        }

        when (f.kind) {
            Kind.TOGGLE -> {
                val sw = MaterialSwitch(ctx).apply { isEnabled = b.canWrite; scaleX = 1.4f; scaleY = 1.4f }
                var lastTouch = 0L
                sw.setOnCheckedChangeListener { v, checked ->
                    if (!v.isPressed) return@setOnCheckedChangeListener
                    lastTouch = SystemClock.uptimeMillis()
                    onError(b.write(if (checked) 1 else 0))
                }
                row.addView(sw)
                refreshers += {
                    if (SystemClock.uptimeMillis() - lastTouch > 2000) {
                        val v = b.read(); handler.post { v?.let { sw.isChecked = it != 0.0 } }
                    }
                }
            }
            Kind.LEVEL -> {
                var current = f.min
                var lastTouch = 0L
                val value = ctx.bodyText("—", 30f).apply {
                    gravity = Gravity.CENTER; minWidth = ctx.dp(110)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                fun btn(txt: String, delta: Int) = MaterialButton(ctx).apply {
                    text = txt; textSize = 30f; isEnabled = b.canWrite
                    minWidth = ctx.dp(84); minHeight = ctx.dp(72)
                    setOnClickListener {
                        current = (current + delta).coerceIn(f.min, f.max)
                        lastTouch = SystemClock.uptimeMillis()
                        value.text = format(f, b, current.toDouble())
                        onError(b.write(current))
                    }
                }
                row.addView(btn("−", -1))
                row.addView(value)
                row.addView(btn("+", +1))
                refreshers += {
                    if (SystemClock.uptimeMillis() - lastTouch > 2000) {
                        val v = b.read()
                        if (v != null) current = v.roundToInt().coerceIn(f.min, f.max)
                        handler.post { value.text = format(f, b, v) }
                    }
                }
            }
            Kind.READ -> Unit
        }
        return row
    }

    private val tick = object : Runnable {
        override fun run() {
            io.execute {
                refreshers.forEach { runCatching(it) }
                handler.postDelayed(this, 1200)
            }
        }
    }

    private fun startRefresh() {
        handler.removeCallbacks(tick)
        if (isVisible) handler.post(tick)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        if (hidden) handler.removeCallbacks(tick) else startRefresh()
    }

    override fun onResume() { super.onResume(); startRefresh() }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }
}

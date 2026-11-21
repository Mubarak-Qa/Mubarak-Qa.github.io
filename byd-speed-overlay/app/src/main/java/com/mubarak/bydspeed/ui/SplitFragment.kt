package com.mubarak.bydspeed.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mubarak.bydspeed.Displays
import com.mubarak.bydspeed.Prefs
import com.mubarak.bydspeed.R
import com.mubarak.bydspeed.split.Apps
import java.util.concurrent.Executors

/** Launch two apps side by side, or one app on another screen. */
class SplitFragment : Fragment() {

    private lateinit var prefs: Prefs
    private var apps: List<Apps.App> = emptyList()
    private lateinit var leftSlot: SlotView
    private lateinit var rightSlot: SlotView
    private lateinit var presets: ChipGroup
    private var otherScreenPkg = ""
    private lateinit var otherScreenSlot: SlotView

    private inner class SlotView(val title: String, var pkg: String, val onChange: (String) -> Unit) {
        val icon = ImageView(requireContext())
        val name = requireContext().bodyText("", 22f)
        val view: View = MaterialCardView(requireContext()).apply {
            val ctx = requireContext()
            radius = ctx.dp(20).toFloat()
            isClickable = true; isFocusable = true
            setOnClickListener { pickApp { p -> pkg = p; onChange(p); render() } }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(16))
                addView(ctx.bodyText(title, 16f).apply { alpha = 0.7f })
                addView(icon, LinearLayout.LayoutParams(ctx.dp(72), ctx.dp(72)).apply { topMargin = ctx.dp(8) })
                addView(name.apply { gravity = Gravity.CENTER })
            })
        }
        fun render() {
            val a = apps.firstOrNull { it.pkg == pkg }
            icon.setImageDrawable(a?.icon)
            name.text = a?.label ?: getString(R.string.split_choose)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        prefs = Prefs(ctx)
        val root = vertical(ctx, ctx.dp(24))

        root.addView(ctx.sectionTitle(getString(R.string.tab_split)))
        leftSlot = SlotView(getString(R.string.split_first), prefs.splitLeft) { prefs.splitLeft = it }
        rightSlot = SlotView(getString(R.string.split_second), prefs.splitRight) { prefs.splitRight = it }
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(leftSlot.view, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "⇄"; textSize = 32f
                setOnClickListener {
                    val t = leftSlot.pkg; leftSlot.pkg = rightSlot.pkg; rightSlot.pkg = t
                    prefs.splitLeft = leftSlot.pkg; prefs.splitRight = rightSlot.pkg
                    leftSlot.render(); rightSlot.render()
                }
            })
            addView(rightSlot.view, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })

        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, ctx.dp(16), 0, 0)
            addView(MaterialButton(ctx).apply {
                text = getString(R.string.split_launch); textSize = 24f; minHeight = ctx.dp(88)
                setOnClickListener { launch(leftSlot.pkg, rightSlot.pkg) }
            }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
            addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.split_save); textSize = 20f; minHeight = ctx.dp(88)
                setOnClickListener { savePreset() }
            }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(12) })
        })

        root.addView(ctx.sectionTitle(getString(R.string.split_presets)))
        presets = ChipGroup(ctx)
        root.addView(presets)

        // Open an app on another screen (e.g. passenger screen)
        root.addView(ctx.sectionTitle(getString(R.string.split_other_screen)))
        val screens = Displays.list(ctx)
        val spinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, screens.map { it.label })
            setSelection(screens.indexOfFirst { it.id == Displays.passenger(ctx)?.id }.coerceAtLeast(0))
            minimumHeight = ctx.dp(64)
        }
        otherScreenSlot = SlotView(getString(R.string.split_app), "") { otherScreenPkg = it }
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(otherScreenSlot.view, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(vertical(ctx, ctx.dp(12)).apply {
                addView(spinner)
                addView(MaterialButton(ctx).apply {
                    text = getString(R.string.split_open_there); textSize = 20f; minHeight = ctx.dp(72)
                    setOnClickListener {
                        val intent = Apps.launchIntent(ctx, otherScreenPkg) ?: return@setOnClickListener toast(getString(R.string.split_choose))
                        val s = screens.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
                        Displays.launchOn(ctx, intent, s.id)?.let { toast(getString(R.string.wp_failed, it)) }
                    }
                })
            }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        root.addView(ctx.bodyText(getString(R.string.split_note), 14f).apply { alpha = 0.6f; setPadding(0, ctx.dp(16), 0, 0) })

        Executors.newSingleThreadExecutor().execute {
            val list = Apps.list(ctx.applicationContext)
            Handler(Looper.getMainLooper()).post {
                if (!isAdded) return@post
                apps = list
                leftSlot.render(); rightSlot.render(); otherScreenSlot.render(); renderPresets()
            }
        }
        return ScrollView(ctx).apply { addView(root) }
    }

    private fun launch(a: String, b: String) {
        if (a.isEmpty() || b.isEmpty()) return toast(getString(R.string.split_choose_both))
        Apps.launchSplit(requireContext(), a, b)?.let { toast(getString(R.string.wp_failed, it)) }
    }

    private fun label(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

    private fun savePreset() {
        if (leftSlot.pkg.isEmpty() || rightSlot.pkg.isEmpty()) return toast(getString(R.string.split_choose_both))
        val entry = "${leftSlot.pkg}|${rightSlot.pkg}"
        val list = prefs.splitPresets.split(';').filter { it.isNotBlank() }.toMutableList()
        if (entry !in list) list += entry
        prefs.splitPresets = list.joinToString(";")
        renderPresets()
    }

    private fun renderPresets() {
        presets.removeAllViews()
        prefs.splitPresets.split(';').filter { it.contains('|') }.forEach { entry ->
            val (a, b) = entry.split('|')
            presets.addView(Chip(requireContext()).apply {
                text = "${label(a)} + ${label(b)}"; textSize = 18f
                chipMinHeight = requireContext().dp(56).toFloat()
                isCloseIconVisible = true
                setOnClickListener { launch(a, b) }
                setOnCloseIconClickListener {
                    prefs.splitPresets = prefs.splitPresets.split(';').filter { it != entry }.joinToString(";")
                    renderPresets()
                }
            })
        }
    }

    private fun pickApp(onPicked: (String) -> Unit) {
        val ctx = requireContext()
        if (apps.isEmpty()) return toast(getString(R.string.car_loading))
        val adapter = object : ArrayAdapter<Apps.App>(ctx, 0, apps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val a = getItem(position)!!
                val row = (convertView as? LinearLayout) ?: LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(ctx.dp(20), ctx.dp(10), ctx.dp(20), ctx.dp(10))
                    addView(ImageView(ctx), LinearLayout.LayoutParams(ctx.dp(56), ctx.dp(56)))
                    addView(ctx.bodyText("", 20f).apply { setPadding(ctx.dp(16), 0, ctx.dp(16), 0) })
                }
                (row.getChildAt(0) as ImageView).setImageDrawable(a.icon)
                (row.getChildAt(1) as TextView).text = a.label
                return row
            }
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.split_choose)
            .setAdapter(adapter) { _, i -> onPicked(apps[i].pkg) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
}

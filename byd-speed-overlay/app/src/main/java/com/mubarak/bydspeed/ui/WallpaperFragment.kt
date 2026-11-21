package com.mubarak.bydspeed.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.mubarak.bydspeed.Displays
import com.mubarak.bydspeed.R
import com.mubarak.bydspeed.wallpaper.PassengerWallpaperActivity
import com.mubarak.bydspeed.wallpaper.Wallpapers

/** Wallpapers for the driver's center screen and the passenger screen. */
class WallpaperFragment : Fragment() {

    private inner class Slot(val key: String) {
        lateinit var preview: ImageView
        var bitmap: Bitmap? = null
        fun set(b: Bitmap?) { bitmap = b; preview.setImageBitmap(b) }
    }

    private val center = Slot("center")
    private val passenger = Slot("passenger")
    private var pickingFor: Slot? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pickingFor ?: return@registerForActivityResult
        if (uri != null) slot.set(Wallpapers.store(requireContext(), slot.key, uri))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val root = vertical(ctx, ctx.dp(24))

        // Center screen
        root.addView(ctx.sectionTitle(getString(R.string.wp_center)))
        val lockBox = MaterialCheckBox(ctx).apply { text = getString(R.string.wp_also_lock); textSize = 18f }
        root.addView(slotCard(center, listOf(lockBox)) {
            val b = center.bitmap ?: return@slotCard toast(getString(R.string.wp_pick_first))
            toast(Wallpapers.applyToCenter(ctx, b, lockBox.isChecked)?.let { getString(R.string.wp_failed, it) }
                ?: getString(R.string.wp_applied))
        })

        // Passenger screen
        root.addView(ctx.sectionTitle(getString(R.string.wp_passenger)))
        val screens = Displays.list(ctx)
        val spinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, screens.map { it.label })
            val p = Displays.passenger(ctx)
            setSelection(screens.indexOfFirst { it.id == p?.id }.coerceAtLeast(0))
            minimumHeight = ctx.dp(64)
        }
        val screensHint = ctx.bodyText(
            if (screens.size < 2) getString(R.string.wp_one_screen) else getString(R.string.wp_choose_screen), 16f
        ).apply { alpha = 0.7f }
        root.addView(slotCard(passenger, listOf(screensHint, spinner)) {
            val b = passenger.bitmap ?: return@slotCard toast(getString(R.string.wp_pick_first))
            Wallpapers.store(ctx, passenger.key, b)
            val screen = screens.getOrNull(spinner.selectedItemPosition) ?: return@slotCard
            val intent = Intent(ctx, PassengerWallpaperActivity::class.java)
                .putExtra(PassengerWallpaperActivity.EXTRA_SLOT, passenger.key)
            toast(Displays.launchOn(ctx, intent, screen.id)?.let { getString(R.string.wp_failed, it) }
                ?: getString(R.string.wp_shown))
        })
        root.addView(ctx.bodyText(getString(R.string.wp_note), 14f).apply { alpha = 0.6f; setPadding(0, ctx.dp(16), 0, 0) })

        center.set(Wallpapers.load(ctx, center.key))
        passenger.set(Wallpapers.load(ctx, passenger.key))
        return ScrollView(ctx).apply { addView(root) }
    }

    private fun slotCard(slot: Slot, extras: List<View>, onApply: () -> Unit): View {
        val ctx = requireContext()
        val col = vertical(ctx, ctx.dp(16))
        slot.preview = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { setColor(0x22888888); cornerRadius = ctx.dp(16).toFloat() }
            clipToOutline = true
        }
        col.addView(slot.preview, lp(h = ctx.dp(220)))

        val presets = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, ctx.dp(12), 0, ctx.dp(12)) }
        Wallpapers.presets.forEach { (a, b) ->
            presets.addView(View(ctx).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(a, b)).apply { cornerRadius = ctx.dp(12).toFloat() }
                setOnClickListener { slot.set(Wallpapers.gradient(a, b)) }
            }, LinearLayout.LayoutParams(ctx.dp(110), ctx.dp(64)).apply { marginEnd = ctx.dp(10) })
        }
        col.addView(HorizontalScrollView(ctx).apply { addView(presets); isHorizontalScrollBarEnabled = false })
        extras.forEach { col.addView(it) }

        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, ctx.dp(8), 0, 0) }
        buttons.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.wp_pick); textSize = 20f; minHeight = ctx.dp(72)
            setOnClickListener { pickingFor = slot; picker.launch(arrayOf("image/*")) }
        }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(MaterialButton(ctx).apply {
            text = getString(R.string.wp_apply); textSize = 20f; minHeight = ctx.dp(72)
            setOnClickListener { onApply() }
        }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(12) })
        col.addView(buttons)

        return MaterialCardView(ctx).apply { radius = ctx.dp(24).toFloat(); addView(col) }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
}

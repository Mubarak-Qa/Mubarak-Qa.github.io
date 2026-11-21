package com.mubarak.bydspeed.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.mubarak.bydspeed.Prefs
import com.mubarak.bydspeed.R
import com.mubarak.bydspeed.SpeedOverlayService
import com.mubarak.bydspeed.WazeLauncher
import com.mubarak.bydspeed.speed.BydSpeedProvider

/** Floating speedometer over Waze. */
class SpeedFragment : Fragment(R.layout.fragment_speed) {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) toast(getString(R.string.perms_ok))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = Prefs(requireContext())
        status = view.findViewById(R.id.status)

        view.findViewById<View>(R.id.btnStartWaze).setOnClickListener {
            if (ensurePermissions()) {
                SpeedOverlayService.start(requireContext())
                if (!WazeLauncher.open(requireContext())) toast(getString(R.string.waze_missing))
            }
        }
        view.findViewById<View>(R.id.btnStartOnly).setOnClickListener {
            if (ensurePermissions()) SpeedOverlayService.start(requireContext())
            view.postDelayed(::refresh, 500)
        }
        view.findViewById<View>(R.id.btnStop).setOnClickListener {
            SpeedOverlayService.stop(requireContext()); view.postDelayed(::refresh, 300)
        }
        view.findViewById<View>(R.id.btnDiagnose).setOnClickListener {
            view.findViewById<TextView>(R.id.diagnostics).text = BydSpeedProvider.diagnose(requireContext())
        }

        val limitLabel = view.findViewById<TextView>(R.id.limitLabel)
        view.findViewById<Slider>(R.id.limitSlider).apply {
            value = (prefs.speedLimit.coerceIn(20, 200) / 5 * 5).toFloat()
            limitLabel.text = getString(R.string.limit_fmt, value.toInt())
            addOnChangeListener { _, v, _ ->
                prefs.speedLimit = v.toInt(); limitLabel.text = getString(R.string.limit_fmt, v.toInt())
            }
        }
        val sizeLabel = view.findViewById<TextView>(R.id.sizeLabel)
        view.findViewById<Slider>(R.id.sizeSlider).apply {
            value = (24 + ((prefs.sizeSp.coerceIn(24, 120) - 24) / 4) * 4).toFloat()
            sizeLabel.text = getString(R.string.size_fmt, value.toInt())
            addOnChangeListener { _, v, _ ->
                prefs.sizeSp = v.toInt(); sizeLabel.text = getString(R.string.size_fmt, v.toInt())
            }
        }
        view.findViewById<MaterialSwitch>(R.id.autoStart).apply {
            isChecked = prefs.autoStart
            setOnCheckedChangeListener { _, c -> prefs.autoStart = c }
        }
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        if (!isAdded) return
        status.text = getString(if (SpeedOverlayService.isRunning) R.string.status_on else R.string.status_off)
    }

    private fun ensurePermissions(): Boolean {
        val ctx = requireContext()
        if (!Settings.canDrawOverlays(ctx)) {
            toast(getString(R.string.need_overlay))
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            return false
        }
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) { permLauncher.launch(needed.toTypedArray()); return false }
        return true
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
}

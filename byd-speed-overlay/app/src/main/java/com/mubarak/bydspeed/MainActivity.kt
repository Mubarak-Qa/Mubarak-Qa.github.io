package com.mubarak.bydspeed

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.mubarak.bydspeed.speed.BydSpeedProvider

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.btnStartWaze).setOnClickListener {
            if (ensurePermissions()) {
                SpeedOverlayService.start(this)
                if (!WazeLauncher.open(this)) toast(getString(R.string.waze_missing))
            }
        }
        findViewById<Button>(R.id.btnStartOnly).setOnClickListener {
            if (ensurePermissions()) SpeedOverlayService.start(this)
            refresh()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            SpeedOverlayService.stop(this); refresh()
        }
        findViewById<Button>(R.id.btnDiagnose).setOnClickListener {
            findViewById<TextView>(R.id.diagnostics).text = BydSpeedProvider.diagnose(this)
        }

        val limitLabel = findViewById<TextView>(R.id.limitLabel)
        findViewById<SeekBar>(R.id.limitSeek).apply {
            max = 200
            progress = prefs.speedLimit
            limitLabel.text = getString(R.string.limit_fmt, progress)
            setOnSeekBarChangeListener(simpleSeek { v ->
                val value = v.coerceAtLeast(20)
                prefs.speedLimit = value
                limitLabel.text = getString(R.string.limit_fmt, value)
            })
        }
        val sizeLabel = findViewById<TextView>(R.id.sizeLabel)
        findViewById<SeekBar>(R.id.sizeSeek).apply {
            max = 120
            progress = prefs.sizeSp
            sizeLabel.text = getString(R.string.size_fmt, progress)
            setOnSeekBarChangeListener(simpleSeek { v ->
                val value = v.coerceAtLeast(24)
                prefs.sizeSp = value
                sizeLabel.text = getString(R.string.size_fmt, value)
            })
        }
        findViewById<CheckBox>(R.id.autoStart).apply {
            isChecked = prefs.autoStart
            setOnCheckedChangeListener { _, c -> prefs.autoStart = c }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        status.text = getString(
            if (SpeedOverlayService.isRunning) R.string.status_on else R.string.status_off
        )
    }

    /** Returns true when everything needed to show the overlay is granted. */
    private fun ensurePermissions(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.need_overlay))
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return false
        }
        val needed = buildList {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 10)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED })
            toast(getString(R.string.perms_ok))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}

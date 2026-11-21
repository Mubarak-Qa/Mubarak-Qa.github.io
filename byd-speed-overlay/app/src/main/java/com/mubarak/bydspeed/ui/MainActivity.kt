package com.mubarak.bydspeed.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.navigationrail.NavigationRailView
import com.mubarak.bydspeed.R

class MainActivity : AppCompatActivity() {

    private val tabs: Map<Int, () -> Fragment> = mapOf(
        R.id.nav_car to ::CarFragment,
        R.id.nav_speed to ::SpeedFragment,
        R.id.nav_radio to ::RadioFragment,
        R.id.nav_wallpaper to ::WallpaperFragment,
        R.id.nav_split to ::SplitFragment,
        R.id.nav_settings to ::SettingsFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val rail = findViewById<NavigationRailView>(R.id.rail)
        rail.setOnItemSelectedListener { show(it.itemId); true }
        if (savedInstanceState == null) {
            rail.selectedItemId = R.id.nav_car
            show(R.id.nav_car)
        }
    }

    /** Keeps each tab alive (show/hide) so e.g. the radio list isn't reloaded on every switch. */
    private fun show(id: Int) {
        val fm = supportFragmentManager
        val tag = "tab_$id"
        val tx = fm.beginTransaction()
        fm.fragments.forEach { if (it.tag != tag) tx.hide(it) }
        val existing = fm.findFragmentByTag(tag)
        if (existing != null) tx.show(existing) else tx.add(R.id.container, tabs.getValue(id)(), tag)
        tx.commit()
    }
}

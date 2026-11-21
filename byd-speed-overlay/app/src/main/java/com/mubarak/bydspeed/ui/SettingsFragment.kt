package com.mubarak.bydspeed.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mubarak.bydspeed.App
import com.mubarak.bydspeed.BuildConfig
import com.mubarak.bydspeed.Prefs
import com.mubarak.bydspeed.R
import com.mubarak.bydspeed.UpdateChecker
import com.mubarak.bydspeed.car.CarBridge
import com.mubarak.bydspeed.car.CarFeatures
import org.json.JSONObject
import java.util.concurrent.Executors

class SettingsFragment : Fragment() {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val prefs = Prefs(ctx)
        val root = vertical(ctx, ctx.dp(24))

        // Language
        root.addView(ctx.sectionTitle(getString(R.string.set_language)))
        val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2)
        root.addView(toggleGroup(
            listOf("" to getString(R.string.set_system), "ar" to "العربية", "en" to "English"), currentLang
        ) { tag ->
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            )
        })

        // Theme
        root.addView(ctx.sectionTitle(getString(R.string.set_theme)))
        root.addView(toggleGroup(
            listOf("system" to getString(R.string.set_system), "light" to getString(R.string.set_light), "dark" to getString(R.string.set_dark)),
            prefs.theme
        ) { tag -> prefs.theme = tag; App.applyTheme(tag) })

        // Updates
        root.addView(ctx.sectionTitle(getString(R.string.set_updates)))
        root.addView(ctx.bodyText(getString(R.string.set_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)))
        root.addView(MaterialButton(ctx).apply {
            text = getString(R.string.set_check_updates); textSize = 20f; minHeight = ctx.dp(72)
            setOnClickListener { checkUpdates() }
        }, lp().apply { topMargin = ctx.dp(8) })

        // Car developer tools
        root.addView(ctx.sectionTitle(getString(R.string.set_dev)))
        root.addView(ctx.bodyText(getString(R.string.set_dev_hint), 16f).apply { alpha = 0.75f })
        val report = ctx.bodyText("", 14f).apply {
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true); textDirection = View.TEXT_DIRECTION_LTR
        }
        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, ctx.dp(8), 0, ctx.dp(8)) }
        buttons.addView(MaterialButton(ctx).apply {
            text = getString(R.string.set_scan); textSize = 18f; minHeight = ctx.dp(72)
            setOnClickListener {
                report.text = getString(R.string.car_loading)
                io.execute {
                    val r = runCatching { CarBridge.scanReport(ctx.applicationContext) }.getOrElse { it.toString() }
                    main.post { if (isAdded) report.text = r }
                }
            }
        }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.set_copy); textSize = 18f; minHeight = ctx.dp(72)
            setOnClickListener {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("scan", report.text))
                toast(getString(R.string.set_copied))
            }
        }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(8) })
        buttons.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.set_share); textSize = 18f; minHeight = ctx.dp(72)
            setOnClickListener {
                runCatching {
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report.text.toString()), null))
                }
            }
        }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(8) })
        root.addView(buttons)
        root.addView(report)

        root.addView(ctx.bodyText(getString(R.string.set_mapping), 18f).apply { setPadding(0, ctx.dp(16), 0, ctx.dp(4)) })
        val mapping = EditText(ctx).apply {
            setText(prefs.carMapping)
            hint = """{"ac_fan": {"set":"setAcWindLevel","setArgs":[1,"v"]}}"""
            typeface = Typeface.MONOSPACE; textSize = 14f; minLines = 4
            textDirection = View.TEXT_DIRECTION_LTR
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        root.addView(mapping)
        root.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(MaterialButton(ctx).apply {
                text = getString(R.string.set_save); textSize = 18f; minHeight = ctx.dp(64)
                setOnClickListener {
                    val txt = mapping.text.toString().trim()
                    if (txt.isNotEmpty() && runCatching { JSONObject(txt) }.isFailure)
                        return@setOnClickListener toast(getString(R.string.set_bad_json))
                    prefs.carMapping = txt; CarFeatures.reset(); refreshCarTab()
                    toast(getString(R.string.set_saved))
                }
            }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = getString(R.string.set_reset); textSize = 18f; minHeight = ctx.dp(64)
                setOnClickListener {
                    mapping.setText(""); prefs.carMapping = ""; CarFeatures.reset(); refreshCarTab()
                    toast(getString(R.string.set_saved))
                }
            }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(8) })
        })

        return ScrollView(ctx).apply { addView(root) }
    }

    private fun refreshCarTab() {
        parentFragmentManager.fragments.filterIsInstance<CarFragment>().forEach { it.rebuild() }
    }

    private fun toggleGroup(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit): View {
        val ctx = requireContext()
        val group = MaterialButtonToggleGroup(ctx).apply { isSingleSelection = true; isSelectionRequired = true }
        options.forEach { (tag, label) ->
            group.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = View.generateViewId(); this.tag = tag; text = label; textSize = 20f; minHeight = ctx.dp(72)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        (0 until group.childCount).map { group.getChildAt(it) }.firstOrNull { it.tag == selected }?.let { group.check(it.id) }
            ?: group.check(group.getChildAt(0).id)
        group.addOnButtonCheckedListener { g, id, checked ->
            if (checked) onSelect(g.findViewById<View>(id).tag as String)
        }
        return group
    }

    private fun checkUpdates() {
        toast(getString(R.string.set_checking))
        io.execute {
            val r = runCatching { UpdateChecker.latest() }
            main.post {
                if (!isAdded) return@post
                r.onFailure { toast(getString(R.string.set_update_failed)) }
                r.onSuccess { rel ->
                    if (rel.code <= BuildConfig.VERSION_CODE) toast(getString(R.string.set_up_to_date))
                    else MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.set_update_available, rel.name))
                        .setMessage(rel.notes)
                        .setPositiveButton(R.string.set_download) { _, _ ->
                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rel.apkUrl))) }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
}

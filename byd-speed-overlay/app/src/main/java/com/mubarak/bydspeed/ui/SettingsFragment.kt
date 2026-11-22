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
import com.mubarak.bydspeed.car.ShizukuCar
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

        // Shizuku (shell-level car access)
        root.addView(ctx.sectionTitle(getString(R.string.shizuku_title)))
        val shizukuStatus = ctx.bodyText("", 18f)
        fun refreshShizuku() {
            shizukuStatus.text = when (ShizukuCar.status()) {
                ShizukuCar.State.CONNECTED -> "✔ " + getString(R.string.shizuku_connected)
                ShizukuCar.State.NO_SHIZUKU -> getString(R.string.shizuku_not_running)
                ShizukuCar.State.OLD -> getString(R.string.shizuku_old)
                ShizukuCar.State.NO_PERMISSION -> getString(R.string.shizuku_no_perm)
                ShizukuCar.State.BINDING -> getString(R.string.shizuku_binding)
                else -> getString(R.string.shizuku_error)
            }
        }
        refreshShizuku()
        root.addView(ctx.bodyText(getString(R.string.shizuku_hint), 15f).apply { alpha = 0.75f })
        root.addView(shizukuStatus.apply { setPadding(0, ctx.dp(8), 0, ctx.dp(8)) })
        root.addView(MaterialButton(ctx).apply {
            text = getString(R.string.shizuku_connect); textSize = 20f; minHeight = ctx.dp(72)
            setOnClickListener { ShizukuCar.connect(); main.postDelayed({ refreshShizuku(); refreshCarTab() }, 800) }
        }, lp())
        ShizukuCar.onChange = { main.post { refreshShizuku(); refreshCarTab() } }

        // Car developer tools
        root.addView(ctx.sectionTitle(getString(R.string.set_dev)))
        root.addView(ctx.bodyText(getString(R.string.set_dev_hint), 16f).apply { alpha = 0.75f })
        val report = ctx.bodyText("", 14f).apply {
            typeface = Typeface.MONOSPACE; setTextIsSelectable(true); textDirection = View.TEXT_DIRECTION_LTR
        }
        var parts: List<String> = emptyList()
        var nextPart = 0
        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, ctx.dp(8), 0, ctx.dp(8)) }
        lateinit var copyBtn: MaterialButton
        buttons.addView(MaterialButton(ctx).apply {
            text = getString(R.string.set_scan); textSize = 18f; minHeight = ctx.dp(72)
            setOnClickListener {
                report.text = getString(R.string.car_loading)
                io.execute {
                    val r = ShizukuCar.scan()
                        ?: runCatching { CarBridge.scanReport(ctx.applicationContext) }.getOrElse { it.toString() }
                    main.post {
                        if (!isAdded) return@post
                        report.text = r
                        parts = CarBridge.split(r); nextPart = 0
                        copyBtn.text = getString(R.string.set_copy_part, 1, parts.size)
                    }
                }
            }
        }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        copyBtn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.set_copy); textSize = 18f; minHeight = ctx.dp(72)
            setOnClickListener {
                if (parts.isEmpty()) parts = CarBridge.split(report.text.toString())
                if (nextPart >= parts.size) nextPart = 0
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("scan", parts[nextPart]))
                toast(getString(R.string.set_copied_part, nextPart + 1, parts.size))
                nextPart++
                text = if (nextPart < parts.size) getString(R.string.set_copy_part, nextPart + 1, parts.size)
                    else getString(R.string.set_copy)
            }
        }
        buttons.addView(copyBtn, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = ctx.dp(8) })
        buttons.addView(MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.set_save_file); textSize = 18f; minHeight = ctx.dp(72)
            setOnClickListener {
                val where = runCatching { saveReport(ctx, report.text.toString()) }.getOrElse { CarBridge.describe(it) }
                toast(getString(R.string.set_saved_to, where))
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

    /** Saves the report to Download/byd_scan.txt (visible from a USB file manager). */
    private fun saveReport(ctx: Context, text: String): String {
        val name = "byd_scan.txt"
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("insert failed")
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            return "Download/$name"
        }
        val f = java.io.File(ctx.getExternalFilesDir(null), name)
        f.writeText(text)
        return f.path
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

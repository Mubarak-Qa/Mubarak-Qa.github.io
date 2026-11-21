package com.mubarak.bydspeed.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.mubarak.bydspeed.Prefs
import com.mubarak.bydspeed.R
import com.mubarak.bydspeed.radio.RadioService
import com.mubarak.bydspeed.radio.Station
import com.mubarak.bydspeed.radio.StationRepository
import java.util.concurrent.Executors

/** Gulf internet radio, grouped by country, with favorites. */
class RadioFragment : Fragment(R.layout.fragment_radio) {

    private val countries = listOf(
        "QA" to R.string.country_qa, "SA" to R.string.country_sa, "AE" to R.string.country_ae,
        "KW" to R.string.country_kw, "BH" to R.string.country_bh, "OM" to R.string.country_om,
    )
    private val FAV = "FAV"

    private lateinit var repo: StationRepository
    private lateinit var prefs: Prefs
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var all: List<Station> = emptyList()
    private var query = ""
    private val adapter = Adapter()
    private lateinit var message: TextView
    private lateinit var nowTitle: TextView
    private lateinit var nowState: TextView
    private lateinit var toggle: MaterialButton
    private val stateListener: () -> Unit = { renderPlayer() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = StationRepository(requireContext())
        prefs = Prefs(requireContext())
        message = view.findViewById(R.id.message)
        nowTitle = view.findViewById(R.id.nowTitle)
        nowState = view.findViewById(R.id.nowState)
        toggle = view.findViewById(R.id.btnToggle)

        val group = view.findViewById<ChipGroup>(R.id.countries)
        (countries.map { it.first to getString(it.second) } + (FAV to getString(R.string.radio_favorites))).forEach { (cc, label) ->
            group.addView(Chip(requireContext()).apply {
                text = label; tag = cc; isCheckable = true; textSize = 20f
                chipMinHeight = requireContext().dp(56).toFloat()
                id = View.generateViewId()
                isChecked = cc == prefs.radioCountry
            })
        }
        group.setOnCheckedStateChangeListener { g, ids ->
            val cc = ids.firstOrNull()?.let { g.findViewById<Chip>(it).tag as String } ?: return@setOnCheckedStateChangeListener
            prefs.radioCountry = cc
            load(cc)
        }

        view.findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RadioFragment.adapter
        }
        view.findViewById<TextView>(R.id.search).doAfterTextChanged { query = it?.toString().orEmpty(); render() }
        toggle.setOnClickListener { RadioService.toggle(requireContext()) }
        view.findViewById<View>(R.id.btnStopRadio).setOnClickListener { RadioService.stop(requireContext()) }

        load(prefs.radioCountry)
    }

    override fun onStart() { super.onStart(); RadioService.State.listeners += stateListener; renderPlayer() }
    override fun onStop() { super.onStop(); RadioService.State.listeners -= stateListener }

    private fun load(cc: String) {
        if (cc == FAV) { all = repo.favorites(); showMessage(if (all.isEmpty()) getString(R.string.radio_no_favs) else null); render(); return }
        all = repo.cached(cc)
        render()
        showMessage(getString(R.string.radio_loading))
        io.execute {
            val result = runCatching { repo.fetch(cc) }
            main.post {
                if (!isAdded || prefs.radioCountry != cc) return@post
                result.onSuccess { all = it; showMessage(if (it.isEmpty()) getString(R.string.radio_empty) else null); render() }
                    .onFailure { showMessage(getString(if (all.isEmpty()) R.string.radio_offline_empty else R.string.radio_offline_cached)) }
            }
        }
    }

    private fun showMessage(text: String?) {
        message.text = text
        message.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    private fun render() {
        val q = query.trim()
        adapter.items = if (q.isEmpty()) all else all.filter { it.name.contains(q, true) || it.tags.contains(q, true) }
        adapter.notifyDataSetChanged()
    }

    private fun renderPlayer() {
        if (!isAdded) return
        val st = RadioService.State
        nowTitle.text = st.station?.name ?: getString(R.string.radio_nothing)
        nowState.text = when {
            st.error != null -> getString(R.string.radio_error, st.error)
            st.buffering -> getString(R.string.radio_buffering)
            st.playing -> getString(R.string.radio_playing)
            st.station != null -> getString(R.string.radio_paused)
            else -> ""
        }
        toggle.text = if (st.playing) "❚❚" else "▶"
        adapter.notifyDataSetChanged()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        var items: List<Station> = emptyList()

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.name)
            val meta: TextView = v.findViewById(R.id.meta)
            val fav: MaterialButton = v.findViewById(R.id.fav)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_station, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val s = items[position]
            val current = RadioService.State.station?.id == s.id
            h.name.text = if (current) "♪ ${s.name}" else s.name
            h.name.setTypeface(null, if (current) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            h.meta.text = listOfNotNull(
                s.codec.takeIf { it.isNotBlank() && it != "UNKNOWN" },
                s.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
                s.tags.split(',').take(3).joinToString(" · ").takeIf { it.isNotBlank() },
            ).joinToString(" | ")
            h.fav.text = if (repo.isFavorite(s)) "★" else "☆"
            h.fav.setOnClickListener {
                repo.toggleFavorite(s)
                if (prefs.radioCountry == FAV) load(FAV) else notifyItemChanged(h.bindingAdapterPosition)
            }
            h.itemView.setOnClickListener { RadioService.play(requireContext(), s) }
        }
    }
}

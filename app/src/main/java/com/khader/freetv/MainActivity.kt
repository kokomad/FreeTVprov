package com.khader.freetv

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.khader.freetv.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var favs: Favorites
    private lateinit var adapter: ChannelAdapter
    private var current: List<Channel> = emptyList()
    private var section: Section = Sections.all[1]
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        favs = Favorites(this)

        adapter = ChannelAdapter(
            isFav = { favs.has(it.url) },
            onClick = {
                startActivity(Intent(this, PlayerActivity::class.java)
                    .putExtra("name", it.name).putExtra("url", it.url))
            },
            onFav = {
                favs.toggle(it)
                if (section.url == null) { current = favs.all(); render() } else adapter.notifyDataSetChanged()
            }
        )
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter

        var first: Chip? = null
        Sections.all.forEach { s ->
            val chip = Chip(this).apply {
                text = s.title
                isCheckable = true
                setOnCheckedChangeListener { _, checked -> if (checked) select(s) }
            }
            b.chips.addView(chip)
            if (s === Sections.all[1]) first = chip
        }
        b.search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; render() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
        })
        b.retry.setOnClickListener { select(section) }
        first?.isChecked = true
    }

    private fun select(s: Section) {
        section = s
        b.retry.visibility = View.GONE
        b.status.visibility = View.GONE
        val url = s.url
        if (url == null) { current = favs.all(); render(); return }
        b.progress.visibility = View.VISIBLE
        current = emptyList(); render()
        lifecycleScope.launch {
            try {
                val list = Repo.get(url)
                if (section !== s) return@launch
                current = list
                b.progress.visibility = View.GONE
                render()
            } catch (e: Exception) {
                if (section !== s) return@launch
                b.progress.visibility = View.GONE
                b.status.text = "تعذّر تحميل القنوات. تحقق من الإنترنت."
                b.status.visibility = View.VISIBLE
                b.retry.visibility = View.VISIBLE
            }
        }
    }

    private fun render() {
        val list = if (query.isEmpty()) current else current.filter { it.name.contains(query, true) }
        adapter.submit(list)
        if (b.progress.visibility != View.VISIBLE && b.retry.visibility != View.VISIBLE) {
            if (list.isEmpty()) {
                b.status.text = if (section.url == null) "لا توجد قنوات مفضلة بعد — اضغط ♡ بجانب أي قناة" else "لا توجد نتائج"
                b.status.visibility = View.VISIBLE
            } else b.status.visibility = View.GONE
        }
    }
}

class ChannelAdapter(
    private val isFav: (Channel) -> Boolean,
    private val onClick: (Channel) -> Unit,
    private val onFav: (Channel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    private var items: List<Channel> = emptyList()

    fun submit(list: List<Channel>) { items = list; notifyDataSetChanged() }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val group: TextView = v.findViewById(R.id.group)
        val fav: TextView = v.findViewById(R.id.fav)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_channel, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val c = items[i]
        h.name.text = c.name
        h.group.text = c.group
        h.fav.text = if (isFav(c)) "♥" else "♡"
        h.logo.load(c.logo.ifEmpty { null }) {
            placeholder(ColorDrawable(0xFF2A2A2F.toInt()))
            error(ColorDrawable(0xFF2A2A2F.toInt()))
        }
        h.itemView.setOnClickListener { onClick(c) }
        h.fav.setOnClickListener { onFav(c) }
    }
}

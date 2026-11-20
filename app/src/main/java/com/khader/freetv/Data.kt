package com.khader.freetv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class Channel(val name: String, val url: String, val logo: String, val group: String)

data class Section(val title: String, val url: String?)

object Sections {
    private const val B = "https://iptv-org.github.io/iptv"
    val all = listOf(
        Section("⭐ المفضلة", null),
        Section("🌍 عربية", "$B/languages/ara.m3u"),
        Section("📰 أخبار", "$B/categories/news.m3u"),
        Section("⚽ رياضة", "$B/categories/sports.m3u"),
        Section("🎬 أفلام", "$B/categories/movies.m3u"),
        Section("🎭 منوعات", "$B/categories/entertainment.m3u"),
        Section("🧒 أطفال", "$B/categories/kids.m3u"),
        Section("🎵 موسيقى", "$B/categories/music.m3u"),
        Section("🔬 وثائقي", "$B/categories/documentary.m3u"),
        Section("🕌 ديني", "$B/categories/religious.m3u"),
        Section("🇬🇧 English", "$B/languages/eng.m3u"),
        Section("🇫🇷 Français", "$B/languages/fra.m3u"),
        Section("🇹🇷 Türkçe", "$B/languages/tur.m3u"),
        Section("🇪🇸 Español", "$B/languages/spa.m3u"),
        Section("🇩🇪 Deutsch", "$B/languages/deu.m3u")
    )
}

object M3u {
    private val logoRe = Regex("""tvg-logo="([^"]*)"""")
    private val groupRe = Regex("""group-title="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""; var logo = ""; var group = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                name = line.substringAfterLast(",").trim()
                logo = logoRe.find(line)?.groupValues?.get(1) ?: ""
                group = groupRe.find(line)?.groupValues?.get(1) ?: ""
            } else if (line.isNotEmpty() && !line.startsWith("#") && name.isNotEmpty()) {
                out.add(Channel(name, line, logo, group))
                name = ""; logo = ""; group = ""
            }
        }
        return out.distinctBy { it.url }.sortedBy { it.name.lowercase() }
    }
}

object Repo {
    private val cache = HashMap<String, List<Channel>>()

    suspend fun get(url: String): List<Channel> {
        cache[url]?.let { return it }
        val list = withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 25000
            try { M3u.parse(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
        }
        if (list.isNotEmpty()) cache[url] = list
        return list
    }
}

class Favorites(ctx: Context) {
    private val sp = ctx.getSharedPreferences("favs", Context.MODE_PRIVATE)
    private val sep = "\u0001"

    fun all(): List<Channel> = (sp.getStringSet("items", emptySet()) ?: emptySet()).mapNotNull {
        val p = it.split(sep)
        if (p.size >= 4) Channel(p[0], p[1], p[2], p[3]) else null
    }.sortedBy { it.name.lowercase() }

    fun has(url: String) = all().any { it.url == url }

    fun toggle(c: Channel) {
        val set = (sp.getStringSet("items", emptySet()) ?: emptySet()).toMutableSet()
        val existing = set.firstOrNull { it.split(sep).getOrNull(1) == c.url }
        if (existing != null) set.remove(existing) else set.add(listOf(c.name, c.url, c.logo, c.group).joinToString(sep))
        sp.edit().putStringSet("items", set).apply()
    }
}

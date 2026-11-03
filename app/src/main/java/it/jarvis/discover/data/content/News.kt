package it.jarvis.discover.data.content

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.jarvis.discover.data.youtube.HttpFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Una testata da cui leggere via RSS pubblico. */
data class NewsSource(val id: String, val name: String, val feedUrl: String, val about: String, val group: NewsGroup)

enum class NewsGroup(val label: String) {
    Positive("Notizie positive"),
    Science("Scienza e curiosità"),
}

/**
 * Testate verificate (RSS pubblico leggibile, in italiano). L'app non giudica le notizie: le
 * etichette dicono cosa si propone ciascuna testata. Si mostra titolo, anteprima e foto come
 * fornite dal feed; l'articolo completo si apre sul sito dell'editore.
 */
val NewsSources = listOf(
    NewsSource("icc", "Italia che Cambia", "https://www.italiachecambia.org/feed/", "Giornalismo costruttivo: storie di cambiamento positivo in Italia", NewsGroup.Positive),
    NewsSource("vita", "Vita", "https://www.vita.it/feed/", "Terzo settore, impegno sociale e innovazione", NewsGroup.Positive),
    NewsSource("lifegate", "LifeGate", "https://www.lifegate.it/feed", "Sostenibilità, ambiente e cultura", NewsGroup.Positive),
    NewsSource("galileo", "Galileo", "https://www.galileonet.it/feed/", "Notizie e divulgazione scientifica", NewsGroup.Science),
    NewsSource("focus", "Focus", "https://www.focus.it/rss", "Scienza, tecnologia e curiosità", NewsGroup.Science),
)

data class NewsArticle(val entry: FeedEntry, val sourceId: String, val sourceName: String, val group: NewsGroup)

enum class NewsStatus { Idle, Loading, Ready, Failed }

/** Notizie reali dai feed RSS, con cache di 30 minuti e tolleranza ai singoli feed non raggiungibili. */
class NewsRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val http: HttpFetcher = TextFetcher,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.applicationContext.getSharedPreferences("discover_news", Context.MODE_PRIVATE)

    val articles = mutableStateListOf<NewsArticle>()
    var status by mutableStateOf(NewsStatus.Idle); private set
    /** Testate che non hanno risposto all'ultimo aggiornamento. */
    var failedSources by mutableStateOf<List<String>>(emptyList()); private set
    var updatedAt by mutableStateOf(0L); private set

    init {
        readCache()?.let { (time, list) ->
            articles.addAll(list); updatedAt = time
            status = NewsStatus.Ready
        }
    }

    fun refresh(force: Boolean = false) {
        if (status == NewsStatus.Loading) return
        if (!force && articles.isNotEmpty() && now() - updatedAt < TTL_MS) return
        status = NewsStatus.Loading
        scope.launch {
            val results = withContext(Dispatchers.IO) {
                NewsSources.map { src ->
                    async {
                        runCatching {
                            val r = http.get(src.feedUrl)
                            if (r.code !in 200..299) null else FeedParser.parse(r.body, 25).map { NewsArticle(it, src.id, src.name, src.group) }
                        }.getOrNull()
                    }
                }.awaitAll()
            }
            failedSources = NewsSources.filterIndexed { i, _ -> results[i] == null }.map { it.name }
            val merged = interleave(results.filterNotNull())
            if (merged.isNotEmpty()) {
                articles.clear(); articles.addAll(merged)
                updatedAt = now()
                writeCache(updatedAt, merged)
                status = NewsStatus.Ready
            } else {
                status = if (articles.isEmpty()) NewsStatus.Failed else NewsStatus.Ready
            }
        }
    }

    private fun readCache(): Pair<Long, List<NewsArticle>>? = runCatching {
        val o = JSONObject(prefs.getString("cache", null) ?: return null)
        val arr = o.getJSONArray("items")
        o.getLong("t") to (0 until arr.length()).map { i ->
            val a = arr.getJSONObject(i)
            NewsArticle(
                FeedEntry(
                    a.getString("id"), a.getString("title"), a.getString("link"), a.optString("summary"),
                    a.optString("img").ifBlank { null }, a.optLong("at"), null, 0,
                ),
                a.getString("src"), a.getString("srcName"), runCatching { NewsGroup.valueOf(a.getString("group")) }.getOrDefault(NewsGroup.Positive),
            )
        }
    }.getOrNull()

    private fun writeCache(time: Long, list: List<NewsArticle>) {
        val arr = JSONArray()
        list.take(120).forEach { n ->
            arr.put(
                JSONObject().put("id", n.entry.id).put("title", n.entry.title).put("link", n.entry.link)
                    .put("summary", n.entry.summary).put("img", n.entry.imageUrl ?: "").put("at", n.entry.publishedMillis)
                    .put("src", n.sourceId).put("srcName", n.sourceName).put("group", n.group.name),
            )
        }
        prefs.edit().putString("cache", JSONObject().put("t", time).put("items", arr).toString()).apply()
    }

    companion object {
        const val TTL_MS = 30 * 60 * 1000L

        /** Alterna le testate, mantenendo per ciascuna l'ordine di pubblicazione più recente prima. */
        fun interleave(perSource: List<List<NewsArticle>>): List<NewsArticle> {
            val sorted = perSource.map { l -> l.sortedByDescending { it.entry.publishedMillis } }
            val out = ArrayList<NewsArticle>()
            val seen = HashSet<String>()
            var i = 0
            while (sorted.any { i < it.size }) {
                sorted.forEach { l -> l.getOrNull(i)?.let { a -> if (a.entry.link.isNotBlank() && seen.add(a.entry.link)) out += a } }
                i++
            }
            return out
        }
    }
}

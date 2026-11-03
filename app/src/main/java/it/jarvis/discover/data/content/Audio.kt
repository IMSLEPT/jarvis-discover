package it.jarvis.discover.data.content

import it.jarvis.discover.data.youtube.HttpFetcher
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

// ---------------------------------------------------------------------------- podcast (iTunes + RSS)

data class Podcast(val id: String, val title: String, val author: String, val artwork: String, val feedUrl: String, val genre: String)

/** Un brano riproducibile: episodio di podcast o capitolo di audiolibro. */
data class AudioItem(
    val id: String,
    val title: String,
    /** Nome di podcast o libro, mostrato sotto il titolo. */
    val subtitle: String,
    val url: String,
    val artwork: String?,
    val durationSec: Int,
    val publishedMillis: Long = 0,
    val summary: String = "",
)

sealed interface AudioResult<out T> {
    data class Ok<T>(val value: T) : AudioResult<T>
    data class Err(val message: String) : AudioResult<Nothing>
}

/**
 * Ricerca podcast con la Search API di Apple (gratuita, senza chiave, circa 20 richieste al minuto) e
 * lettura dei feed RSS originali. L'audio viene riprodotto dal server dell'editore: l'app non lo copia.
 */
class PodcastClient(private val http: HttpFetcher = TextFetcher) {

    fun search(term: String, limit: Int = 25): AudioResult<List<Podcast>> {
        val url = "https://itunes.apple.com/search?media=podcast&country=IT&limit=$limit&term=" + URLEncoder.encode(term.trim(), "UTF-8")
        return guarded("Apple Podcasts") { parseSearch(http.get(url).also { check(it.code in 200..299) { "codice ${it.code}" } }.body) }
    }

    fun episodes(podcast: Podcast, limit: Int = 40): AudioResult<List<AudioItem>> =
        guarded("il feed del podcast") {
            val r = http.get(podcast.feedUrl)
            check(r.code in 200..299) { "codice ${r.code}" }
            FeedParser.parse(r.body, limit).mapNotNull { e ->
                val audio = e.audioUrl ?: return@mapNotNull null
                if (!audio.startsWith("https://")) return@mapNotNull null // si riproduce solo audio cifrato (https)
                AudioItem(
                    id = podcast.id + ":" + e.id, title = e.title, subtitle = podcast.title, url = audio,
                    artwork = e.imageUrl ?: podcast.artwork.ifBlank { null }, durationSec = e.durationSec,
                    publishedMillis = e.publishedMillis, summary = e.summary,
                )
            }
        }

    private fun <T> guarded(what: String, block: () -> T): AudioResult<T> = try {
        AudioResult.Ok(block())
    } catch (e: IOException) {
        AudioResult.Err("Non riesco a collegarmi a $what. Controlla la connessione.")
    } catch (e: IllegalStateException) {
        AudioResult.Err("$what non ha risposto correttamente (${e.message}).")
    }

    companion object {
        fun parseSearch(json: String): List<Podcast> {
            val arr = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
            val out = ArrayList<Podcast>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val feed = o.optString("feedUrl")
                val title = o.optString("collectionName").ifBlank { o.optString("trackName") }
                if (!feed.startsWith("https://") || title.isBlank()) continue // senza feed https non si può ascoltare
                out += Podcast(
                    id = o.optLong("collectionId", 0).toString().takeIf { it != "0" } ?: feed,
                    title = title,
                    author = o.optString("artistName"),
                    artwork = o.optString("artworkUrl600").ifBlank { o.optString("artworkUrl100") },
                    feedUrl = feed,
                    genre = o.optString("primaryGenreName"),
                )
            }
            return out
        }
    }
}

// ---------------------------------------------------------------------------- audiolibri (LibriVox su Internet Archive)

data class Audiobook(val id: String, val title: String, val author: String) {
    val pageUrl: String get() = "https://archive.org/details/$id"
}

data class AudiobookDetail(val book: Audiobook, val description: String, val tracks: List<AudioItem>)

/**
 * Audiolibri LibriVox in italiano, letti da volontari e di pubblico dominio (collezione "librivoxaudio"
 * di Internet Archive). Sono un catalogo piccolo (decine di titoli) e le voci sono di volontari: la
 * qualità varia. Non si distribuisce nulla di protetto da copyright.
 */
class AudiobookClient(private val http: HttpFetcher = TextFetcher) {

    fun list(query: String = "", page: Int = 1, rows: Int = 50): AudioResult<List<Audiobook>> {
        val base = "collection:librivoxaudio AND language:(ita OR Italian)"
        val q = if (query.isBlank()) base else "$base AND (title:(${sanitize(query)}) OR creator:(${sanitize(query)}))"
        val url = "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(q, "UTF-8") +
            "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&sort%5B%5D=" + URLEncoder.encode("title asc", "UTF-8") +
            "&rows=$rows&page=$page&output=json"
        return try {
            val r = http.get(url)
            if (r.code !in 200..299) AudioResult.Err("Internet Archive non risponde (${r.code}).") else AudioResult.Ok(parseList(r.body))
        } catch (e: IOException) {
            AudioResult.Err("Non riesco a collegarmi a Internet Archive. Controlla la connessione.")
        }
    }

    fun detail(book: Audiobook): AudioResult<AudiobookDetail> {
        if (!ID.matches(book.id)) return AudioResult.Err("Identificativo non valido.")
        return try {
            val r = http.get("https://archive.org/metadata/${book.id}")
            if (r.code !in 200..299) AudioResult.Err("Internet Archive non risponde (${r.code}).")
            else AudioResult.Ok(parseDetail(book, r.body))
        } catch (e: IOException) {
            AudioResult.Err("Non riesco a collegarmi a Internet Archive. Controlla la connessione.")
        }
    }

    companion object {
        private val ID = Regex("^[A-Za-z0-9._\\-]{3,150}$")

        /** Toglie ciò che nella sintassi di ricerca ha un significato speciale. */
        fun sanitize(text: String): String = text.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().replace(Regex("\\s+"), " ")

        fun parseList(json: String): List<Audiobook> {
            val docs = runCatching { JSONObject(json).optJSONObject("response")?.optJSONArray("docs") }.getOrNull() ?: return emptyList()
            val out = ArrayList<Audiobook>()
            for (i in 0 until docs.length()) {
                val d = docs.optJSONObject(i) ?: continue
                val id = d.optString("identifier")
                if (!ID.matches(id)) continue
                out += Audiobook(id, FeedParser.clean(first(d.opt("title"))).ifBlank { id }, FeedParser.clean(first(d.opt("creator"))))
            }
            return out
        }

        /** I campi di Internet Archive possono essere un testo o una lista di testi. */
        private fun first(v: Any?): String = when (v) {
            is String -> v
            is JSONArray -> if (v.length() > 0) v.optString(0) else ""
            else -> ""
        }

        fun parseDetail(book: Audiobook, json: String): AudiobookDetail {
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return AudiobookDetail(book, "", emptyList())
            val description = FeedParser.clean(first(root.optJSONObject("metadata")?.opt("description"))).take(900)
            val files = root.optJSONArray("files") ?: JSONArray()
            val candidates = ArrayList<JSONObject>()
            for (i in 0 until files.length()) files.optJSONObject(i)?.let(candidates::add)
            val mp3 = candidates.filter { it.optString("format").contains("MP3", ignoreCase = true) || it.optString("name").endsWith(".mp3", true) }
            // Meglio una sola versione per capitolo: prima la 64 kbps (leggera), poi VBR, poi le altre.
            val preferred = listOf("64Kbps MP3", "VBR MP3").firstNotNullOfOrNull { fmt -> mp3.filter { it.optString("format").equals(fmt, true) }.takeIf { it.isNotEmpty() } } ?: mp3
            val tracks = preferred.sortedBy { it.optString("name") }.mapIndexed { i, f ->
                val name = f.optString("name")
                AudioItem(
                    id = book.id + "/" + name,
                    title = f.optString("title").ifBlank { "Capitolo ${i + 1}" },
                    subtitle = book.title,
                    url = "https://archive.org/download/${book.id}/" + name.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
                    artwork = null,
                    durationSec = FeedParser.parseDuration(f.optString("length")),
                )
            }
            return AudiobookDetail(book, description, tracks)
        }
    }
}

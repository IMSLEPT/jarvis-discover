package it.jarvis.discover.data.content

import it.jarvis.discover.data.youtube.HttpFetcher
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Un libro (opera) del catalogo di Open Library. */
data class Book(
    val key: String,
    val title: String,
    val authors: List<String>,
    val year: Int?,
    val coverId: Long?,
    val subjects: List<String>,
    val languages: List<String>,
    val editionCount: Int,
    /** Identificativi di Internet Archive delle edizioni digitalizzate (prestito digitale o pubblico dominio). */
    val archiveIds: List<String>,
    val hasFulltext: Boolean,
) {
    val inItalian: Boolean get() = "ita" in languages
    val coverUrl: String? get() = coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
    val pageUrl: String get() = "https://openlibrary.org$key"
    val archiveUrl: String? get() = archiveIds.firstOrNull()?.let { "https://archive.org/details/$it" }
    val searchOnGoogleBooksUrl: String
        get() = "https://www.google.com/search?tbm=bks&q=" + URLEncoder.encode(title + " " + authors.firstOrNull().orEmpty(), "UTF-8")
}

sealed interface BooksResult {
    data class Ok(val books: List<Book>) : BooksResult
    data class Err(val message: String) : BooksResult
}

/**
 * Catalogo bibliografico Open Library (gratuito, senza chiave). Le descrizioni, quando esistono,
 * sono quelle di Open Library e spesso sono in inglese: l'app lo dice.
 */
class OpenLibraryClient(private val http: HttpFetcher = TextFetcher) {

    fun search(text: String, italianOnly: Boolean = true, limit: Int = 24, page: Int = 1): BooksResult {
        val q = buildString {
            append(text.trim())
            if (italianOnly) append(" language:ita")
        }
        val url = "https://openlibrary.org/search.json?q=" + URLEncoder.encode(q, "UTF-8") +
            "&fields=" + URLEncoder.encode(FIELDS, "UTF-8") + "&limit=$limit&page=$page&lang=it"
        return try {
            val r = http.get(url)
            if (r.code !in 200..299) BooksResult.Err("Open Library non risponde (${r.code}). Riprova più tardi.")
            else BooksResult.Ok(parseSearch(r.body))
        } catch (e: java.io.IOException) {
            BooksResult.Err("Non riesco a collegarmi a Open Library. Controlla la connessione.")
        }
    }

    /** Descrizione dell'opera, se presente. Stringa vuota se Open Library non ne ha una. */
    fun description(key: String): String = try {
        val r = http.get("https://openlibrary.org$key.json")
        if (r.code in 200..299) parseDescription(r.body) else ""
    } catch (e: java.io.IOException) {
        ""
    }

    companion object {
        const val FIELDS = "key,title,author_name,first_publish_year,cover_i,subject,language,edition_count,ia,has_fulltext"

        fun parseSearch(json: String): List<Book> {
            val docs = runCatching { JSONObject(json).optJSONArray("docs") }.getOrNull() ?: return emptyList()
            val out = ArrayList<Book>()
            for (i in 0 until docs.length()) {
                val d = docs.optJSONObject(i) ?: continue
                val key = d.optString("key")
                val title = d.optString("title")
                if (!key.startsWith("/works/") || title.isBlank()) continue
                out += Book(
                    key = key,
                    title = title,
                    authors = strings(d.optJSONArray("author_name")).take(3),
                    year = d.optInt("first_publish_year", 0).takeIf { it > 0 },
                    coverId = d.optLong("cover_i", 0).takeIf { it > 0 },
                    subjects = strings(d.optJSONArray("subject")).take(8),
                    languages = strings(d.optJSONArray("language")),
                    editionCount = d.optInt("edition_count", 0),
                    archiveIds = strings(d.optJSONArray("ia")).take(3),
                    hasFulltext = d.optBoolean("has_fulltext", false),
                )
            }
            return out
        }

        fun parseDescription(json: String): String {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return ""
            val d = o.opt("description")
            val text = when (d) {
                is String -> d
                is JSONObject -> d.optString("value")
                else -> ""
            }
            return FeedParser.clean(text).take(900)
        }

        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
    }
}

/** Percorsi tematici: raccolte automatiche per argomento. Non sono classifiche editoriali: i titoli arrivano da Open Library. */
data class ReadingPath(val id: String, val title: String, val emoji: String, val query: String, val about: String)

val ReadingPaths = listOf(
    ReadingPath("psico", "Capire la psicologia umana", "🧠", "psicologia", "Libri in italiano sul modo in cui pensiamo, sentiamo e ci comportiamo."),
    ReadingPath("filo", "Prime domande di filosofia", "🤔", "filosofia", "Opere di filosofia e storia del pensiero disponibili in italiano."),
    ReadingPath("figli", "Crescere i figli", "👨‍👩‍👦", "educazione dei figli", "Educazione, genitorialità e sviluppo del bambino."),
    ReadingPath("scienza", "Scienza per curiosi", "🔬", "divulgazione scientifica", "Divulgazione scientifica in italiano."),
    ReadingPath("tech", "Tecnologia e intelligenza artificiale", "🤖", "intelligenza artificiale", "Per orientarsi su AI, tecnologia e futuro."),
    ReadingPath("bio", "Vite che ispirano", "🌟", "biografia", "Biografie e autobiografie."),
    ReadingPath("produttivita", "Tempo e produttività", "⏳", "gestione del tempo produttività", "Organizzazione, abitudini e concentrazione."),
    ReadingPath("mindful", "Benessere e mindfulness", "🧘", "mindfulness meditazione", "Consapevolezza, meditazione e benessere."),
    ReadingPath("relazioni", "Relazioni", "💬", "relazioni comunicazione", "Rapporti, comunicazione ed emozioni."),
)

/** Perché un libro potrebbe interessare: confronto semplice e dichiarato tra argomenti dell'utente e soggetti del libro. */
fun reasonsFor(book: Book, interests: List<String>): List<String> {
    val haystack = (book.subjects + book.title).joinToString(" ").lowercase()
    return interests.filter { i -> i.length >= 4 && haystack.contains(i.lowercase().take(maxOf(4, i.length - 2))) }.take(3)
}


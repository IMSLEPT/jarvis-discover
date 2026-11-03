package it.jarvis.discover.data.content

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.jarvis.discover.data.categoryOf
import it.jarvis.discover.data.today.DailyContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * "Un libro per oggi": un titolo dal catalogo Open Library, scelto in base ai tuoi interessi tra i percorsi
 * di lettura. Il criterio è semplice e dichiarato (percorso legato all'argomento, ruotato ogni giorno), non una
 * valutazione di qualità del libro. Il risultato resta in cache per tutta la giornata.
 */
class TodayBook(
    context: Context,
    private val scope: CoroutineScope,
    private val client: OpenLibraryClient = OpenLibraryClient(),
) {
    private val prefs = context.applicationContext.getSharedPreferences("discover_today", Context.MODE_PRIVATE)

    var book by mutableStateOf<Book?>(null); private set
    var path by mutableStateOf<ReadingPath?>(null); private set
    var failed by mutableStateOf(false); private set
    private var loading = false

    init {
        runCatching {
            val o = JSONObject(prefs.getString(K_PICK, null) ?: return@runCatching)
            if (o.getLong("day") == DailyContent.today()) {
                book = ContentJson.bookFromJson(o.getJSONObject("book"))
                path = ReadingPaths.firstOrNull { it.id == o.getString("path") }
            }
        }
    }

    fun load(interests: List<String>) {
        val day = DailyContent.today()
        val chosen = pathFor(interests, day)
        if ((book != null && path?.id == chosen.id) || loading) return
        loading = true
        failed = false
        scope.launch {
            val result = withContext(Dispatchers.IO) { client.search(chosen.query, italianOnly = true, limit = 20) }
            loading = false
            when (result) {
                is BooksResult.Ok -> {
                    val pick = pickBook(result.books, day)
                    if (pick == null) failed = true else {
                        book = pick
                        path = chosen
                        prefs.edit().putString(
                            K_PICK,
                            JSONObject().put("day", day).put("path", chosen.id).put("book", ContentJson.bookToJson(pick)).toString(),
                        ).apply()
                    }
                }
                is BooksResult.Err -> failed = true
            }
        }
    }

    companion object {
        private const val K_PICK = "book"

        /** Categoria di interesse → percorso di lettura più vicino. */
        private val CATEGORY_PATH = mapOf(
            "psicologia" to "psico",
            "crescita" to "produttivita",
            "tecnologia" to "tech",
            "relazioni" to "relazioni",
            "benessere" to "mindful",
            "cultura" to "filo",
            "creativita" to "bio",
        )

        /** Percorso del giorno: ruota tra quelli legati agli interessi; senza interessi, tra tutti. */
        fun pathFor(interests: List<String>, day: Long): ReadingPath {
            val ids = interests.mapNotNull { categoryOf(it)?.id }.distinct().mapNotNull(CATEGORY_PATH::get)
            val candidates = ids.mapNotNull { id -> ReadingPaths.firstOrNull { it.id == id } }.ifEmpty { ReadingPaths }
            return DailyContent.pick(candidates, day)
        }

        /** Tra i risultati, preferisce i libri in italiano con copertina; la scelta ruota col giorno. */
        fun pickBook(books: List<Book>, day: Long): Book? {
            val good = books.filter { it.inItalian && it.coverId != null }.ifEmpty { books.filter { it.inItalian } }
            return if (good.isEmpty()) null else DailyContent.pick(good, day, salt = 3)
        }
    }
}

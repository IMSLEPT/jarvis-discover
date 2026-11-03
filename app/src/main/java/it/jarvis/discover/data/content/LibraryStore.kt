package it.jarvis.discover.data.content

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject

enum class Shelf(val label: String) {
    ToRead("Da leggere"),
    Reading("Sto leggendo"),
    Read("Letto"),
}

data class ShelvedBook(val book: Book, val shelf: Shelf, val at: Long)

/** Libri sugli scaffali, podcast seguiti, episodi salvati e punto in cui eri arrivato. Solo sul telefono. */
class LibraryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("discover_library", Context.MODE_PRIVATE)

    val books = mutableStateListOf<ShelvedBook>().apply { addAll(loadBooks()) }
    val podcasts = mutableStateListOf<Podcast>().apply { addAll(ContentJson.listFromJson(prefs.getString(K_PODCASTS, null), ContentJson::podcastFromJson)) }
    val savedAudio = mutableStateListOf<AudioItem>().apply { addAll(ContentJson.listFromJson(prefs.getString(K_AUDIO, null), ContentJson::audioFromJson)) }

    // ---- libri
    fun shelfOf(key: String): Shelf? = books.firstOrNull { it.book.key == key }?.shelf

    /** Mette il libro su uno scaffale; con [shelf] nullo lo toglie. */
    fun setShelf(book: Book, shelf: Shelf?) {
        books.removeAll { it.book.key == book.key }
        if (shelf != null) books.add(0, ShelvedBook(book, shelf, System.currentTimeMillis()))
        prefs.edit().putString(K_BOOKS, ContentJson.listToJson(books) { sb -> ContentJson.bookToJson(sb.book).put("shelf", sb.shelf.name).put("shelfAt", sb.at) }).apply()
    }

    private fun loadBooks(): List<ShelvedBook> = ContentJson.listFromJson(prefs.getString(K_BOOKS, null)) { o ->
        val book = ContentJson.bookFromJson(o) ?: return@listFromJson null
        val shelf = runCatching { Shelf.valueOf(o.optString("shelf")) }.getOrNull() ?: return@listFromJson null
        ShelvedBook(book, shelf, o.optLong("shelfAt"))
    }

    // ---- podcast
    fun isSubscribed(id: String) = podcasts.any { it.id == id }

    fun toggleSubscribe(p: Podcast) {
        if (!podcasts.removeAll { it.id == p.id }) podcasts.add(0, p)
        prefs.edit().putString(K_PODCASTS, ContentJson.listToJson(podcasts, ContentJson::podcastToJson)).apply()
    }

    // ---- audio salvato
    fun isAudioSaved(id: String) = savedAudio.any { it.id == id }

    fun toggleAudioSaved(item: AudioItem) {
        if (!savedAudio.removeAll { it.id == item.id }) savedAudio.add(0, item)
        prefs.edit().putString(K_AUDIO, ContentJson.listToJson(savedAudio, ContentJson::audioToJson)).apply()
    }

    // ---- punto di ripresa
    fun positionOf(id: String): Long = runCatching { JSONObject(prefs.getString(K_POS, "{}")).optLong(id, 0L) }.getOrDefault(0L)

    fun savePosition(id: String, ms: Long) {
        val o = runCatching { JSONObject(prefs.getString(K_POS, "{}")) }.getOrDefault(JSONObject())
        if (ms < 5_000) o.remove(id) else o.put(id, ms)
        // Si tengono gli ultimi 300 per non far crescere il file senza limite.
        while (o.length() > 300) o.remove(o.keys().next())
        prefs.edit().putString(K_POS, o.toString()).apply()
    }

    private companion object {
        const val K_BOOKS = "books"
        const val K_PODCASTS = "podcasts"
        const val K_AUDIO = "audio"
        const val K_POS = "positions"
    }
}

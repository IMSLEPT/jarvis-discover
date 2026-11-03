package it.jarvis.discover.data.content

import org.json.JSONArray
import org.json.JSONObject

/** Salvataggio su file locale di libri, podcast e tracce audio. Pura, testata con JVM unit test. */
object ContentJson {

    fun bookToJson(b: Book): JSONObject = JSONObject()
        .put("key", b.key).put("title", b.title).put("authors", JSONArray(b.authors)).put("year", b.year ?: JSONObject.NULL)
        .put("cover", b.coverId ?: JSONObject.NULL).put("subjects", JSONArray(b.subjects)).put("langs", JSONArray(b.languages))
        .put("editions", b.editionCount).put("ia", JSONArray(b.archiveIds)).put("full", b.hasFulltext)

    fun bookFromJson(o: JSONObject): Book? {
        val key = o.optString("key").ifBlank { return null }
        return Book(
            key = key, title = o.optString("title"), authors = strings(o.optJSONArray("authors")),
            year = if (o.isNull("year")) null else o.optInt("year"), coverId = if (o.isNull("cover")) null else o.optLong("cover"),
            subjects = strings(o.optJSONArray("subjects")), languages = strings(o.optJSONArray("langs")),
            editionCount = o.optInt("editions"), archiveIds = strings(o.optJSONArray("ia")), hasFulltext = o.optBoolean("full"),
        )
    }

    fun podcastToJson(p: Podcast): JSONObject = JSONObject()
        .put("id", p.id).put("title", p.title).put("author", p.author).put("art", p.artwork).put("feed", p.feedUrl).put("genre", p.genre)

    fun podcastFromJson(o: JSONObject): Podcast? {
        val feed = o.optString("feed").ifBlank { return null }
        return Podcast(o.optString("id", feed), o.optString("title"), o.optString("author"), o.optString("art"), feed, o.optString("genre"))
    }

    fun audioToJson(a: AudioItem): JSONObject = JSONObject()
        .put("id", a.id).put("title", a.title).put("sub", a.subtitle).put("url", a.url).put("art", a.artwork ?: "")
        .put("dur", a.durationSec).put("at", a.publishedMillis)

    fun audioFromJson(o: JSONObject): AudioItem? {
        val url = o.optString("url").ifBlank { return null }
        return AudioItem(o.optString("id", url), o.optString("title"), o.optString("sub"), url, o.optString("art").ifBlank { null }, o.optInt("dur"), o.optLong("at"))
    }

    fun <T> listToJson(list: List<T>, enc: (T) -> JSONObject): String = JSONArray().also { a -> list.forEach { a.put(enc(it)) } }.toString()

    fun <T> listFromJson(json: String?, dec: (JSONObject) -> T?): List<T> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(dec) }
        }.getOrDefault(emptyList())
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
}

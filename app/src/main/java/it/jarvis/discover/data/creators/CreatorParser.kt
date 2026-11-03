package it.jarvis.discover.data.creators

import org.json.JSONArray
import org.json.JSONObject

/** Lettura delle risposte JSON relative a canali e playlist. Nessuna dipendenza da Android. */
object CreatorParser {

    class SearchHit(val videoIds: List<String>, val channelIds: List<String>)

    /** Separa i video dai canali in una risposta di search.list con `type=video,channel`. */
    fun parseMixedSearch(json: String): SearchHit {
        val items = runCatching { JSONObject(json).optJSONArray("items") }.getOrNull()
            ?: return SearchHit(emptyList(), emptyList())
        val videos = LinkedHashSet<String>()
        val channels = LinkedHashSet<String>()
        for (i in 0 until items.length()) {
            val id = items.optJSONObject(i)?.optJSONObject("id") ?: continue
            id.optString("videoId").takeIf { it.isNotBlank() }?.let(videos::add)
            id.optString("channelId").takeIf { it.isNotBlank() }?.let(channels::add)
        }
        return SearchHit(videos.toList(), channels.toList())
    }

    /** Canali di una risposta di channels.list (parti snippet, statistics, contentDetails). */
    fun parseChannels(json: String): List<Creator> {
        val items = runCatching { JSONObject(json).optJSONArray("items") }.getOrNull() ?: return emptyList()
        val out = ArrayList<Creator>()
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val snippet = obj.optJSONObject("snippet") ?: continue
            if (id.isBlank()) continue
            val stats = obj.optJSONObject("statistics")
            val hidden = stats?.optBoolean("hiddenSubscriberCount", false) == true
            val uploads = obj.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")
                ?.optString("uploads").orEmpty().ifBlank { CreatorRules.uploadsIdFor(id) }
            out += Creator(
                id = id,
                title = snippet.optString("title").ifBlank { "Canale senza nome" },
                handle = snippet.optString("customUrl").takeIf { it.isNotBlank() }?.let { if (it.startsWith("@")) it else "@$it" },
                description = snippet.optString("description").take(600),
                thumbnail = bestThumb(snippet.optJSONObject("thumbnails")),
                subscribers = if (hidden) null else stats?.optString("subscriberCount")?.toLongOrNull(),
                videoCount = stats?.optString("videoCount")?.toLongOrNull(),
                uploadsPlaylistId = uploads,
            )
        }
        return out
    }

    /** Id dei video e data di pubblicazione in una risposta di playlistItems.list. */
    class PlaylistPage(val videoIds: List<String>, val publishedAt: Map<String, String>)

    fun parsePlaylistItems(json: String): PlaylistPage {
        val items = runCatching { JSONObject(json).optJSONArray("items") }.getOrNull()
            ?: return PlaylistPage(emptyList(), emptyMap())
        val ids = LinkedHashSet<String>()
        val dates = HashMap<String, String>()
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            val id = obj.optJSONObject("contentDetails")?.optString("videoId").orEmpty()
                .ifBlank { obj.optJSONObject("snippet")?.optJSONObject("resourceId")?.optString("videoId").orEmpty() }
            if (id.isBlank()) continue
            ids += id
            val published = obj.optJSONObject("contentDetails")?.optString("videoPublishedAt").orEmpty()
                .ifBlank { obj.optJSONObject("snippet")?.optString("publishedAt").orEmpty() }
            if (published.isNotBlank()) dates[id] = published
        }
        return PlaylistPage(ids.toList(), dates)
    }

    private fun bestThumb(thumbs: JSONObject?): String {
        if (thumbs == null) return ""
        for (k in listOf("medium", "high", "default")) {
            val url = thumbs.optJSONObject(k)?.optString("url").orEmpty()
            if (url.isNotBlank()) return url
        }
        return ""
    }

    // ------------------------------------------------------------------ salvataggio dei creator seguiti

    fun followsToJson(list: List<FollowedCreator>): String {
        val arr = JSONArray()
        list.forEach { f ->
            val c = f.creator
            arr.put(
                JSONObject()
                    .put("id", c.id).put("title", c.title).put("handle", c.handle ?: JSONObject.NULL)
                    .put("desc", c.description).put("thumb", c.thumbnail)
                    .put("subs", c.subscribers ?: JSONObject.NULL).put("vc", c.videoCount ?: JSONObject.NULL)
                    .put("up", c.uploadsPlaylistId)
                    .put("at", f.followedAt).put("level", f.level.name)
                    .put("topics", JSONArray(f.topics)).put("notify", f.notify),
            )
        }
        return arr.toString()
    }

    fun followsFromJson(json: String?): List<FollowedCreator> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                val topics = o.optJSONArray("topics")?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty()
                FollowedCreator(
                    creator = Creator(
                        id = id,
                        title = o.optString("title"),
                        handle = if (o.isNull("handle")) null else o.optString("handle").takeIf { it.isNotBlank() },
                        description = o.optString("desc"),
                        thumbnail = o.optString("thumb"),
                        subscribers = if (o.isNull("subs")) null else o.optLong("subs"),
                        videoCount = if (o.isNull("vc")) null else o.optLong("vc"),
                        uploadsPlaylistId = o.optString("up").ifBlank { CreatorRules.uploadsIdFor(id) },
                    ),
                    followedAt = o.optLong("at"),
                    level = runCatching { FollowLevel.valueOf(o.optString("level")) }.getOrDefault(FollowLevel.Normal),
                    topics = topics,
                    notify = o.optBoolean("notify", false),
                )
            }
        }.getOrDefault(emptyList())
    }
}

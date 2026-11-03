package it.jarvis.discover.data.youtube

import org.json.JSONArray
import org.json.JSONObject

/** Costruttori di risposte JSON nel formato di YouTube Data API v3, per i test. */
object TestData {

    fun video(
        id: String,
        title: String = "Come funziona il cervello? Una spiegazione semplice",
        description: String = "In questo video spieghiamo come funziona la memoria.",
        duration: String = "PT10M",
        embeddable: Boolean = true,
        privacy: String = "public",
        live: String = "none",
        audio: String? = "it",
        textLang: String? = null,
        caption: Boolean = false,
        kids: Boolean = false,
        blocked: List<String>? = null,
        allowed: List<String>? = null,
        views: String? = "12345",
        channelId: String = "UC_$id",
        thumbnails: JSONObject? = null,
    ): JSONObject {
        val snippet = JSONObject()
            .put("publishedAt", "2024-03-12T10:00:00Z")
            .put("channelId", channelId)
            .put("title", title)
            .put("description", description)
            .put("channelTitle", "Canale $channelId")
            .put("liveBroadcastContent", live)
        if (audio != null) snippet.put("defaultAudioLanguage", audio)
        if (textLang != null) snippet.put("defaultLanguage", textLang)
        if (thumbnails != null) snippet.put("thumbnails", thumbnails)

        val content = JSONObject().put("duration", duration).put("caption", caption.toString())
        if (blocked != null || allowed != null) {
            val r = JSONObject()
            blocked?.let { r.put("blocked", JSONArray(it)) }
            allowed?.let { r.put("allowed", JSONArray(it)) }
            content.put("regionRestriction", r)
        }
        val obj = JSONObject()
            .put("id", id)
            .put("snippet", snippet)
            .put("contentDetails", content)
            .put("status", JSONObject().put("embeddable", embeddable).put("privacyStatus", privacy).put("madeForKids", kids))
        if (views != null) obj.put("statistics", JSONObject().put("viewCount", views).put("likeCount", "100"))
        return obj
    }

    fun videosResponse(vararg videos: JSONObject): String =
        JSONObject().put("items", JSONArray(videos.toList())).toString()

    fun searchResponse(vararg ids: String): String {
        val items = JSONArray()
        ids.forEach { items.put(JSONObject().put("id", JSONObject().put("kind", "youtube#video").put("videoId", it))) }
        return JSONObject().put("items", items).toString()
    }

    fun errorBody(code: Int, message: String, reason: String? = null, status: String? = null, detailReason: String? = null): String {
        val error = JSONObject().put("code", code).put("message", message)
        if (reason != null) error.put("errors", JSONArray().put(JSONObject().put("reason", reason).put("domain", "youtube.quota")))
        if (status != null) error.put("status", status)
        if (detailReason != null) error.put("details", JSONArray().put(JSONObject().put("reason", detailReason)))
        return JSONObject().put("error", error).toString()
    }

    fun item(id: String, channelId: String = "UC_$id", topic: String? = null, lang: LangClass = LangClass.Italian, duration: Int = 600) =
        VideoItem(
            id = id, title = "Titolo $id", channelId = channelId, channelTitle = "Canale", description = "d",
            publishedAt = "2024-01-01T00:00:00Z", durationSec = duration, thumbnail = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
            views = 10, likes = null, lang = lang, hasCaptions = false, topic = topic,
        )
}

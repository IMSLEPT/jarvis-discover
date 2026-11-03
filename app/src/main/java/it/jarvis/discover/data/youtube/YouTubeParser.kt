package it.jarvis.discover.data.youtube

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lettura delle risposte JSON di YouTube Data API v3. Nessuna dipendenza da Android:
 * è testata con JVM unit test su risposte di esempio.
 */
object YouTubeParser {

    /** Sotto questa durata un video è troppo "sottile" per il feed. */
    const val MIN_DURATION_SEC = 30
    const val MAX_DESCRIPTION = 800

    private val DURATION = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")
    private val KEY_PATTERN = Regex("AIza[0-9A-Za-z_\\-]{20,}")

    /** "PT1H2M3S" → 3723. Restituisce 0 se il formato non è valido (es. dirette, "P0D"). */
    fun parseDurationSeconds(iso: String?): Int {
        val m = DURATION.matchEntire(iso?.trim().orEmpty()) ?: return 0
        fun g(i: Int) = m.groupValues[i].toLongOrNull() ?: 0L
        val total = g(1) * 86_400 + g(2) * 3_600 + g(3) * 60 + g(4)
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Id dei video in una risposta di search.list (ignora canali e playlist). */
    fun parseSearchIds(json: String): List<String> {
        val items = runCatching { JSONObject(json).optJSONArray("items") }.getOrNull() ?: return emptyList()
        val ids = LinkedHashSet<String>()
        for (i in 0 until items.length()) {
            val id = items.optJSONObject(i)?.optJSONObject("id")?.optString("videoId").orEmpty()
            if (id.isNotBlank()) ids += id
        }
        return ids.toList()
    }

    /**
     * Video di una risposta di videos.list (parti snippet, contentDetails, statistics, status),
     * già ripuliti: restano solo quelli pubblici, incorporabili, non in diretta, non bloccati in
     * Italia, non per bambini, lunghi almeno [MIN_DURATION_SEC] e non stranieri senza sottotitoli.
     */
    fun parseVideos(json: String, region: String = "IT"): List<VideoItem> {
        val items = runCatching { JSONObject(json).optJSONArray("items") }.getOrNull() ?: return emptyList()
        val out = ArrayList<VideoItem>()
        for (i in 0 until items.length()) {
            val obj = items.optJSONObject(i) ?: continue
            parseOne(obj, region)?.let(out::add)
        }
        return out
    }

    private fun parseOne(obj: JSONObject, region: String): VideoItem? {
        val id = obj.optString("id")
        if (id.isBlank()) return null
        val snippet = obj.optJSONObject("snippet") ?: return null
        val content = obj.optJSONObject("contentDetails") ?: return null
        val status = obj.optJSONObject("status")

        if (status == null || !status.optBoolean("embeddable", false)) return null
        if (status.optString("privacyStatus") != "public") return null
        if (status.optBoolean("madeForKids", false)) return null
        if (snippet.optString("liveBroadcastContent", "none") != "none") return null

        val restriction = content.optJSONObject("regionRestriction")
        if (restriction != null) {
            restriction.optJSONArray("blocked")?.let { if (it.contains(region)) return null }
            restriction.optJSONArray("allowed")?.let { if (!it.contains(region)) return null }
        }

        val duration = parseDurationSeconds(content.optString("duration"))
        if (duration < MIN_DURATION_SEC) return null

        val title = snippet.optString("title").trim()
        if (title.isEmpty()) return null
        val description = snippet.optString("description")
        val hasCaptions = content.optString("caption") == "true"
        val lang = classifyLanguage(
            audio = snippet.optString("defaultAudioLanguage"),
            text = snippet.optString("defaultLanguage"),
            hasCaptions = hasCaptions,
            title = title,
            description = description,
        )
        if (lang == LangClass.Foreign) return null

        val stats = obj.optJSONObject("statistics")
        return VideoItem(
            id = id,
            title = title,
            channelId = snippet.optString("channelId"),
            channelTitle = snippet.optString("channelTitle"),
            description = description.take(MAX_DESCRIPTION),
            publishedAt = snippet.optString("publishedAt"),
            durationSec = duration,
            thumbnail = pickThumbnail(snippet.optJSONObject("thumbnails"), id),
            views = stats?.optString("viewCount")?.toLongOrNull(),
            likes = stats?.optString("likeCount")?.toLongOrNull(),
            lang = lang,
            hasCaptions = hasCaptions,
            topic = null,
        )
    }

    private fun JSONArray.contains(value: String): Boolean {
        for (i in 0 until length()) if (optString(i).equals(value, ignoreCase = true)) return true
        return false
    }

    /** Preferisce la miniatura 16:9 più nitida; la `high` e la `standard` hanno bande nere. */
    fun pickThumbnail(thumbnails: JSONObject?, id: String): String {
        val maxres = thumbnails?.optJSONObject("maxres")?.optString("url").orEmpty()
        if (maxres.isNotBlank()) return maxres
        val medium = thumbnails?.optJSONObject("medium")?.optString("url").orEmpty()
        if (medium.isNotBlank()) return medium
        return "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    }

    fun classifyLanguage(
        audio: String?,
        text: String?,
        hasCaptions: Boolean,
        title: String,
        description: String,
    ): LangClass {
        val a = audio.orEmpty().lowercase()
        val t = text.orEmpty().lowercase()
        val other = if (hasCaptions) LangClass.Subtitled else LangClass.Foreign
        return when {
            a.isNotBlank() -> if (a.startsWith("it")) LangClass.Italian else other
            t.isNotBlank() -> if (t.startsWith("it")) LangClass.ItalianGuess else other
            looksItalian("$title $description") -> LangClass.ItalianGuess
            else -> other
        }
    }

    private val ITALIAN_WORDS = setOf(
        "il", "gli", "che", "è", "sono", "della", "delle", "dello", "degli", "dei", "nel", "nella", "nei",
        "questo", "questa", "questi", "anche", "più", "perché", "cosa", "come", "non", "una", "alla", "allo",
        "sul", "sulla", "tutto", "tutti", "ogni", "quando", "dove", "molto", "quello", "quella", "fare",
        "essere", "dal", "dalla", "già", "così", "cui", "suo", "sua", "loro", "ma", "per",
    )
    private val ENGLISH_WORDS = setOf(
        "the", "and", "of", "to", "is", "you", "for", "with", "how", "what", "this", "that", "are",
        "your", "from", "it", "on", "be", "we", "my", "was", "have", "will", "can",
    )

    /** Euristica per video senza lingua dichiarata: parole italiane distintive contro parole inglesi. */
    fun looksItalian(text: String): Boolean {
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.take(200)
        val itHits = words.count { it in ITALIAN_WORDS }
        val enHits = words.count { it in ENGLISH_WORDS }
        return itHits >= 2 && itHits > enHits
    }

    /** Classifica un errore HTTP di YouTube. Tollerante: l'unica ragione documentata è `quotaExceeded`. */
    fun classifyError(httpCode: Int, body: String?): YtError {
        val error = runCatching { JSONObject(body.orEmpty()).optJSONObject("error") }.getOrNull()
        val message = error?.optString("message").orEmpty()
        val reasons = ArrayList<String>()
        error?.optJSONArray("errors")?.let { arr ->
            for (i in 0 until arr.length()) reasons += arr.optJSONObject(i)?.optString("reason").orEmpty()
        }
        error?.optJSONArray("details")?.let { arr ->
            for (i in 0 until arr.length()) reasons += arr.optJSONObject(i)?.optString("reason").orEmpty()
        }
        error?.optString("status")?.let { reasons += it }
        val all = (reasons + message).joinToString(" ").lowercase()

        val kind = when {
            httpCode == 429 || "quotaexceeded" in all || "dailylimitexceeded" in all ||
                "ratelimitexceeded" in all || "resource_exhausted" in all -> YtErrorKind.QuotaExceeded
            "api_key_invalid" in all || "keyinvalid" in all || "api key not valid" in all -> YtErrorKind.InvalidKey
            "accessnotconfigured" in all || "service_disabled" in all ||
                "has not been used in project" in all || "is disabled" in all -> YtErrorKind.ApiNotEnabled
            "blocked" in all || "referer" in all || "restriction" in all -> YtErrorKind.KeyRestricted
            else -> YtErrorKind.Other
        }
        return YtError(kind, httpCode, redactKeys(message).take(140).ifBlank { null })
    }

    /** Toglie da un testo qualunque sequenza che somigli a una chiave API Google. */
    fun redactKeys(text: String): String = KEY_PATTERN.replace(text, "[chiave]")

    // --- Serializzazione per salvataggi e cache locali ---

    fun toJson(v: VideoItem): JSONObject = JSONObject()
        .put("id", v.id).put("title", v.title).put("cid", v.channelId).put("ch", v.channelTitle)
        .put("desc", v.description).put("pub", v.publishedAt).put("dur", v.durationSec)
        .put("thumb", v.thumbnail).put("views", v.views ?: JSONObject.NULL).put("likes", v.likes ?: JSONObject.NULL)
        .put("lang", v.lang.name).put("cc", v.hasCaptions).put("topic", v.topic ?: JSONObject.NULL)

    fun fromJson(o: JSONObject): VideoItem? {
        val id = o.optString("id")
        if (id.isBlank()) return null
        return VideoItem(
            id = id,
            title = o.optString("title"),
            channelId = o.optString("cid"),
            channelTitle = o.optString("ch"),
            description = o.optString("desc"),
            publishedAt = o.optString("pub"),
            durationSec = o.optInt("dur"),
            thumbnail = o.optString("thumb").ifBlank { "https://i.ytimg.com/vi/$id/mqdefault.jpg" },
            views = if (o.isNull("views")) null else o.optLong("views"),
            likes = if (o.isNull("likes")) null else o.optLong("likes"),
            lang = LangClass.entries.firstOrNull { it.name == o.optString("lang") } ?: LangClass.ItalianGuess,
            hasCaptions = o.optBoolean("cc"),
            topic = if (o.isNull("topic")) null else o.optString("topic"),
        )
    }

    fun listToJson(items: List<VideoItem>): String {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        return arr.toString()
    }

    fun listFromJson(raw: String?): List<VideoItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

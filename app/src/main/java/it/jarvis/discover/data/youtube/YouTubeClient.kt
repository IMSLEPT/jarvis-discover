package it.jarvis.discover.data.youtube

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HttpResponse(val code: Int, val body: String)

/** Astrazione della rete, per poter testare il client senza internet. */
fun interface HttpFetcher {
    @Throws(IOException::class)
    fun get(url: String): HttpResponse
}

object UrlConnectionFetcher : HttpFetcher {
    override fun get(url: String): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }
}

data class FeedResult(
    val items: List<VideoItem>,
    /** Errore incontrato lungo la strada; se [items] non è vuoto il risultato è parziale. */
    val error: YtError?,
    val searchesDone: Int,
)

/**
 * Client minimale di YouTube Data API v3 (solo lettura, con chiave API). Costi di quota ufficiali:
 * search.list = 1 nel proprio bucket da 100/giorno; videos.list e i18nLanguages.list = 1 unità
 * del bucket da 10.000/giorno. Le funzioni sono bloccanti: chiamarle fuori dal thread principale.
 */
class YouTubeClient(private val http: HttpFetcher = UrlConnectionFetcher) {

    internal fun url(path: String, params: List<Pair<String, String>>): String =
        "https://www.googleapis.com/youtube/v3/$path?" +
            params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    /** Esegue la richiesta e traduce errori HTTP e di rete in [YtError]. Non registra mai l'URL (contiene la chiave). */
    internal fun call(url: String): ApiResult<String> {
        val response = try {
            http.get(url)
        } catch (e: IOException) {
            return ApiResult.Err(YtError(YtErrorKind.Network))
        } catch (e: SecurityException) {
            return ApiResult.Err(YtError(YtErrorKind.Network))
        }
        return if (response.code in 200..299) {
            ApiResult.Ok(response.body)
        } else {
            ApiResult.Err(YouTubeParser.classifyError(response.code, response.body))
        }
    }

    /** Verifica la chiave con una chiamata economica (i18nLanguages.list, 1 unità del bucket generale). */
    fun testKey(apiKey: String): ApiResult<Unit> =
        when (val r = call(url("i18nLanguages", listOf("part" to "snippet", "hl" to "it", "key" to apiKey)))) {
            is ApiResult.Ok -> ApiResult.Ok(Unit)
            is ApiResult.Err -> r
        }

    fun search(apiKey: String, query: String, durationHint: String? = null, maxResults: Int = 25): ApiResult<List<String>> {
        val params = buildList {
            add("part" to "snippet")
            add("type" to "video")
            add("q" to query)
            add("maxResults" to maxResults.toString())
            add("relevanceLanguage" to "it")
            add("regionCode" to "IT")
            add("videoEmbeddable" to "true")
            add("videoSyndicated" to "true")
            add("safeSearch" to "moderate")
            if (durationHint != null) add("videoDuration" to durationHint)
            add("key" to apiKey)
        }
        return when (val r = call(url("search", params))) {
            is ApiResult.Ok -> ApiResult.Ok(YouTubeParser.parseSearchIds(r.value))
            is ApiResult.Err -> r
        }
    }

    /** Dettagli di molti video; manda al massimo [CHUNK] id per richiesta. */
    fun videos(apiKey: String, ids: List<String>): ApiResult<List<VideoItem>> {
        val all = ArrayList<VideoItem>()
        for (chunk in ids.chunked(CHUNK)) {
            val params = listOf(
                "part" to "snippet,contentDetails,statistics,status",
                "id" to chunk.joinToString(","),
                "hl" to "it",
                "key" to apiKey,
            )
            when (val r = call(url("videos", params))) {
                is ApiResult.Ok -> all += YouTubeParser.parseVideos(r.value)
                is ApiResult.Err -> return r
            }
        }
        return ApiResult.Ok(all)
    }

    /**
     * Cerca con [queries] e restituisce i video già filtrati e alternati per argomento.
     * [onSearchDone] viene chiamata dopo ogni search.list riuscita (per il contatore di quota).
     * Si ferma al primo errore bloccante; ciò che era già stato trovato viene comunque restituito.
     */
    fun fetchFeed(
        apiKey: String,
        queries: List<FeedQuery>,
        durationHint: String? = null,
        onSearchDone: () -> Unit = {},
    ): FeedResult {
        val idsByQuery = ArrayList<Pair<FeedQuery, List<String>>>()
        var error: YtError? = null
        var searches = 0
        for (q in queries) {
            when (val r = search(apiKey, q.text, durationHint)) {
                is ApiResult.Ok -> {
                    searches++
                    onSearchDone()
                    idsByQuery += q to r.value
                }
                is ApiResult.Err -> {
                    error = r.error
                    break
                }
            }
        }
        val uniqueIds = idsByQuery.flatMap { it.second }.distinct()
        if (uniqueIds.isEmpty()) return FeedResult(emptyList(), error, searches)

        val details = when (val r = videos(apiKey, uniqueIds)) {
            is ApiResult.Ok -> r.value.associateBy { it.id }
            is ApiResult.Err -> return FeedResult(emptyList(), r.error, searches)
        }
        val claimed = HashSet<String>()
        val perQuery = idsByQuery.map { (q, ids) ->
            ids.mapNotNull { id -> if (claimed.add(id)) details[id]?.copy(topic = q.topic) else null }
        }
        return FeedResult(FeedQueries.interleave(perQuery), error, searches)
    }

    companion object {
        const val CHUNK = 50
    }
}

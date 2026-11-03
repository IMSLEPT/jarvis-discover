package it.jarvis.discover.data.creators

import it.jarvis.discover.data.youtube.ApiResult
import it.jarvis.discover.data.youtube.VideoItem
import it.jarvis.discover.data.youtube.YouTubeClient
import it.jarvis.discover.data.youtube.YouTubeParser
import it.jarvis.discover.data.youtube.YtErrorKind

/** Video di un creator, con il numero di ricerche (quota "search") eventualmente consumate. */
data class CreatorVideos(val videos: List<VideoItem>, val searchesUsed: Int)

/**
 * Chiamate di YouTube Data API v3 dedicate a canali e creator. Costi ufficiali: channels.list e
 * playlistItems.list = 1 unità del bucket generale; search.list = 1 del bucket "ricerche" (100/giorno).
 * Per leggere i contenuti di un creator si usa la sua playlist dei caricamenti (non intacca le ricerche).
 */
class CreatorClient(private val yt: YouTubeClient) {

    /** Ricerca universale: una sola search.list con video e canali insieme (1 ricerca consumata). */
    fun universalSearch(apiKey: String, query: String): ApiResult<UniversalResult> {
        val params = listOf(
            "part" to "snippet", "type" to "video,channel", "q" to query, "maxResults" to "25",
            "relevanceLanguage" to "it", "regionCode" to "IT", "safeSearch" to "moderate", "key" to apiKey,
        )
        val hit = when (val r = yt.call(yt.url("search", params))) {
            is ApiResult.Ok -> CreatorParser.parseMixedSearch(r.value)
            is ApiResult.Err -> return r
        }
        val creators = if (hit.channelIds.isEmpty()) emptyList() else when (val r = channels(apiKey, hit.channelIds.take(10))) {
            is ApiResult.Ok -> hit.channelIds.mapNotNull { id -> r.value.firstOrNull { it.id == id } }
            is ApiResult.Err -> return r
        }
        val videos = if (hit.videoIds.isEmpty()) emptyList() else when (val r = yt.videos(apiKey, hit.videoIds)) {
            is ApiResult.Ok -> r.value
            is ApiResult.Err -> return r
        }
        return ApiResult.Ok(UniversalResult(creators, videos))
    }

    fun channels(apiKey: String, ids: List<String>): ApiResult<List<Creator>> {
        val all = ArrayList<Creator>()
        for (chunk in ids.distinct().chunked(YouTubeClient.CHUNK)) {
            val params = listOf(
                "part" to "snippet,statistics,contentDetails", "id" to chunk.joinToString(","),
                "hl" to "it", "maxResults" to "50", "key" to apiKey,
            )
            when (val r = yt.call(yt.url("channels", params))) {
                is ApiResult.Ok -> all += CreatorParser.parseChannels(r.value)
                is ApiResult.Err -> return r
            }
        }
        return ApiResult.Ok(all)
    }

    /** Trova un canale dal suo @handle (1 unità del bucket generale, nessuna ricerca consumata). */
    fun channelByHandle(apiKey: String, handle: String): ApiResult<Creator?> {
        val clean = handle.trim().removePrefix("@")
        val params = listOf(
            "part" to "snippet,statistics,contentDetails", "forHandle" to "@$clean", "hl" to "it", "key" to apiKey,
        )
        return when (val r = yt.call(yt.url("channels", params))) {
            is ApiResult.Ok -> ApiResult.Ok(CreatorParser.parseChannels(r.value).firstOrNull())
            is ApiResult.Err -> r
        }
    }

    /**
     * Ultimi video di un creator, dal più recente. Si legge la playlist dei caricamenti; se YouTube la
     * rifiuta (ripiego) si usa una ricerca per canale ordinata per data, che consuma 1 ricerca.
     */
    fun creatorVideos(apiKey: String, creator: Creator, max: Int = 50): ApiResult<CreatorVideos> {
        val playlist = playlistVideoIds(apiKey, creator.uploadsPlaylistId, max)
        val ids: List<String>
        var searches = 0
        when (playlist) {
            is ApiResult.Ok -> ids = playlist.value.videoIds
            is ApiResult.Err -> {
                val kind = playlist.error.kind
                if (kind == YtErrorKind.Network || kind == YtErrorKind.QuotaExceeded || playlist.error.isKeyProblem) return playlist
                val params = listOf(
                    "part" to "snippet", "type" to "video", "channelId" to creator.id, "order" to "date",
                    "maxResults" to minOf(max, 50).toString(), "videoEmbeddable" to "true", "key" to apiKey,
                )
                ids = when (val r = yt.call(yt.url("search", params))) {
                    is ApiResult.Ok -> YouTubeParser.parseSearchIds(r.value).also { searches = 1 }
                    is ApiResult.Err -> return r
                }
            }
        }
        if (ids.isEmpty()) return ApiResult.Ok(CreatorVideos(emptyList(), searches))
        return when (val r = yt.videos(apiKey, ids)) {
            is ApiResult.Ok -> {
                val order = ids.withIndex().associate { it.value to it.index }
                ApiResult.Ok(CreatorVideos(r.value.sortedBy { order[it.id] ?: Int.MAX_VALUE }, searches))
            }
            is ApiResult.Err -> r
        }
    }

    /**
     * Video recenti di più creator seguiti, mescolati (uno per creator a turno), per il feed.
     * I creator che danno errore vengono saltati; l'errore si segnala solo se nessuno ha risposto.
     */
    fun followedFeed(apiKey: String, creators: List<Creator>, perCreator: Int = 8): ApiResult<List<VideoItem>> {
        if (creators.isEmpty()) return ApiResult.Ok(emptyList())
        val idsPerCreator = ArrayList<Pair<Creator, List<String>>>()
        var lastError: ApiResult.Err? = null
        for (c in creators) {
            when (val r = playlistVideoIds(apiKey, c.uploadsPlaylistId, perCreator)) {
                is ApiResult.Ok -> idsPerCreator += c to r.value.videoIds
                is ApiResult.Err -> {
                    lastError = r
                    if (r.error.kind == YtErrorKind.QuotaExceeded || r.error.isKeyProblem || r.error.kind == YtErrorKind.Network) break
                }
            }
        }
        val allIds = idsPerCreator.flatMap { it.second }.distinct()
        if (allIds.isEmpty()) return lastError ?: ApiResult.Ok(emptyList())
        val details = when (val r = yt.videos(apiKey, allIds)) {
            is ApiResult.Ok -> r.value.associateBy { it.id }
            is ApiResult.Err -> return r
        }
        val lists = idsPerCreator.map { (c, ids) -> ids.mapNotNull { details[it]?.copy(topic = c.title) } }
        val out = ArrayList<VideoItem>()
        var i = 0
        while (lists.any { i < it.size }) {
            lists.forEach { l -> if (i < l.size) out += l[i] }
            i++
        }
        return ApiResult.Ok(out)
    }

    /** Date di pubblicazione dei più recenti caricamenti, per capire se c'è qualcosa di nuovo (usato dalle notifiche). */
    fun latestUploads(apiKey: String, creator: Creator, max: Int = 5): ApiResult<CreatorParser.PlaylistPage> =
        playlistVideoIds(apiKey, creator.uploadsPlaylistId, max)

    private fun playlistVideoIds(apiKey: String, playlistId: String, max: Int): ApiResult<CreatorParser.PlaylistPage> {
        val params = listOf(
            "part" to "contentDetails", "playlistId" to playlistId,
            "maxResults" to max.coerceIn(1, 50).toString(), "key" to apiKey,
        )
        return when (val r = yt.call(yt.url("playlistItems", params))) {
            is ApiResult.Ok -> ApiResult.Ok(CreatorParser.parsePlaylistItems(r.value))
            is ApiResult.Err -> r
        }
    }
}

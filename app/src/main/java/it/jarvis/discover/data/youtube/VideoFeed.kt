package it.jarvis.discover.data.youtube

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.jarvis.discover.data.creators.CreatorClient
import it.jarvis.discover.data.creators.CreatorStore
import it.jarvis.discover.data.reco.ProfileStore
import it.jarvis.discover.data.reco.RankContext
import it.jarvis.discover.data.reco.Recommender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface FeedStatus {
    data object Idle : FeedStatus
    data object Loading : FeedStatus
    data object Ready : FeedStatus
    data class Failed(val error: YtError) : FeedStatus
}

sealed interface KeyTest {
    data object Idle : KeyTest
    data object Running : KeyTest
    data object Ok : KeyTest
    data class Failed(val error: YtError) : KeyTest
}

/**
 * Stato e logica del feed video: chiave API cifrata, ricerche a quota controllata, cache,
 * video salvati, "mi piace", video nascosti e preferenze espresse dall'utente.
 * Tutto resta sul telefono; la chiave viene letta dall'archivio cifrato solo al momento della richiesta.
 */
class VideoFeed(
    context: Context,
    private val scope: CoroutineScope,
    val creators: CreatorStore = CreatorStore(context),
    val profile: ProfileStore = ProfileStore.from(context),
    private val client: YouTubeClient = YouTubeClient(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("discover_videos", Context.MODE_PRIVATE)
    private val secure = SecureStore(appContext)
    private val creatorClient = CreatorClient(client)

    // --- Chiave API ---
    var hasKey by mutableStateOf(secure.has(KEY_NAME)); private set
    /** Ultime 4 cifre della chiave, per riconoscerla senza mostrarla. */
    var keyHint by mutableStateOf<String?>(null); private set
    var keyTest by mutableStateOf<KeyTest>(KeyTest.Idle); private set
    /** Cresce a ogni nuova chiave salvata, per far ripartire il caricamento del feed. */
    var keyVersion by mutableIntStateOf(0); private set

    // --- Feed ---
    var status by mutableStateOf<FeedStatus>(FeedStatus.Idle); private set
    var busy by mutableStateOf(false); private set
    /** Ultimo errore non bloccante (es. quota finita mentre il feed è già pieno). */
    var lastError by mutableStateOf<YtError?>(null); private set
    val pool = mutableStateListOf<VideoItem>()
    var searchesToday by mutableIntStateOf(0); private set
    /** Motivi per cui ogni video è nel feed, calcolati dalla classifica trasparente. */
    private val reasons = mutableStateMapOf<String, List<String>>()
    private var rankInterests: Set<String> = emptySet()

    // --- Raccolta e feedback dell'utente ---
    val saved = mutableStateListOf<VideoItem>()
    var likedIds by mutableStateOf(loadSet(K_LIKED)); private set
    var hiddenIds by mutableStateOf(loadSet(K_HIDDEN)); private set
    private var boosts: Map<String, Int> = loadBoosts()

    private var cursor = 0
    private var loadedSig: String? = null
    private var job: Job? = null

    init {
        saved.addAll(YouTubeParser.listFromJson(prefs.getString(K_SAVED, null)))
        rolloverQuota()
        if (hasKey) {
            scope.launch {
                keyHint = withContext(Dispatchers.IO) { secure.get(KEY_NAME)?.takeLast(4) }
                if (keyHint == null) hasKey = false // chiave non più decifrabile: va reinserita
            }
        }
    }

    // ------------------------------------------------------------------ chiave

    /** Salva la chiave cifrata. Restituisce un messaggio d'errore, oppure null se è andata bene. */
    fun saveKey(raw: String): String? {
        val key = raw.trim()
        if (key.length < 20 || key.any { it.isWhitespace() }) {
            return "La chiave sembra incompleta: di solito ha circa 39 caratteri e nessuno spazio."
        }
        return try {
            secure.put(KEY_NAME, key)
            hasKey = true
            keyHint = key.takeLast(4)
            keyTest = KeyTest.Idle
            status = FeedStatus.Idle
            lastError = null
            loadedSig = null
            keyVersion++
            null
        } catch (e: Exception) {
            "Non riesco a salvare la chiave in modo sicuro su questo telefono."
        }
    }

    fun removeKey() {
        job?.cancel()
        secure.remove(KEY_NAME)
        hasKey = false
        keyHint = null
        keyTest = KeyTest.Idle
        status = FeedStatus.Idle
        busy = false
        lastError = null
        pool.clear()
        loadedSig = null
        prefs.edit().remove(K_CACHE).apply()
    }

    fun testKey() {
        if (keyTest == KeyTest.Running) return
        keyTest = KeyTest.Running
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                secure.get(KEY_NAME)?.let { client.testKey(it) }
            }
            keyTest = when (result) {
                null -> { hasKey = false; KeyTest.Idle }
                is ApiResult.Ok -> KeyTest.Ok
                is ApiResult.Err -> KeyTest.Failed(result.error)
            }
        }
    }

    // ------------------------------------------------------------------ feed

    /** Elementi da mostrare, con i filtri attuali. Legge stato Compose: va chiamata nella composizione. */
    fun visible(filter: FeedFilter, acceptSubtitled: Boolean): List<VideoItem> =
        creators.filter(pool.filter { it.id !in hiddenIds && it.isShown(acceptSubtitled) && filter.matches(it) })

    fun remainingSearches(): Int {
        rolloverQuota()
        return (SEARCH_LIMIT - searchesToday).coerceAtLeast(0)
    }

    /** Carica il feed: dalla cache se è recente e adatta agli interessi, altrimenti da YouTube. */
    fun refresh(modes: List<String>, interests: List<String>, force: Boolean = false) {
        if (!hasKey || job?.isActive == true) return
        rankInterests = (modes + interests).toSet()
        val candidates = FeedQueries.candidates(modes, interests, boosts)
        val sig = FeedQueries.signature(candidates) + followSignature()
        if (!force && pool.isNotEmpty() && loadedSig == sig) return

        val cache = readCache()
        val fresh = cache != null && cache.sig == sig && now() - cache.time < CACHE_TTL_MS
        if (!force && cache != null && fresh) {
            useCache(cache, sig)
            return
        }
        val remaining = remainingSearches()
        if (remaining <= 0 || (!force && cache != null && remaining < MIN_AUTO_SEARCHES)) {
            if (cache != null) {
                useCache(cache, sig)
                if (remaining <= 0) lastError = YtError(YtErrorKind.QuotaExceeded)
            } else {
                status = FeedStatus.Failed(YtError(YtErrorKind.QuotaExceeded))
            }
            return
        }
        cursor = 0
        val queries = FeedQueries.window(candidates, 0, minOf(FeedQueries.BATCH, remaining))
        cursor = queries.size
        fetch(queries, hint = null, replace = true, sig = sig)
    }

    /** Aggiunge altri video con le ricerche successive (orientate alla durata del filtro). Vero se ha avviato una ricerca. */
    fun loadMore(modes: List<String>, interests: List<String>, filter: FeedFilter): Boolean {
        if (!hasKey || busy || job?.isActive == true) return false
        rankInterests = (modes + interests).toSet()
        val remaining = remainingSearches()
        if (remaining <= 0) {
            if (lastError?.kind != YtErrorKind.QuotaExceeded) lastError = YtError(YtErrorKind.QuotaExceeded)
            return false
        }
        val candidates = FeedQueries.candidates(modes, interests, boosts)
        if (candidates.isEmpty()) return false
        val queries = FeedQueries.window(candidates, cursor, minOf(FeedQueries.BATCH, remaining))
        cursor += queries.size
        fetch(queries, hint = filter.durationHint, replace = false, sig = FeedQueries.signature(candidates) + followSignature())
        return true
    }

    /**
     * Esegue una chiamata a YouTube con la chiave API, letta dall'archivio cifrato solo qui.
     * Restituisce null se la chiave non c'è più. Va chiamata da una coroutine.
     */
    suspend fun <T> withKey(block: (key: String, client: CreatorClient) -> ApiResult<T>): ApiResult<T>? {
        val result = withContext(Dispatchers.IO) {
            val key = secure.get(KEY_NAME) ?: return@withContext null
            block(key, creatorClient)
        }
        if (result == null) hasKey = false
        return result
    }

    /** Registra ricerche consumate fuori dal feed (ricerca universale, ripiego dei creator). */
    fun noteSearches(n: Int) = addSearches(n)

    /** Se YouTube dice "quota finita", il contatore locale si allinea. */
    fun noteError(error: YtError) {
        if (error.kind == YtErrorKind.QuotaExceeded) markQuotaExhausted()
    }

    fun clearError() {
        lastError = null
    }

    private fun useCache(cache: Cache, sig: String) {
        pool.clear()
        pool.addAll(rankItems(cache.items.filter { it.id !in hiddenIds }))
        loadedSig = sig
        cursor = FeedQueries.BATCH
        status = FeedStatus.Ready
    }

    /** Cambia quando cambiano i creator seguiti che alimentano il feed: la cache non vale più. */
    private fun followSignature(): String =
        creators.feedSources(FOLLOWED_SOURCES).joinToString(",", prefix = "|f:") { it.id }

    /** Ordina i video con la classifica trasparente e ricorda i motivi di ciascuno. */
    private fun rankItems(items: List<VideoItem>): List<VideoItem> {
        if (items.isEmpty()) return items
        val ctx = RankContext(
            interests = rankInterests,
            profile = profile.snapshot(),
            seenIds = profile.seenIds,
            isFollowed = { creators.isFollowing(it) },
            exploration = profile.exploration,
            nowMs = now(),
        )
        val ranked = Recommender.rank(items, ctx)
        ranked.forEach { reasons[it.item.id] = it.reasons }
        return ranked.map { it.item }
    }

    /** Perché questo video compare: i motivi principali, oppure almeno l'argomento da cui arriva. */
    fun whyThis(item: VideoItem): List<String> =
        reasons[item.id]?.takeIf { it.isNotEmpty() } ?: listOfNotNull(item.topic?.let { "Argomento: $it" })

    /** Da chiamare quando il video è rimasto in vista abbastanza da dire che l'hai guardato davvero. */
    fun markWatched(item: VideoItem) = profile.onWatched(item)

    private fun fetch(queries: List<FeedQuery>, hint: String?, replace: Boolean, sig: String) {
        busy = true
        if (pool.isEmpty()) status = FeedStatus.Loading
        val followed = if (replace) creators.feedSources(FOLLOWED_SOURCES) else emptyList()
        job = scope.launch {
            val result = withContext(Dispatchers.IO) {
                val key = secure.get(KEY_NAME) ?: return@withContext null
                val searched = client.fetchFeed(key, queries, hint)
                if (followed.isEmpty()) {
                    searched
                } else {
                    // I video dei creator seguiti si leggono dalle playlist dei caricamenti: non consumano ricerche.
                    val own = (creatorClient.followedFeed(key, followed) as? ApiResult.Ok)?.value.orEmpty()
                    searched.copy(items = FeedQueries.interleave(listOf(own.take(FOLLOWED_MAX), searched.items)))
                }
            }
            busy = false
            if (result == null) {
                hasKey = false
                status = FeedStatus.Idle
                return@launch
            }
            addSearches(result.searchesDone)
            val fresh = result.items.filter { it.id !in hiddenIds }
            if (replace && fresh.isNotEmpty()) pool.clear()
            val known = pool.mapTo(HashSet()) { it.id }
            pool.addAll(rankItems(fresh.filter { it.id !in known }))
            if (pool.isNotEmpty()) {
                loadedSig = sig
                writeCache(sig, pool.take(CACHE_MAX))
            }

            val error = result.error
            if (error?.kind == YtErrorKind.QuotaExceeded) markQuotaExhausted()
            when {
                error != null && pool.isEmpty() -> status = FeedStatus.Failed(error)
                else -> {
                    status = FeedStatus.Ready
                    if (error != null) lastError = error
                }
            }
        }
    }

    // ------------------------------------------------------------------ azioni dell'utente

    fun isLiked(id: String) = id in likedIds
    fun isSaved(id: String) = saved.any { it.id == id }

    fun toggleLike(item: VideoItem) {
        likedIds = if (item.id in likedIds) likedIds - item.id else likedIds + item.id
        saveSet(K_LIKED, likedIds)
        profile.onLiked(item, item.id in likedIds)
    }

    fun toggleSave(item: VideoItem) {
        if (!saved.removeAll { it.id == item.id }) saved.add(0, item)
        prefs.edit().putString(K_SAVED, YouTubeParser.listToJson(saved)).apply()
        profile.onSaved(item, isSaved(item.id))
    }

    fun hide(item: VideoItem) {
        hiddenIds = (hiddenIds + item.id).toList().takeLast(HIDDEN_MAX).toCollection(LinkedHashSet())
        saveSet(K_HIDDEN, hiddenIds)
        profile.onHidden(item)
    }

    fun unhide(id: String) {
        hiddenIds = hiddenIds - id
        saveSet(K_HIDDEN, hiddenIds)
    }

    /** "Più contenuti così": il tema del video passa avanti nelle prossime ricerche. */
    fun moreLike(item: VideoItem): Boolean {
        val topic = item.topic ?: return false
        boosts = boosts + (topic to ((boosts[topic] ?: 0) + 1))
        val obj = JSONObject()
        boosts.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(K_BOOSTS, obj.toString()).apply()
        profile.onMoreLike(item)
        return true
    }

    // ------------------------------------------------------------------ quota

    private fun rolloverQuota() {
        val day = Format.pacificDay(now())
        if (prefs.getString(K_QDAY, null) != day) {
            prefs.edit().putString(K_QDAY, day).putInt(K_QCOUNT, 0).apply()
            searchesToday = 0
        } else {
            searchesToday = prefs.getInt(K_QCOUNT, 0)
        }
    }

    private fun addSearches(n: Int) {
        if (n <= 0) return
        rolloverQuota()
        searchesToday += n
        prefs.edit().putInt(K_QCOUNT, searchesToday).apply()
    }

    /** YouTube ha detto "quota finita": il contatore locale (una stima) si allinea. */
    private fun markQuotaExhausted() {
        rolloverQuota()
        searchesToday = SEARCH_LIMIT
        prefs.edit().putInt(K_QCOUNT, SEARCH_LIMIT).apply()
    }

    // ------------------------------------------------------------------ persistenza

    private class Cache(val time: Long, val sig: String, val items: List<VideoItem>)

    private fun readCache(): Cache? = runCatching {
        val obj = JSONObject(prefs.getString(K_CACHE, null) ?: return null)
        Cache(obj.getLong("t"), obj.getString("sig"), YouTubeParser.listFromJson(obj.optJSONArray("items")?.toString()))
    }.getOrNull()?.takeIf { it.items.isNotEmpty() }

    private fun writeCache(sig: String, items: List<VideoItem>) {
        val obj = JSONObject().put("t", now()).put("sig", sig).put("items", JSONArray(YouTubeParser.listToJson(items)))
        prefs.edit().putString(K_CACHE, obj.toString()).apply()
    }

    private fun loadSet(key: String): Set<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).mapTo(LinkedHashSet()) { arr.getString(it) }
    }.getOrDefault(emptySet())

    private fun saveSet(key: String, values: Set<String>) {
        prefs.edit().putString(key, JSONArray(values.toList()).toString()).apply()
    }

    private fun loadBoosts(): Map<String, Int> = runCatching {
        val obj = JSONObject(prefs.getString(K_BOOSTS, "{}"))
        obj.keys().asSequence().associateWith { obj.getInt(it) }
    }.getOrDefault(emptyMap())

    companion object {
        /** Tetto giornaliero ufficiale di search.list (bucket a parte da 100 chiamate). */
        const val SEARCH_LIMIT = 100
        private const val MIN_AUTO_SEARCHES = 8
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
        private const val CACHE_MAX = 150
        private const val HIDDEN_MAX = 3000
        private const val FOLLOWED_SOURCES = 12
        private const val FOLLOWED_MAX = 40

        const val KEY_NAME = "youtube_api_key"
        private const val K_SAVED = "saved"
        private const val K_LIKED = "liked"
        private const val K_HIDDEN = "hidden"
        private const val K_BOOSTS = "boosts"
        private const val K_CACHE = "cache"
        private const val K_QDAY = "quota_day"
        private const val K_QCOUNT = "quota_searches"
    }
}

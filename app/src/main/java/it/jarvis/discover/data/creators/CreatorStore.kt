package it.jarvis.discover.data.creators

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.jarvis.discover.data.youtube.VideoItem

/**
 * Creator seguiti e scelte dell'utente (livello, argomenti, notifiche). Salvati solo sul telefono.
 * L'ultima data di pubblicazione vista per le notifiche sta in una chiave a parte, perché la scrive
 * anche il lavoro in background.
 */
class CreatorStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val follows = mutableStateListOf<FollowedCreator>().apply {
        addAll(CreatorParser.followsFromJson(prefs.getString(K_FOLLOWS, null)))
    }

    /** Creator di cui è aperta la pagina (null = nessuna). */
    var page by mutableStateOf<CreatorRef?>(null); private set

    init {
        syncSchedule()
    }

    private fun map(): Map<String, FollowedCreator> = follows.associateBy { it.creator.id }

    fun isFollowing(id: String) = follows.any { it.creator.id == id }
    fun get(id: String): FollowedCreator? = follows.firstOrNull { it.creator.id == id }

    fun open(creator: Creator) { page = CreatorRef(creator.id, creator.title, creator) }

    /** Apre la pagina di un canale noto solo per id e nome (es. dall'autore di un video): i dettagli si caricano dopo. */
    fun open(channelId: String, title: String) {
        if (channelId.isBlank()) return
        page = CreatorRef(channelId, title, get(channelId)?.creator)
    }
    fun close() { page = null }

    fun follow(creator: Creator) {
        if (isFollowing(creator.id)) return
        follows.add(0, FollowedCreator(creator, followedAt = System.currentTimeMillis()))
        persist()
    }

    fun unfollow(id: String) {
        if (follows.removeAll { it.creator.id == id }) {
            prefs.edit().putString(K_SEEN, JsonSeen.remove(prefs.getString(K_SEEN, null), id)).apply()
            persist()
        }
    }

    private fun update(id: String, change: (FollowedCreator) -> FollowedCreator) {
        val i = follows.indexOfFirst { it.creator.id == id }
        if (i >= 0) {
            follows[i] = change(follows[i])
            persist()
        }
    }

    fun setLevel(id: String, level: FollowLevel) = update(id) { it.copy(level = level) }

    fun setTopics(id: String, topics: List<String>) =
        update(id) { it.copy(topics = topics.map(String::trim).filter(String::isNotBlank).distinct().take(MAX_TOPICS)) }

    fun setNotify(id: String, notify: Boolean) = update(id) { it.copy(notify = notify) }

    /** Applica ai video le scelte sui creator seguiti (esclusi, argomenti, "meno spesso"). Legge stato Compose. */
    fun filter(videos: List<VideoItem>): List<VideoItem> = CreatorRules.apply(videos, map())

    /** Creator da cui prendere video per il feed: i seguiti non esclusi, i più recenti per primi. */
    fun feedSources(limit: Int): List<Creator> =
        follows.filter { it.level != FollowLevel.Excluded }.take(limit).map { it.creator }

    private fun persist() {
        prefs.edit().putString(K_FOLLOWS, CreatorParser.followsToJson(follows)).apply()
        syncSchedule()
    }

    private fun syncSchedule() {
        CreatorUpdates.schedule(appContext, enabled = follows.any { it.notify })
    }

    companion object {
        const val PREFS = "discover_creators"
        const val K_FOLLOWS = "follows"
        const val K_SEEN = "last_seen"
        const val MAX_TOPICS = 8
    }
}

/** Riferimento a un canale da aprire; [creator] è presente solo se i dettagli sono già noti. */
data class CreatorRef(val id: String, val title: String, val creator: Creator?)

/** Ultima data di pubblicazione già vista per creator (ISO 8601, ordinabile come testo). */
object JsonSeen {
    fun read(json: String?): Map<String, String> = runCatching {
        val o = org.json.JSONObject(json ?: return emptyMap())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun write(map: Map<String, String>): String {
        val o = org.json.JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }

    fun remove(json: String?, id: String): String = write(read(json) - id)
}

package it.jarvis.discover.data.reco

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.jarvis.discover.data.youtube.VideoItem
import it.jarvis.discover.data.youtube.YouTubeParser
import org.json.JSONArray
import org.json.JSONObject

enum class DurationBucket(val label: String) {
    Short("brevi (fino a 5 minuti)"),
    Medium("medi (da 5 a 20 minuti)"),
    Long("lunghi (oltre 20 minuti)");

    companion object {
        fun of(sec: Int) = when {
            sec <= 5 * 60 -> Short
            sec <= 20 * 60 -> Medium
            else -> Long
        }
    }
}

/** Quanto spazio dare agli argomenti nuovi: ogni N-esimo video è una proposta per allargare gli orizzonti. */
enum class Exploration(val label: String, val every: Int) {
    Off("Nessuna", 0),
    Low("Poche", 8),
    Medium("Medie", 5),
    High("Molte", 3);

    companion object { fun fromName(n: String?) = entries.firstOrNull { it.name == n } ?: Medium }
}

/** Ciò che l'app ha imparato di te, in forma che il recommender può leggere. */
data class ProfileSnapshot(
    val topics: Map<String, Double> = emptyMap(),
    val channels: Map<String, Double> = emptyMap(),
    val durations: Map<DurationBucket, Int> = emptyMap(),
) {
    companion object { val Empty = ProfileSnapshot() }
}

data class HistoryEntry(val video: VideoItem, val at: Long)

object Topics {
    fun norm(topic: String?): String = topic.orEmpty().trim().lowercase()
}

/**
 * Il "profilo" di Discover: argomenti e creator che hai premiato o scartato, durate che guardi, cronologia.
 * Resta sul telefono, si può correggere voce per voce, spegnere (l'app smette di imparare) o azzerare.
 */
class ProfileStore(
    initial: String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val persist: (String) -> Unit,
) {
    var learning by mutableStateOf(true); private set
    var exploration by mutableStateOf(Exploration.Medium); private set
    var topics by mutableStateOf<Map<String, Double>>(emptyMap()); private set
    var channels by mutableStateOf<Map<String, Double>>(emptyMap()); private set
    var durations by mutableStateOf<Map<DurationBucket, Int>>(emptyMap()); private set
    /** Nomi dei creator di cui si hanno pesi, per mostrarli per nome. */
    var channelNames by mutableStateOf<Map<String, String>>(emptyMap()); private set
    val history = mutableStateListOf<HistoryEntry>()

    init { decode(initial) }

    fun snapshot() = ProfileSnapshot(topics, channels, durations)
    val seenIds: Set<String> get() = history.mapTo(HashSet()) { it.video.id }
    val isEmpty: Boolean get() = topics.isEmpty() && channels.isEmpty() && durations.isEmpty() && history.isEmpty()

    // ---- segnali
    fun onWatched(v: VideoItem) {
        if (!learning) return
        // Rivedere lo stesso video non rafforza di nuovo i segnali: aggiorna solo la data in cronologia.
        val already = history.any { it.video.id == v.id }
        history.removeAll { it.video.id == v.id }
        history.add(0, HistoryEntry(v, now()))
        while (history.size > MAX_HISTORY) history.removeAt(history.size - 1)
        if (already) save() else update(v, topic = 1.0, channel = 0.6, bucketDelta = 1)
    }

    fun onLiked(v: VideoItem, liked: Boolean) = update(v, topic = if (liked) 2.0 else -2.0, channel = if (liked) 1.0 else -1.0, bucketDelta = if (liked) 1 else 0)
    fun onSaved(v: VideoItem, saved: Boolean) = update(v, topic = if (saved) 3.0 else -3.0, channel = if (saved) 1.5 else -1.5, bucketDelta = if (saved) 1 else 0)
    fun onMoreLike(v: VideoItem) = update(v, topic = 3.0, channel = 0.0, bucketDelta = 0)
    fun onHidden(v: VideoItem) = update(v, topic = -2.5, channel = -2.0, bucketDelta = 0)

    private fun update(v: VideoItem, topic: Double, channel: Double, bucketDelta: Int) {
        if (!learning) return
        val t = Topics.norm(v.topic)
        val newTopics = decay(topics).toMutableMap()
        if (t.isNotEmpty() && topic != 0.0) newTopics[t] = ((newTopics[t] ?: 0.0) + topic).coerceIn(-LIMIT, LIMIT)
        val newChannels = decay(channels).toMutableMap()
        if (v.channelId.isNotBlank() && channel != 0.0) {
            newChannels[v.channelId] = ((newChannels[v.channelId] ?: 0.0) + channel).coerceIn(-LIMIT, LIMIT)
            if (v.channelTitle.isNotBlank()) channelNames = channelNames + (v.channelId to v.channelTitle)
        }
        topics = prune(newTopics)
        channels = prune(newChannels)
        if (bucketDelta != 0) {
            val b = DurationBucket.of(v.durationSec)
            durations = durations + (b to ((durations[b] ?: 0) + bucketDelta))
        }
        save()
    }

    // ---- correzioni dell'utente
    fun changeLearning(on: Boolean) { learning = on; save() }
    fun changeExploration(e: Exploration) { exploration = e; save() }

    fun adjustTopic(topic: String, delta: Double) {
        val t = Topics.norm(topic)
        topics = prune(topics + (t to ((topics[t] ?: 0.0) + delta).coerceIn(-LIMIT, LIMIT)))
        save()
    }

    fun removeTopic(topic: String) { topics = topics - Topics.norm(topic); save() }
    fun removeChannel(id: String) { channels = channels - id; save() }
    fun removeFromHistory(id: String) { history.removeAll { it.video.id == id }; save() }
    fun clearHistory() { history.clear(); save() }

    /** Dimentica ciò che ha imparato (argomenti, creator, durate). Cronologia e impostazioni restano. */
    fun resetLearning() { topics = emptyMap(); channels = emptyMap(); channelNames = emptyMap(); durations = emptyMap(); save() }

    /** Azzera tutto il profilo: pesi, durate, cronologia; le impostazioni tornano a quelle iniziali. */
    fun resetAll() {
        topics = emptyMap(); channels = emptyMap(); channelNames = emptyMap(); durations = emptyMap()
        history.clear()
        learning = true
        exploration = Exploration.Medium
        save()
    }

    // ---- persistenza
    private fun save() = persist(encode())

    fun encode(): String {
        val o = JSONObject()
        o.put("learning", learning).put("explore", exploration.name)
        o.put("topics", JSONObject(topics.mapValues { it.value }))
        o.put("channels", JSONObject(channels.mapValues { it.value }))
        o.put("names", JSONObject(channelNames.filterKeys { it in channels }))
        o.put("dur", JSONObject(durations.entries.associate { it.key.name to it.value }))
        val h = JSONArray()
        history.forEach { h.put(JSONObject().put("v", YouTubeParser.toJson(it.video)).put("at", it.at)) }
        o.put("hist", h)
        return o.toString()
    }

    private fun decode(raw: String?) {
        if (raw.isNullOrBlank()) return
        runCatching {
            val o = JSONObject(raw)
            learning = o.optBoolean("learning", true)
            exploration = Exploration.fromName(o.optString("explore"))
            topics = readMap(o.optJSONObject("topics"))
            channels = readMap(o.optJSONObject("channels"))
            channelNames = o.optJSONObject("names")?.let { n -> n.keys().asSequence().associateWith { n.optString(it) } } ?: emptyMap()
            durations = o.optJSONObject("dur")?.let { d ->
                DurationBucket.entries.mapNotNull { b -> if (d.has(b.name)) b to d.optInt(b.name) else null }.toMap()
            } ?: emptyMap()
            val h = o.optJSONArray("hist")
            if (h != null) for (i in 0 until h.length()) {
                val e = h.optJSONObject(i) ?: continue
                val v = e.optJSONObject("v")?.let(YouTubeParser::fromJson) ?: continue
                history += HistoryEntry(v, e.optLong("at"))
            }
        }
    }

    private fun readMap(o: JSONObject?): Map<String, Double> =
        o?.keys()?.asSequence()?.associateWith { o.optDouble(it) }?.filterValues { !it.isNaN() } ?: emptyMap()

    private fun decay(m: Map<String, Double>) = m.mapValues { it.value * DECAY }
    private fun prune(m: Map<String, Double>) = m.filterValues { kotlin.math.abs(it) >= 0.05 }

    companion object {
        const val MAX_HISTORY = 100
        const val LIMIT = 12.0
        /** Ogni nuovo segnale rende un po' meno importanti i vecchi: i gusti cambiano. */
        const val DECAY = 0.995

        fun from(context: Context): ProfileStore {
            val prefs = context.applicationContext.getSharedPreferences("discover_profile", Context.MODE_PRIVATE)
            return ProfileStore(prefs.getString("data", null)) { prefs.edit().putString("data", it).apply() }
        }
    }
}

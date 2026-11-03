package it.jarvis.discover.data.creators

import it.jarvis.discover.data.youtube.VideoItem

/** Un canale YouTube (persona, divulgatore, conferenza...). Dati reali restituiti da YouTube. */
data class Creator(
    val id: String,
    val title: String,
    val handle: String?,
    val description: String,
    val thumbnail: String,
    val subscribers: Long?,
    val videoCount: Long?,
    /** Playlist con tutti i caricamenti del canale. */
    val uploadsPlaylistId: String,
) {
    val channelUrl: String get() = handle?.let { "https://www.youtube.com/$it" } ?: "https://www.youtube.com/channel/$id"
}

/** Quanto spazio dare ai contenuti di un creator seguito. */
enum class FollowLevel(val label: String) {
    Normal("Normale"),
    Reduced("Meno spesso"),
    Excluded("Escludi"),
}

/** Creator seguito, con le scelte dell'utente. Salvato solo sul telefono. */
data class FollowedCreator(
    val creator: Creator,
    val followedAt: Long,
    val level: FollowLevel = FollowLevel.Normal,
    /** Se non vuoto, dei suoi video si vedono solo quelli che nominano almeno una di queste parole. */
    val topics: List<String> = emptyList(),
    val notify: Boolean = false,
)

/** Risultato della ricerca universale: canali e video trovati con la stessa richiesta. */
data class UniversalResult(val creators: List<Creator>, val videos: List<VideoItem>)

/** Regole (pure, testabili) che decidono se e quanto un video di un creator seguito compare nel feed. */
object CreatorRules {

    /** Ogni quanti video di un creator "meno spesso" ne resta uno. */
    const val REDUCED_KEEP_EVERY = 3

    fun matchesTopics(video: VideoItem, topics: List<String>): Boolean {
        if (topics.isEmpty()) return true
        val haystack = (video.title + " " + video.description).lowercase()
        return topics.any { t -> t.isNotBlank() && haystack.contains(t.trim().lowercase()) }
    }

    /**
     * Applica ai [videos] le scelte dell'utente sui creator seguiti: esclusi via, argomenti
     * filtrati, "meno spesso" ridotti a un video ogni [REDUCED_KEEP_EVERY]. L'ordine si mantiene.
     */
    fun apply(videos: List<VideoItem>, follows: Map<String, FollowedCreator>): List<VideoItem> {
        if (follows.isEmpty()) return videos
        val seen = HashMap<String, Int>()
        return videos.filter { v ->
            val f = follows[v.channelId] ?: return@filter true
            when (f.level) {
                FollowLevel.Excluded -> false
                else -> {
                    if (!matchesTopics(v, f.topics)) return@filter false
                    if (f.level == FollowLevel.Reduced) {
                        val n = seen.getOrDefault(v.channelId, 0)
                        seen[v.channelId] = n + 1
                        n % REDUCED_KEEP_EVERY == 0
                    } else true
                }
            }
        }
    }

    /** "UC123" → "UU123": id della playlist dei caricamenti, quando YouTube non lo dichiara. */
    fun uploadsIdFor(channelId: String): String =
        if (channelId.startsWith("UC") && channelId.length > 2) "UU" + channelId.substring(2) else channelId
}

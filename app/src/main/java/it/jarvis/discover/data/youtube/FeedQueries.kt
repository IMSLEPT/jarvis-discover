package it.jarvis.discover.data.youtube

import it.jarvis.discover.data.moodById

/**
 * Trasforma modalità e interessi scelti dall'utente in ricerche YouTube. Le ricerche sono il bene
 * più prezioso della quota gratuita (100 al giorno), quindi si usano a finestre di poche alla volta.
 */
object FeedQueries {

    /** Ricerche di partenza per ogni modalità (l'ultima, "Per te", usa gli interessi). */
    val MoodSeeds: Map<String, List<String>> = mapOf(
        "motivami" to listOf("discorso motivazionale", "storie di successo e riscatto", "disciplina e abitudini"),
        "insegnami" to listOf("divulgazione scientifica", "intelligenza artificiale spiegata", "psicologia spiegata semplice"),
        "riflettere" to listOf("filosofia lezione", "discorso che fa riflettere", "TEDx italiano"),
        "stare_bene" to listOf("storie che ispirano", "gentilezza storie vere", "buone notizie"),
        "rilassami" to listOf("meditazione guidata", "mindfulness", "respirazione e rilassamento"),
        "idee" to listOf("creatività e nuove idee", "innovazione e progetti", "come iniziare un progetto"),
        "leggere" to listOf("recensione libro", "audiolibro", "conferenza con autore"),
        "sorprendimi" to listOf("curiosità incredibili", "scoperte sorprendenti", "cose che non sapevi"),
    )

    /** Se l'utente non ha scelto né modalità né interessi. */
    val Defaults: List<String> = listOf(
        "divulgazione scientifica",
        "crescita personale",
        "TEDx italiano",
        "cultura generale curiosità",
    )

    /** Quante ricerche si fanno per volta (ognuna costa 1 delle 100 giornaliere). */
    const val BATCH = 4

    /**
     * Elenco completo delle ricerche candidate, in ordine di priorità: prima gli argomenti che
     * l'utente ha premiato con "Più contenuti così", poi alternando modalità e interessi.
     */
    fun candidates(modeIds: List<String>, interests: List<String>, boosts: Map<String, Int> = emptyMap()): List<FeedQuery> {
        val moodQueries: List<FeedQuery> = modeIds
            .filter { it != "per_te" }
            .flatMap { id ->
                val title = moodById(id)?.title ?: id
                MoodSeeds[id].orEmpty().map { FeedQuery(it, title) }
            }
        val interestQueries = interests.map { FeedQuery(it, it) }

        val mixed = ArrayList<FeedQuery>()
        var i = 0
        while (i < moodQueries.size || i < interestQueries.size) {
            moodQueries.getOrNull(i)?.let(mixed::add)
            interestQueries.getOrNull(i)?.let(mixed::add)
            i++
        }
        val base = if (mixed.isEmpty()) Defaults.map { FeedQuery(it, "Per te") } else mixed

        return base
            .distinctBy { it.text.lowercase() }
            .sortedByDescending { boosts[it.topic] ?: 0 } // sortedBy è stabile: a parità resta l'ordine alternato
    }

    /** Finestra circolare di [size] ricerche a partire da [start]. */
    fun window(list: List<FeedQuery>, start: Int, size: Int = BATCH): List<FeedQuery> {
        if (list.isEmpty()) return emptyList()
        val n = minOf(size, list.size)
        return List(n) { list[(start + it).mod(list.size)] }
    }

    /** Impronta delle ricerche: se cambia, la cache non è più adatta agli interessi attuali. */
    fun signature(queries: List<FeedQuery>): String = queries.joinToString("|") { it.text.lowercase() }.hashCode().toString()

    /**
     * Unisce i risultati di più ricerche alternandoli, così il feed non è un blocco di un solo
     * argomento; limita a [maxPerChannel] i video dello stesso canale.
     */
    fun interleave(perQuery: List<List<VideoItem>>, maxPerChannel: Int = 3): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        val seen = HashSet<String>()
        val perChannel = HashMap<String, Int>()
        var index = 0
        while (perQuery.any { index < it.size }) {
            for (list in perQuery) {
                val item = list.getOrNull(index) ?: continue
                if (!seen.add(item.id)) continue
                val n = perChannel.getOrDefault(item.channelId, 0)
                if (item.channelId.isNotBlank() && n >= maxPerChannel) continue
                perChannel[item.channelId] = n + 1
                out += item
            }
            index++
        }
        return out
    }
}

package it.jarvis.discover.data.reco

import it.jarvis.discover.data.youtube.LangClass
import it.jarvis.discover.data.youtube.VideoItem
import java.time.Instant
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.tanh

/** Tutto ciò che serve a ordinare i video: interessi, profilo e un orologio. */
data class RankContext(
    val interests: Set<String>,
    val profile: ProfileSnapshot,
    val seenIds: Set<String>,
    val isFollowed: (channelId: String) -> Boolean,
    val exploration: Exploration,
    val nowMs: Long,
) {
    /** Argomenti scelti (interessi e modalità), normalizzati. */
    val interestSet: Set<String> = interests.map(Topics::norm).toSet()
}

data class Ranked(val item: VideoItem, val score: Double, val reasons: List<String>, val exploratory: Boolean)

/**
 * Ordina i video con regole semplici e visibili, non con una scatola nera. Ogni video riceve punti per:
 * interessi scelti, argomenti e creator che hai premiato, durata abituale, freschezza e qualità (apprezzamenti,
 * sottotitoli, lingua certa); perde punti se sembra clickbait o l'hai già visto. Poi si rimescola per evitare
 * ripetizioni (stesso argomento o stesso canale di fila) e ogni tanto si inserisce un argomento nuovo.
 * L'app non può verificare se un video dice il vero: la qualità qui è solo un indizio.
 */
object Recommender {

    // Pesi: sono mostrati all'utente nella schermata "Come funziona".
    const val W_INTEREST = 1.0
    const val W_TOPIC = 0.8
    const val W_CHANNEL = 0.6
    const val W_FOLLOWED = 0.5
    const val W_DURATION = 0.5
    const val W_FRESH = 0.3
    const val W_QUALITY = 0.5
    const val W_CLICKBAIT = 0.8
    const val P_SEEN = 2.0
    const val P_SAME_TOPIC = 0.35
    const val P_SAME_CHANNEL = 0.5

    private const val MIN_REASON = 0.12
    private const val MIN_EXPLORE_QUALITY = 0.35

    private class Part(val value: Double, val text: String)

    private class Scored(val item: VideoItem, val total: Double, val parts: List<Part>, val quality: Double, val clickbait: Double, val topicKey: String)

    fun rank(items: List<VideoItem>, ctx: RankContext): List<Ranked> {
        val remaining = items.map { score(it, ctx) }.toMutableList()
        val picked = ArrayList<Ranked>()
        val pickedKeys = ArrayList<Pair<String, String>>() // (argomento, canale)
        var position = 0
        while (remaining.isNotEmpty()) {
            position++
            val every = ctx.exploration.every
            var choice: Scored? = null
            var exploratory = false
            if (every > 0 && position % every == 0) {
                choice = remaining.filter { isExploratory(it, ctx) }.maxWithOrNull(compareBy<Scored> { adjusted(it, pickedKeys) })
                exploratory = choice != null
            }
            if (choice == null) choice = remaining.maxWithOrNull(compareBy<Scored> { adjusted(it, pickedKeys) })!!
            remaining.remove(choice)
            pickedKeys += choice.topicKey to choice.item.channelId
            val reasons = if (exploratory) listOf("Per allargare i tuoi orizzonti: un argomento nuovo per te") + topReasons(choice, 1)
            else topReasons(choice, 3)
            picked += Ranked(choice.item, choice.total, reasons, exploratory)
        }
        return picked
    }

    // ---- punteggio
    private fun score(v: VideoItem, ctx: RankContext): Scored {
        val parts = ArrayList<Part>()
        val topic = Topics.norm(v.topic)

        if (topic.isNotEmpty() && topic in ctx.interestSet) parts += Part(W_INTEREST, "Corrisponde a un tuo interesse: «${v.topic}»")

        val tw = ctx.profile.topics[topic] ?: 0.0
        if (tw != 0.0) {
            val x = W_TOPIC * tanh(tw / 4.0)
            parts += Part(x, if (x > 0) "Hai apprezzato altri contenuti su «${v.topic}»" else "Hai scartato contenuti su «${v.topic}»")
        }

        val cw = ctx.profile.channels[v.channelId] ?: 0.0
        if (cw != 0.0) {
            val x = W_CHANNEL * tanh(cw / 4.0)
            parts += Part(x, if (x > 0) "Hai già apprezzato video di ${v.channelTitle}" else "Hai scartato video di ${v.channelTitle}")
        }
        if (v.channelId.isNotBlank() && ctx.isFollowed(v.channelId)) parts += Part(W_FOLLOWED, "Dal creator che segui: ${v.channelTitle}")

        val total = ctx.profile.durations.values.sum()
        if (total >= 5) {
            val share = (ctx.profile.durations[DurationBucket.of(v.durationSec)] ?: 0).toDouble() / total
            val x = W_DURATION * (share - 1.0 / 3.0) * 1.5
            if (x > 0) parts += Part(x, "Ha la durata che di solito guardi")
            else if (x < 0) parts += Part(x, "Dura più o meno di quanto guardi di solito")
        }

        val age = ageDays(v, ctx.nowMs)
        if (age != null) {
            val fresh = W_FRESH * exp(-age / 365.0)
            parts += Part(fresh, if (age <= 30) "Pubblicato da poco" else "Contenuto recente")
        }

        val quality = quality(v)
        parts += Part(W_QUALITY * quality, "Buoni segnali di qualità (apprezzamenti, sottotitoli, lingua)")

        val bait = Quality.clickbait(v.title)
        if (bait > 0) parts += Part(-W_CLICKBAIT * bait, "Il titolo sembra clickbait")

        if (v.id in ctx.seenIds) parts += Part(-P_SEEN, "Lo hai già visto")

        return Scored(v, parts.sumOf { it.value }, parts, quality, bait, topic)
    }

    private fun quality(v: VideoItem): Double {
        val views = v.views ?: 0L
        val likes = v.likes ?: 0L
        val ratio = if (views >= 1000) (likes.toDouble() / views / 0.04).coerceIn(0.0, 1.0) else 0.3
        val reach = (log10(maxOf(views, 1L).toDouble()) / 6.0).coerceIn(0.0, 1.0)
        return (0.5 * ratio + 0.2 * reach + (if (v.hasCaptions) 0.15 else 0.0) + (if (v.lang == LangClass.Italian) 0.15 else 0.0)).coerceIn(0.0, 1.0)
    }

    private fun ageDays(v: VideoItem, nowMs: Long): Double? = runCatching {
        (nowMs - Instant.parse(v.publishedAt).toEpochMilli()).coerceAtLeast(0) / 86_400_000.0
    }.getOrNull()

    // ---- diversità e novità
    private fun adjusted(s: Scored, picked: List<Pair<String, String>>): Double {
        val recent = picked.takeLast(6)
        val sameTopic = recent.takeLast(4).count { it.first.isNotEmpty() && it.first == s.topicKey }
        val sameChannel = recent.count { it.second.isNotBlank() && it.second == s.item.channelId }
        return s.total - P_SAME_TOPIC * sameTopic - P_SAME_CHANNEL * sameChannel
    }

    /** Una proposta "nuova": argomento fuori dai tuoi interessi e senza storia con te, ma di buona qualità e non clickbait. */
    private fun isExploratory(s: Scored, ctx: RankContext): Boolean {
        val affinity = ctx.profile.topics[s.topicKey] ?: 0.0
        return s.topicKey !in ctx.interestSet && affinity < 0.5 &&
            s.item.id !in ctx.seenIds && s.quality >= MIN_EXPLORE_QUALITY && s.clickbait < 0.34
    }

    private fun topReasons(s: Scored, n: Int): List<String> =
        s.parts.filter { it.value >= MIN_REASON }.sortedByDescending { it.value }.take(n).map { it.text }
}

/** Indizi di qualità del titolo. Sono regole semplici: possono sbagliare e non dicono nulla sulla verità dei contenuti. */
object Quality {
    private val BAIT = listOf(
        "non crederai", "incredibile", "shock", "sconvolgente", "devi vedere", "prima che lo cancellino",
        "nessuno ti dice", "il segreto che", "ti cambierà la vita", "troppo forte", "100%", "virale",
    )

    /** Da 0 (nessun indizio) a 1 (molti indizi). */
    fun clickbait(title: String): Double {
        var score = 0.0
        val letters = title.filter { it.isLetter() }
        if (letters.length >= 12 && letters.count { it.isUpperCase() }.toDouble() / letters.length > 0.6) score += 0.4
        if (title.contains("!!") || title.contains("?!") || title.contains("!?")) score += 0.2
        val lower = title.lowercase()
        score += BAIT.count { it in lower } * 0.3
        return score.coerceIn(0.0, 1.0)
    }
}

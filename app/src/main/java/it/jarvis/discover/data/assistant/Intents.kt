package it.jarvis.discover.data.assistant

import it.jarvis.discover.data.wellbeing.Session
import it.jarvis.discover.data.wellbeing.SessionCategory
import java.text.Normalizer
import kotlin.math.abs

/** Cosa ha chiesto l'utente, capito con regole sulla lingua italiana (nessuna IA, nessuna rete). */
sealed interface AssistantIntent {
    data object Help : AssistantIntent
    data class Videos(
        val topic: String?,
        val person: String?,
        val count: Int,
        val ted: Boolean = false,
        val speech: Boolean = false,
    ) : AssistantIntent
    data class Books(val topic: String?, val count: Int) : AssistantIntent
    data class Audiobooks(val topic: String?, val count: Int) : AssistantIntent
    data class Podcasts(val topic: String?, val count: Int) : AssistantIntent
    data class News(val count: Int) : AssistantIntent
    data class Mindfulness(val minutes: Int?, val category: SessionCategory?) : AssistantIntent
    data class LearnTopic(val topic: String) : AssistantIntent
    data class LearnQuick(val minutes: Int, val topic: String?) : AssistantIntent
    data object ExplainVideo : AssistantIntent
    /** Domanda libera: senza IA l'assistente non può rispondere da solo. */
    data class Chat(val text: String) : AssistantIntent
}

object ItalianText {
    private val accents = Regex("\\p{Mn}+")

    /** Minuscolo, senza accenti, apostrofi normalizzati e spazi singoli: per confrontare il testo con le regole. */
    fun fold(text: String): String =
        accents.replace(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD), "")
            .replace('’', '\'').replace('`', '\'')
            .replace(Regex("\\s+"), " ").trim()

    val numberWords: Map<String, Int> = mapOf(
        "un" to 1, "uno" to 1, "una" to 1, "due" to 2, "tre" to 3, "quattro" to 4, "cinque" to 5, "sei" to 6,
        "sette" to 7, "otto" to 8, "nove" to 9, "dieci" to 10, "quindici" to 15, "venti" to 20, "trenta" to 30,
    )

    private val numberPattern = "(\\d{1,3}|" + numberWords.keys.joinToString("|") + ")"
    private val minutesRegex = Regex("\\b$numberPattern\\s*(?:minut\\w*|min\\b)")
    private val countRegex = Regex(
        "\\b$numberPattern\\s+(?:[a-z']+\\s+){0,2}?(?:video|ted|talk|conferenz\\w*|libr\\w*|audiolibr\\w*|podcast|notizi\\w*|discors\\w*|lezion\\w*|intervist\\w*)",
    )

    private fun toNumber(token: String): Int? = token.toIntOrNull() ?: numberWords[token]

    /** "dieci minuti", "5 min" → numero. Il testo va passato già "foldato". */
    fun minutesIn(folded: String): Int? =
        minutesRegex.find(folded)?.groupValues?.get(1)?.let(::toNumber)?.takeIf { it in 1..180 }

    /** "cinque video", "3 libri" → numero (1–10). Se non c'è, [default]. */
    fun countIn(folded: String, default: Int = 5): Int =
        countRegex.find(folded)?.groupValues?.get(1)?.let(::toNumber)?.takeIf { it in 1..10 } ?: default

    private val trailing = Regex(
        "\\s+(?:in italiano|in lingua italiana|in lingua|oggi|adesso|ora|subito|per favore|per piacere|grazie|di oggi|in\\s+" + numberPattern + "\\s+minut\\w*)$",
    )
    private val leadingArticle = Regex("^(?:l'|un'|(?:il|lo|la|i|gli|le|un|uno|una)\\s+)")
    // Le preposizioni con apostrofo si attaccano alla parola ("sull'ai"); le altre richiedono uno spazio ("sulla mente").
    private val prepositions = Regex(
        "(?:^|\\s)(?:(?:sull'|dell'|d')|(?:sulla|sullo|sulle|sugli|sui|sul|su|della|dello|delle|dei|degli|del|di|riguardo a|riguardo|in tema di|per)\\s+)([^,.?!;]+)",
    )
    private val onlyOn = Regex(
        "(?:^|\\s)(?:(?:sull')|(?:sulla|sullo|sulle|sugli|sui|sul|su|riguardo a|riguardo|in tema di)\\s+)([^,.?!;]+)",
    )

    /**
     * Argomento della richiesta: ciò che segue l'ultima preposizione ("libro sulla mente" → "mente").
     * Con [onlyOnPrepositions] si considerano solo "su/sul/sulla…", utile quando "di" fa parte di altre espressioni.
     */
    fun topicOf(folded: String, onlyOnPrepositions: Boolean = false): String? {
        val m = (if (onlyOnPrepositions) onlyOn else prepositions).findAll(folded).lastOrNull() ?: return null
        var t = m.groupValues[1].trim()
        var changed = true
        while (changed) {
            val before = t
            t = trailing.replace(t, "").trim()
            changed = t != before
        }
        t = leadingArticle.replace(t, "").trim()
        return t.takeIf { it.length >= 2 }
    }

    private val personRegex = Regex("""\b(?:di|da|con)\s+((?:[A-ZÀ-Ý][\p{L}'’.-]*\s?){1,3})""")

    /** Nome proprio dopo "di/da/con", scritto con le maiuscole ("video di Raffaele Gaito" → "Raffaele Gaito"). */
    fun personIn(original: String): String? =
        personRegex.findAll(original).map { it.groupValues[1].trim() }
            .firstOrNull { it.length >= 3 && it.split(' ').any { w -> w.length >= 3 } && it.lowercase() !in setOf("ted", "youtube") }
            ?.removeSuffix(".")
}

object IntentParser {
    private fun has(f: String, vararg words: String) = words.any { Regex("\\b$it").containsMatchIn(f) }

    fun parse(text: String): AssistantIntent {
        val original = text.trim()
        val f = ItalianText.fold(original)
        if (f.isBlank()) return AssistantIntent.Help

        if (f.split(' ').size <= 4 &&
            Regex("^(ciao|salve|buongiorno|buonasera|buonanotte|hey|ehi|aiuto|help|cosa sai fare|che cosa sai fare|cosa puoi fare|che cosa puoi fare|come funzioni|come ti uso)\\b").containsMatchIn(f)
        ) {
            return AssistantIntent.Help
        }

        // Spiegazione del video che sto guardando
        if (has(f, "spiega", "spiegami", "riassum", "di cosa parla", "cosa dice", "che cosa dice", "cosa racconta") &&
            has(f, "video", "questo", "quello")
        ) return AssistantIntent.ExplainVideo

        // Domande di definizione o di spiegazione generale: risposta libera, non una ricerca
        if (Regex("^(?:cos'e|che cos'e|cosa e|che cosa e|cosa significa|che significa|cosa vuol dire|che vuol dire|perche|come mai|chi e|chi era|spiega|spiegami)\\b").containsMatchIn(f)) {
            return AssistantIntent.Chat(original)
        }

        // Mindfulness e rilassamento: ha la precedenza perché le parole sono molto specifiche
        if (has(f, "mindfulness", "meditaz", "meditare", "medita", "rilassamento", "rilassarmi", "respirazione", "respiro", "sessione guidata")) {
            return AssistantIntent.Mindfulness(ItalianText.minutesIn(f), categoryFor(f))
        }
        if (has(f, "addormentarmi", "dormire", "conciliare il sonno", "prima di dormire") ||
            (has(f, "stress", "ansia", "ansios", "nervos", "agitat") && has(f, "aiut", "calmar", "fare", "fammi", "esercizio", "sessione"))
        ) {
            return AssistantIntent.Mindfulness(ItalianText.minutesIn(f), categoryFor(f))
        }

        if (has(f, "notizi", "news") && !has(f, "video")) return AssistantIntent.News(ItalianText.countIn(f))

        if (has(f, "audiolibr", "audiobook") || (has(f, "ascolt") && has(f, "libro", "libri"))) {
            return AssistantIntent.Audiobooks(ItalianText.topicOf(f), ItalianText.countIn(f, 4))
        }
        if (has(f, "podcast")) return AssistantIntent.Podcasts(ItalianText.topicOf(f), ItalianText.countIn(f, 4))
        if (has(f, "libro", "libri", "romanzo", "romanzi", "saggio", "saggi", "leggere", "lettura") && !has(f, "video")) {
            return AssistantIntent.Books(ItalianText.topicOf(f), ItalianText.countIn(f, 4))
        }

        if (has(f, "motivazional", "motivarmi", "motivazione", "ispirar", "ispirazione", "discorso", "discorsi")) {
            val topic = ItalianText.topicOf(f)
            return AssistantIntent.Videos(topic = topic, person = ItalianText.personIn(original), count = ItalianText.countIn(f, 4), speech = true)
        }

        // Imparare qualcosa in poco tempo
        val minutes = ItalianText.minutesIn(f)
        if (has(f, "imparare", "impara", "scoprire", "scopro") && minutes != null &&
            !Regex("\\b(?:voglio|vorrei|devo)\\s+imparare\\s+a\\b").containsMatchIn(f)
        ) {
            return AssistantIntent.LearnQuick(minutes, ItalianText.topicOf(f, onlyOnPrepositions = true))
        }
        if (Regex("\\bcosa (?:posso|potrei) (?:imparare|scoprire)").containsMatchIn(f)) {
            return AssistantIntent.LearnQuick(minutes ?: 10, ItalianText.topicOf(f, onlyOnPrepositions = true))
        }

        learnRegex.find(f)?.let { m ->
            val t = m.groupValues[1].trim().trimEnd('.', '!', '?')
            if (t.length >= 3) return AssistantIntent.LearnTopic(t)
        }

        if (has(f, "video", "ted\\b", "tedx", "conferenz", "talk", "intervist", "lezion", "guardare", "vedere", "mostrami", "mostrarmi")) {
            val ted = Regex("\\bted(?:x)?\\b").containsMatchIn(f)
            val person = ItalianText.personIn(original)
            val topic = ItalianText.topicOf(f, onlyOnPrepositions = person != null)
            return AssistantIntent.Videos(topic = topic, person = person, count = ItalianText.countIn(f), ted = ted)
        }

        return AssistantIntent.Chat(original)
    }

    private val learnRegex = Regex(
        "\\b(?:voglio|vorrei|devo|mi piacerebbe|aiutami a|aiutami ad)\\s+(?:imparare|capire|migliorare|diventare|essere|sapere come|studiare)\\s*(?:a|ad|di)?\\s+(.+)$",
    )

    private fun categoryFor(f: String): SessionCategory? = when {
        has(f, "dorm", "sonno", "addormen", "notte", "sera") -> SessionCategory.Evening
        has(f, "stress", "ansia", "ansios", "nervos", "agitat", "calmar") -> SessionCategory.Stress
        has(f, "emozion", "rabbia", "tristezza", "triste", "arrabbiat") -> SessionCategory.Emotions
        has(f, "mindfulness") -> SessionCategory.Mindfulness
        has(f, "meditaz", "meditare", "medita") -> SessionCategory.Meditation
        else -> null
    }

    /**
     * Sceglie la sessione guidata più vicina alla durata chiesta (5 minuti se non detta) nella categoria richiesta.
     * Se la categoria non ha sessioni, ripiega su mindfulness e meditazione.
     */
    fun pickSession(minutes: Int?, category: SessionCategory?, sessions: List<Session>): Session? {
        val pool = category?.let { c -> sessions.filter { it.category == c } }?.takeIf { it.isNotEmpty() }
            ?: sessions.filter { it.category == SessionCategory.Mindfulness || it.category == SessionCategory.Meditation }.ifEmpty { sessions }
        val target = minutes ?: 5
        return pool.minWithOrNull(compareBy<Session> { abs(it.minutes - target) }.thenBy { it.minutes }.thenBy { it.id })
    }
}

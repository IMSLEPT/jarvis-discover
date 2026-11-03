package it.jarvis.discover.data.wellbeing

/**
 * "Come ti senti?": per ogni stato un paio di esercizi tra cui scegliere. Sono proposte di benessere quotidiano,
 * non una diagnosi né una cura.
 */
data class Emotion(val id: String, val emoji: String, val label: String, val note: String, val sessionIds: List<String>, val breathId: String?)

object Emotions {
    val all = listOf(
        Emotion("ansioso", "😟", "Ansioso o preoccupato", "Molte persone trovano utile rallentare l'espirazione e tornare ai sensi.", listOf("cinque_sensi", "respiro_ancora"), "lunga_4_8"),
        Emotion("stressato", "😤", "Stressato", "Sciogliere la tensione dei muscoli aiuta a sentirsi più leggeri.", listOf("stress_sciogli", "pausa_minuto"), "lenta_4_6"),
        Emotion("triste", "😔", "Giù di morale", "Un po' di gentilezza verso di sé e dare un nome a ciò che si sente possono aiutare.", listOf("gentilezza", "emozioni_nome"), null),
        Emotion("arrabbiato", "😠", "Arrabbiato", "Prima di rispondere, una pausa con respiri lenti dà il tempo di scegliere come reagire.", listOf("emozioni_nome", "pausa_minuto"), "lunga_4_8"),
        Emotion("stanco", "😴", "Stanco", "Una pausa breve e consapevole vale più di quanto sembri.", listOf("pausa_minuto", "respiro_ancora"), "coerente_5_5"),
        Emotion("sveglio", "🌙", "Non riesco a rallentare la sera", "Un rilassamento lento prima di dormire. Non promette il sonno, ma aiuta a rallentare.", listOf("sonno", "sera"), "lenta_4_6"),
        Emotion("sovraccarico", "🌀", "Sovraccarico", "Quando c'è troppo, ritornare al presente per un attimo fa spazio.", listOf("cinque_sensi", "respiro_ancora"), "coerente_5_5"),
        Emotion("bene", "😊", "Sto bene", "Un buon momento per fissare ciò che di bello c'è, con la gratitudine.", listOf("sera", "gentilezza"), null),
    )

    fun byId(id: String): Emotion? = all.firstOrNull { it.id == id }
}

/** Percorso di una settimana contro lo stress: nessun ordine obbligato, nessuna scadenza. */
data class PathStep(val day: Int, val title: String, val refId: String, val isBreath: Boolean)

object StressPath {
    val steps = listOf(
        PathStep(1, "Respirazione lenta · 3 minuti", "lenta_4_6", true),
        PathStep(2, "Scansione del corpo", "scansione_corpo", false),
        PathStep(3, "Qui e ora: i cinque sensi", "cinque_sensi", false),
        PathStep(4, "Sciogli la tensione", "stress_sciogli", false),
        PathStep(5, "Dare un nome a ciò che senti", "emozioni_nome", false),
        PathStep(6, "Gentilezza verso di sé", "gentilezza", false),
        PathStep(7, "Riflessione serale", "sera", false),
    )

    val ids: Set<String> get() = steps.map { it.refId }.toSet()
}

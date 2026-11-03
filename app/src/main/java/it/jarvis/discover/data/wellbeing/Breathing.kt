package it.jarvis.discover.data.wellbeing

/** Una fase del respiro. [from] e [to] sono la dimensione del cerchio (0,55 = piccolo, 1 = grande) all'inizio e alla fine. */
data class BreathPhase(val label: String, val seconds: Int, val from: Float, val to: Float)

data class BreathPattern(
    val id: String,
    val title: String,
    val rhythm: String,
    val about: String,
    val phases: List<BreathPhase>,
) {
    val cycleSec: Int get() = phases.sumOf { it.seconds }
    val hasHold: Boolean get() = phases.any { it.from == it.to }
}

/** Dove ci si trova in un dato istante di un esercizio di respirazione. */
data class BreathPoint(val phase: BreathPhase, val secondsLeft: Int, val scale: Float, val cycle: Int)

object Breathing {
    private const val SMALL = 0.55f
    private const val BIG = 1f

    private fun inhale(s: Int) = BreathPhase("Inspira", s, SMALL, BIG)
    private fun exhale(s: Int) = BreathPhase("Espira", s, BIG, SMALL)
    private fun holdFull(s: Int) = BreathPhase("Trattieni", s, BIG, BIG)
    private fun holdEmpty(s: Int) = BreathPhase("Pausa", s, SMALL, SMALL)

    val patterns = listOf(
        BreathPattern(
            "lenta_4_6", "Respirazione lenta", "4 · 6",
            "Inspiri in 4 secondi ed espiri in 6. L'espirazione un po' più lunga dà una sensazione di calma a molte persone.",
            listOf(inhale(4), exhale(6)),
        ),
        BreathPattern(
            "coerente_5_5", "Respiro regolare", "5 · 5",
            "Inspiri in 5 secondi ed espiri in 5: un ritmo morbido e uniforme, facile da mantenere.",
            listOf(inhale(5), exhale(5)),
        ),
        BreathPattern(
            "lunga_4_8", "Espirazione lunga", "4 · 8",
            "Inspiri in 4 ed espiri in 8. Per i momenti in cui senti molta agitazione.",
            listOf(inhale(4), exhale(8)),
        ),
        BreathPattern(
            "quadrato_4", "Respiro quadrato", "4 · 4 · 4 · 4",
            "Inspiri, fai una piccola pausa, espiri e fai un'altra pausa: quattro secondi ciascuno. Ha brevi pause con i polmoni pieni e vuoti: se senti vertigini o fastidio, smetti e respira normalmente.",
            listOf(inhale(4), holdFull(4), exhale(4), holdEmpty(4)),
        ),
    )

    fun byId(id: String): BreathPattern? = patterns.firstOrNull { it.id == id }

    /** Stato dell'esercizio dopo [elapsedMs] millisecondi (il cerchio si ingrandisce e rimpicciolisce in modo continuo). */
    fun pointAt(pattern: BreathPattern, elapsedMs: Long): BreathPoint {
        val cycleMs = pattern.cycleSec * 1000L
        val t = elapsedMs.coerceAtLeast(0)
        val cycle = (t / cycleMs).toInt()
        var inCycle = t % cycleMs
        for (p in pattern.phases) {
            val len = p.seconds * 1000L
            if (inCycle < len) {
                val f = inCycle.toFloat() / len
                // Andamento morbido (ease in-out) per non far "scattare" il cerchio.
                val eased = f * f * (3f - 2f * f)
                return BreathPoint(p, ((len - inCycle + 999) / 1000).toInt(), p.from + (p.to - p.from) * eased, cycle)
            }
            inCycle -= len
        }
        val last = pattern.phases.last()
        return BreathPoint(last, 0, last.to, cycle)
    }
}

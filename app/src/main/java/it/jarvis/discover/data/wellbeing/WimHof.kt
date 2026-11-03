package it.jarvis.discover.data.wellbeing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Livello dell'allenamento di respirazione Wim Hof.
 *
 * Lo schema (respiri profondi → apnea a polmoni vuoti fino al primo bisogno di respirare → un respiro profondo
 * trattenuto 15 secondi → nuovo giro) è quello descritto dalla pagina ufficiale del metodo. Il ritmo dei respiri,
 * il numero ridotto di respiri e di giri del livello "Dolce" e il TETTO alla durata dell'apnea sono scelte di
 * prudenza di Discover, non parte del metodo originale.
 */
data class WimHofLevel(
    val id: String,
    val title: String,
    val about: String,
    val breaths: Int,
    val rounds: Int,
    val inhaleMs: Int,
    val exhaleMs: Int,
    /** Tetto all'apnea: raggiunto questo tempo si passa comunque al respiro di recupero. */
    val maxHoldSec: Int,
    val recoveryHoldSec: Int = 15,
)

enum class WhPhase(val label: String) {
    Inhale("Inspira"),
    Exhale("Espira"),
    Hold("Apnea a polmoni vuoti"),
    RecoveryInhale("Inspira a fondo"),
    RecoveryHold("Trattieni"),
    Release("Espira e rilassati"),
    Done("Finito"),
}

object WimHof {
    const val GENTLE_ID = "dolce"
    const val RECOVERY_INHALE_MS = 3_000
    const val RELEASE_MS = 3_000
    /** Sotto questo tempo (secondi) la sessione non viene registrata nel diario. */
    const val MIN_LOG_SEC = 30

    val levels = listOf(
        WimHofLevel(
            GENTLE_ID, "Dolce",
            "Per iniziare: 20 respiri morbidi, 3 giri, apnea al massimo 30 secondi.",
            breaths = 20, rounds = 3, inhaleMs = 2000, exhaleMs = 2000, maxHoldSec = 30,
        ),
        WimHofLevel(
            "standard", "Standard",
            "30 respiri, 3 giri, apnea al massimo 60 secondi. Si sblocca dopo aver completato una volta il livello Dolce.",
            breaths = 30, rounds = 3, inhaleMs = 1750, exhaleMs = 1250, maxHoldSec = 60,
        ),
    )

    fun byId(id: String): WimHofLevel? = levels.firstOrNull { it.id == id }

    /** Titolo con cui si registra una sessione portata a termine. */
    fun logTitle(level: WimHofLevel, completed: Boolean): String =
        "Wim Hof · ${level.title}" + if (completed) "" else " (interrotto)"

    /** Il livello Standard si sblocca solo dopo aver completato il Dolce almeno una volta. */
    fun unlocked(level: WimHofLevel, entries: List<Activity>): Boolean =
        level.id == GENTLE_ID || entries.any { it.kind == ActivityKind.Breathing && it.title == logTitle(byId(GENTLE_ID)!!, completed = true) }

    /** Durata prevista senza contare le apnee (che le decidi tu): utile per mostrare "circa N minuti". */
    fun fixedSeconds(l: WimHofLevel): Int {
        val perRound = (l.breaths * (l.inhaleMs + l.exhaleMs) + RECOVERY_INHALE_MS + RELEASE_MS) / 1000 + l.recoveryHoldSec
        return perRound * l.rounds
    }
}

/**
 * Stato di un'esecuzione, avanzato a colpi di tempo con [advance]. Non sa nulla di schermo né di orologio:
 * così si prova tutto senza il telefono. L'apnea finisce solo quando l'utente lo dice ([endHold]) o al tetto.
 */
class WimHofRun(val level: WimHofLevel) {
    var phase by mutableStateOf(WhPhase.Inhale); private set
    var round by mutableIntStateOf(1); private set
    var breath by mutableIntStateOf(1); private set
    /** Millisecondi trascorsi nella fase corrente (per l'apnea: da quando è iniziata). */
    var phaseMs by mutableLongStateOf(0L); private set
    var elapsedMs by mutableLongStateOf(0L); private set
    val holdsSec = mutableListOf<Int>()

    val finished: Boolean get() = phase == WhPhase.Done

    private val maxHoldMs: Long get() = level.maxHoldSec * 1000L

    private fun durationMs(p: WhPhase): Long = when (p) {
        WhPhase.Inhale -> level.inhaleMs.toLong()
        WhPhase.Exhale -> level.exhaleMs.toLong()
        WhPhase.RecoveryInhale -> WimHof.RECOVERY_INHALE_MS.toLong()
        WhPhase.RecoveryHold -> level.recoveryHoldSec * 1000L
        WhPhase.Release -> WimHof.RELEASE_MS.toLong()
        WhPhase.Hold, WhPhase.Done -> Long.MAX_VALUE
    }

    /** Secondi che mancano alla fine della fase (o secondi di apnea trascorsi, se [phase] è Hold). */
    val secondsShown: Int
        get() = when (phase) {
            WhPhase.Hold -> (phaseMs / 1000).toInt()
            WhPhase.Done -> 0
            else -> ((durationMs(phase) - phaseMs + 999) / 1000).toInt()
        }

    /** Dimensione del cerchio: piccolo = polmoni vuoti, 1 = pieni. */
    val scale: Float
        get() {
            val small = 0.55f
            val big = 1f
            fun ease(f: Float) = f * f * (3f - 2f * f)
            val f = if (phase == WhPhase.Hold || phase == WhPhase.Done) 0f else (phaseMs.toFloat() / durationMs(phase)).coerceIn(0f, 1f)
            val v = when (phase) {
                WhPhase.Inhale, WhPhase.RecoveryInhale -> small + (big - small) * ease(f)
                WhPhase.Exhale, WhPhase.Release -> big - (big - small) * ease(f)
                WhPhase.RecoveryHold -> big
                WhPhase.Hold, WhPhase.Done -> small
            }
            return v.coerceIn(small, big)
        }

    fun advance(deltaMs: Long) {
        var ms = deltaMs.coerceAtLeast(0)
        while (ms > 0 && phase != WhPhase.Done) {
            if (phase == WhPhase.Hold) {
                val need = maxHoldMs - phaseMs
                if (ms < need) {
                    phaseMs += ms; elapsedMs += ms; ms = 0
                } else {
                    phaseMs += need; elapsedMs += need; ms -= need
                    endHold()
                }
            } else {
                val left = durationMs(phase) - phaseMs
                if (ms < left) {
                    phaseMs += ms; elapsedMs += ms; ms = 0
                } else {
                    elapsedMs += left; ms -= left
                    next()
                }
            }
        }
    }

    /** L'utente sente il bisogno di respirare: chiude l'apnea e passa al respiro di recupero. */
    fun endHold() {
        if (phase != WhPhase.Hold) return
        holdsSec += (phaseMs / 1000).toInt()
        go(WhPhase.RecoveryInhale)
    }

    private fun go(p: WhPhase) { phase = p; phaseMs = 0 }

    private fun next() {
        when (phase) {
            WhPhase.Inhale -> go(WhPhase.Exhale)
            WhPhase.Exhale -> if (breath < level.breaths) { breath++; go(WhPhase.Inhale) } else go(WhPhase.Hold)
            WhPhase.RecoveryInhale -> go(WhPhase.RecoveryHold)
            WhPhase.RecoveryHold -> go(WhPhase.Release)
            WhPhase.Release -> if (round < level.rounds) { round++; breath = 1; go(WhPhase.Inhale) } else go(WhPhase.Done)
            WhPhase.Hold, WhPhase.Done -> Unit
        }
    }
}

/** Esegue un [WimHofRun] nel tempo e registra nel diario la sessione (solo se è durata almeno mezzo minuto). */
class WimHofPlayer(
    private val scope: CoroutineScope,
    private val store: WellbeingStore,
) {
    var run by mutableStateOf<WimHofRun?>(null); private set

    private var job: Job? = null

    val active: Boolean get() = run != null

    fun start(level: WimHofLevel) {
        job?.cancel()
        val r = WimHofRun(level)
        run = r
        job = scope.launch {
            while (!r.finished) {
                delay(TICK)
                r.advance(TICK)
            }
            log(r, completed = true)
        }
    }

    fun endHold() { run?.endHold() }

    /** Ferma subito. Se era in corso, la registra come interrotta (se è durata abbastanza). */
    fun stop() {
        val r = run ?: return
        job?.cancel()
        job = null
        if (!r.finished) log(r, completed = false)
        run = null
    }

    /** Chiude la schermata finale. */
    fun dismiss() {
        job?.cancel()
        job = null
        run = null
    }

    fun release() { job?.cancel(); job = null }

    private fun log(r: WimHofRun, completed: Boolean) {
        val seconds = (r.elapsedMs / 1000).toInt()
        if (seconds >= WimHof.MIN_LOG_SEC) store.log(ActivityKind.Breathing, WimHof.logTitle(r.level, completed), seconds)
    }

    companion object { const val TICK = 100L }
}

package it.jarvis.discover.data.wellbeing

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

enum class SpeakResult { Done, Interrupted, Failed }

/** Voce che legge i passi. Separata dal lettore così si può provare senza il telefono. */
interface Speaker {
    /** Prepara la voce italiana. Vero se è disponibile. */
    suspend fun prepare(): Boolean
    suspend fun speak(text: String): SpeakResult
    fun stop()
    fun shutdown()
}

/** Voce di sistema (Text-to-Speech) in italiano. Tutto avviene sul telefono. */
class TtsSpeaker(context: Context) : Speaker {
    private val app = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready: Boolean? = null
    private var counter = 0

    override suspend fun prepare(): Boolean {
        ready?.let { return it }
        val ok = suspendCancellableCoroutine { cont ->
            val engine = TextToSpeech(app) { status ->
                val e = tts
                val good = status == TextToSpeech.SUCCESS && e != null && run {
                    val r = e.setLanguage(Locale.ITALIAN)
                    r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                }
                if (good) {
                    e?.setSpeechRate(0.92f)
                    e?.setPitch(1.0f)
                }
                if (cont.isActive) cont.resume(good)
            }
            tts = engine
        }
        ready = ok
        return ok
    }

    override suspend fun speak(text: String): SpeakResult = suspendCancellableCoroutine { cont ->
        val engine = tts
        if (engine == null || ready != true) {
            cont.resume(SpeakResult.Failed)
            return@suspendCancellableCoroutine
        }
        val id = "u${counter++}"
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(SpeakResult.Done) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { if (utteranceId == id && cont.isActive) cont.resume(SpeakResult.Interrupted) }
            @Deprecated("Richiesto dall'API", ReplaceWith("onError(utteranceId, errorCode)"))
            override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(SpeakResult.Failed) }
        })
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) cont.resume(SpeakResult.Failed)
        cont.invokeOnCancellation { engine.stop() }
    }

    override fun stop() { tts?.stop() }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = null
    }
}

enum class PlayState { Idle, Running, Paused, Finished }

/**
 * Guida una sessione passo per passo: legge il testo con la voce (se c'è e se la lasci accesa), aspetta il
 * silenzio del passo e passa al successivo. Si può mettere in pausa, saltare e fermare in ogni momento.
 * Una sessione si registra nel diario solo se dura almeno mezzo minuto.
 */
class SessionPlayer(
    private val scope: CoroutineScope,
    private val speaker: Speaker,
    private val store: WellbeingStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var session by mutableStateOf<Session?>(null); private set
    var index by mutableIntStateOf(0); private set
    var state by mutableStateOf(PlayState.Idle); private set
    var pauseLeft by mutableIntStateOf(0); private set
    /** L'utente vuole la voce (può spegnerla e leggere soltanto). */
    var voiceOn by mutableStateOf(true)
    /** Vero se la voce italiana è disponibile; falso se manca la sintesi vocale o la lingua. */
    var voiceAvailable by mutableStateOf(true); private set

    private var job: Job? = null
    private var startedAt = 0L
    private var activeMs = 0L
    private var lastTick = 0L

    val step: Step? get() = session?.steps?.getOrNull(index)
    val isActive: Boolean get() = state == PlayState.Running || state == PlayState.Paused

    fun start(s: Session) {
        cancelJob()
        session = s
        index = 0
        state = PlayState.Running
        activeMs = 0
        startedAt = now()
        lastTick = startedAt
        launchFrom(0, first = true)
    }

    fun pause() {
        if (state != PlayState.Running) return
        accrue()
        state = PlayState.Paused
        speaker.stop()
    }

    fun resume() {
        if (state != PlayState.Paused) return
        lastTick = now()
        state = PlayState.Running
    }

    fun next() { session?.let { if (isActive) jump((index + 1).coerceAtMost(it.steps.size)) } }
    fun previous() { if (isActive) jump((index - 1).coerceAtLeast(0)) }

    /** Ferma la sessione. Se è durata abbastanza la registra comunque nel diario. */
    fun stop() {
        val s = session ?: return
        if (state == PlayState.Idle) return
        accrue()
        val finished = state == PlayState.Finished
        cancelJob()
        speaker.stop()
        if (!finished) record(s, completed = false)
        state = PlayState.Idle
        session = null
    }

    fun dismiss() {
        cancelJob()
        speaker.stop()
        state = PlayState.Idle
        session = null
    }

    fun release() {
        cancelJob()
        speaker.shutdown()
    }

    private fun jump(i: Int) {
        val s = session ?: return
        speaker.stop()
        cancelJob()
        if (i >= s.steps.size) { finish(s); return }
        index = i
        launchFrom(i, first = false)
    }

    private fun launchFrom(from: Int, first: Boolean) {
        val s = session ?: return
        job = scope.launch {
            if (first && voiceOn) voiceAvailable = speaker.prepare()
            for (i in from until s.steps.size) {
                index = i
                runStep(s.steps[i])
            }
            finish(s)
        }
    }

    private suspend fun runStep(step: Step) {
        var spoken = false
        while (!spoken) {
            awaitRunning()
            spoken = if (voiceOn && voiceAvailable) {
                when (speaker.speak(step.text)) {
                    SpeakResult.Done -> true
                    SpeakResult.Interrupted -> false   // pausa: si rilegge il passo quando riparte
                    SpeakResult.Failed -> { voiceAvailable = false; false }
                }
            } else {
                readSilently(step.text)
            }
        }
        var left = step.pauseSec
        while (left > 0) {
            awaitRunning()
            pauseLeft = left
            delay(1000)
            if (state == PlayState.Running) left--
        }
        pauseLeft = 0
    }

    /** Senza voce: lascia il tempo di leggere. Restituisce falso se in pausa. */
    private suspend fun readSilently(text: String): Boolean {
        var leftMs = Reading.seconds(text) * 1000L
        while (leftMs > 0) {
            if (state != PlayState.Running) return false
            delay(250)
            leftMs -= 250
        }
        return true
    }

    private suspend fun awaitRunning() {
        while (state == PlayState.Paused) delay(100)
    }

    private fun finish(s: Session) {
        accrue()
        state = PlayState.Finished
        pauseLeft = 0
        record(s, completed = true)
    }

    private fun record(s: Session, completed: Boolean) {
        val seconds = (activeMs / 1000).toInt()
        if (seconds < MIN_LOG_SEC) return
        // Per segnare il passaggio del percorso serve averla davvero seguita, non averla saltata a colpi di "avanti".
        val followed = completed && seconds >= s.durationSec / 3
        val kind = when (s.category) {
            SessionCategory.Meditation -> ActivityKind.Meditation
            SessionCategory.Mindfulness -> ActivityKind.Mindfulness
            SessionCategory.Evening -> ActivityKind.Evening
            SessionCategory.Emotions -> ActivityKind.Emotions
            SessionCategory.Stress -> ActivityKind.Stress
        }
        // Il passaggio del percorso si segna come fatto solo se la sessione è stata ascoltata fino in fondo.
        store.log(kind, s.title, seconds, refId = if (followed) s.id else null)
    }

    private fun accrue() {
        val t = now()
        if (state == PlayState.Running) activeMs += (t - lastTick).coerceAtLeast(0)
        lastTick = t
    }

    private fun cancelJob() { job?.cancel(); job = null }

    companion object { const val MIN_LOG_SEC = 30 }
}

/** Esercizio di respirazione guidato dall'animazione: dura il tempo scelto e poi si ferma da solo. */
class BreathingPlayer(
    private val scope: CoroutineScope,
    private val store: WellbeingStore,
) {
    var pattern by mutableStateOf<BreathPattern?>(null); private set
    var running by mutableStateOf(false); private set
    var finished by mutableStateOf(false); private set
    var elapsedMs by mutableStateOf(0L); private set
    var totalMs by mutableStateOf(0L); private set

    private var job: Job? = null

    fun start(p: BreathPattern, minutes: Int) {
        job?.cancel()
        pattern = p
        totalMs = minutes * 60_000L
        elapsedMs = 0
        finished = false
        running = true
        job = scope.launch {
            while (elapsedMs < totalMs) {
                delay(TICK)
                elapsedMs += TICK
            }
            running = false
            finished = true
            log(p, completed = true)
        }
    }

    fun stop() {
        val p = pattern ?: return
        if (running) {
            job?.cancel()
            running = false
            log(p, completed = false)
        }
        finished = false
        pattern = null
        elapsedMs = 0
    }

    fun release() { job?.cancel(); running = false }

    private fun log(p: BreathPattern, completed: Boolean) {
        val seconds = (elapsedMs / 1000).toInt()
        if (seconds >= SessionPlayer.MIN_LOG_SEC) store.log(ActivityKind.Breathing, p.title, seconds, refId = if (completed && totalMs >= 180_000L) p.id else null)
    }

    companion object { const val TICK = 100L }
}

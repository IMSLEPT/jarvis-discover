package it.jarvis.discover.data.wellbeing

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class NoiseKind(val label: String, val emoji: String, val about: String) {
    White("Rumore bianco", "☁️", "Un fruscio uniforme, utile per coprire i rumori intorno."),
    Pink("Rumore rosa", "🌸", "Più morbido del bianco, con meno acuti."),
    Brown("Rumore marrone", "🌰", "Profondo e grave, simile a un forte scroscio lontano."),
    Waves("Onde", "🌊", "Un rumore grave che sale e scende lentamente, come il mare."),
}

/**
 * Generatore di rumori rilassanti: l'audio è calcolato sul telefono, quindi funziona senza connessione,
 * non ha diritti d'autore e non scarica nulla. Campioni a 16 bit, mono.
 */
class NoiseSynth(private val kind: NoiseKind, seed: Long = 1L, val sampleRate: Int = SAMPLE_RATE) {
    private val rnd = Random(seed)
    private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var b3 = 0.0; private var b4 = 0.0; private var b5 = 0.0; private var b6 = 0.0
    private var brown = 0.0
    private var n = 0L

    private fun white() = rnd.nextDouble() * 2.0 - 1.0

    private fun pink(): Double {
        val w = white()
        // Filtro di Paul Kellet (approssimazione del rumore rosa).
        b0 = 0.99886 * b0 + w * 0.0555179
        b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522
        b5 = -0.7616 * b5 - w * 0.0168980
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
        return out * 0.11
    }

    private fun brown(): Double {
        brown = (brown + 0.02 * white()) / 1.02
        return (brown * 3.5).coerceIn(-1.0, 1.0)
    }

    /** Riempie [out] con [count] campioni. */
    fun fill(out: ShortArray, count: Int = out.size) {
        for (i in 0 until count) {
            val v = when (kind) {
                NoiseKind.White -> white() * 0.35
                NoiseKind.Pink -> pink() * 0.9
                NoiseKind.Brown -> brown() * 0.9
                NoiseKind.Waves -> {
                    val t = n.toDouble() / sampleRate
                    // Due oscillazioni lente sfasate: il "respiro" delle onde non è mai identico.
                    val slow = (sin(2 * PI * t / 9.0) + 1) / 2
                    val drift = (sin(2 * PI * t / 23.0) + 1) / 2
                    val env = 0.18 + 0.82 * (slow * (0.7 + 0.3 * drift)).pow(1.6)
                    brown() * env * 0.95
                }
            }
            n++
            out[i] = (v.coerceIn(-0.95, 0.95) * 32767).toInt().toShort()
        }
    }

    companion object { const val SAMPLE_RATE = 22050 }
}

/** Riproduce i rumori in sottofondo con un timer di spegnimento, finché l'app resta aperta. */
class NoisePlayer(
    private val scope: CoroutineScope,
    private val store: WellbeingStore,
) {
    var kind by mutableStateOf<NoiseKind?>(null); private set
    var minutesLeft by mutableIntStateOf(0); private set
    var volume by mutableFloatStateOf(0.6f); private set

    private var job: Job? = null
    private var timer: Job? = null
    private var track: AudioTrack? = null
    private var startedAt = 0L

    val isPlaying: Boolean get() = kind != null

    fun play(newKind: NoiseKind, minutes: Int = 30) {
        stop()
        kind = newKind
        minutesLeft = minutes
        startedAt = System.currentTimeMillis()
        job = scope.launch(Dispatchers.IO) {
            val synth = NoiseSynth(newKind, seed = System.nanoTime())
            val rate = synth.sampleRate
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t = try {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(min, rate / 2 * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (e: Exception) {
                kind = null
                return@launch
            }
            track = t
            t.setVolume(volume)
            t.play()
            val buf = ShortArray(rate / 10)
            try {
                while (isActive) {
                    synth.fill(buf)
                    if (t.write(buf, 0, buf.size) < 0) break
                }
            } finally {
                runCatching { t.stop() }
                t.release()
                if (track === t) track = null
            }
        }
        timer = scope.launch {
            while (minutesLeft > 0) {
                delay(60_000)
                minutesLeft--
            }
            stop()
        }
    }

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        track?.setVolume(volume)
    }

    fun stop() {
        val k = kind
        job?.cancel(); job = null
        timer?.cancel(); timer = null
        kind = null
        minutesLeft = 0
        if (k != null) {
            val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            if (seconds >= MIN_LOG_SEC) store.log(ActivityKind.Sound, k.label, seconds)
        }
    }

    fun release() = stop()

    companion object { const val MIN_LOG_SEC = 30 }
}

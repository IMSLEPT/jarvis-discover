package it.jarvis.discover.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import it.jarvis.discover.data.content.AudioItem
import it.jarvis.discover.data.content.LibraryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lato app della riproduzione audio: si collega al [PlaybackService] solo quando serve e mette a
 * disposizione dello schermo lo stato (brano, posizione, velocità). Ricorda dove eri arrivato.
 */
class AudioController(
    context: Context,
    private val scope: CoroutineScope,
    private val library: LibraryStore,
) {
    private val appContext = context.applicationContext

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingStart: (() -> Unit)? = null
    private var ticker: Job? = null
    private var sleepJob: Job? = null

    var queue by mutableStateOf<List<AudioItem>>(emptyList()); private set
    var current by mutableStateOf<AudioItem?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var isBuffering by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var speed by mutableFloatStateOf(1f); private set
    var error by mutableStateOf<String?>(null); private set
    /** Minuti rimasti al timer di spegnimento (0 = non attivo). */
    var sleepMinutesLeft by mutableStateOf(0); private set

    val hasMedia: Boolean get() = current != null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@AudioController.isPlaying = isPlaying
            if (!isPlaying) saveProgress()
            if (isPlaying) startTicker()
        }

        override fun onPlaybackStateChanged(state: Int) {
            isBuffering = state == Player.STATE_BUFFERING
            durationMs = controller?.duration?.takeIf { it > 0 } ?: durationMs
            if (state == Player.STATE_ENDED) {
                current?.let { library.savePosition(it.id, 0) }
                isPlaying = false
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            current = queue.firstOrNull { it.id == mediaItem?.mediaId }
            positionMs = 0
            durationMs = (current?.durationSec ?: 0) * 1000L
        }

        override fun onPlayerError(e: PlaybackException) {
            error = "Non riesco a riprodurre questo audio (${e.errorCodeName}). Il server potrebbe non rispondere."
            isPlaying = false
        }
    }

    /** Avvia la riproduzione di [items] a partire da [index], riprendendo dal punto salvato. */
    fun play(items: List<AudioItem>, index: Int = 0) {
        if (items.isEmpty()) return
        error = null
        queue = items
        current = items.getOrNull(index)
        val start = {
            val c = controller
            if (c != null) {
                c.setMediaItems(items.map(::toMediaItem), index, library.positionOf(items[index].id))
                c.setPlaybackSpeed(speed)
                c.prepare()
                c.play()
            }
        }
        if (controller != null) start() else { pendingStart = start; connect() }
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0))
        positionMs = ms.coerceAtLeast(0)
    }

    fun skip(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0)
        seekTo(if (durationMs > 0) target.coerceAtMost(durationMs) else target)
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    fun setSpeed(value: Float) {
        speed = value
        controller?.setPlaybackSpeed(value)
    }

    /** Spegne la riproduzione dopo [minutes] minuti; 0 annulla il timer. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepMinutesLeft = minutes
        if (minutes <= 0) return
        sleepJob = scope.launch {
            while (sleepMinutesLeft > 0) {
                delay(60_000)
                sleepMinutesLeft--
            }
            controller?.pause()
        }
    }

    fun stop() {
        saveProgress()
        controller?.run { stop(); clearMediaItems() }
        current = null
        queue = emptyList()
        isPlaying = false
        positionMs = 0
        setSleepTimer(0)
    }

    fun dismissError() { error = null }

    fun release() {
        saveProgress()
        ticker?.cancel()
        sleepJob?.cancel()
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull()
            if (c == null) {
                error = "Non riesco ad avviare il lettore audio."
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            pendingStart?.invoke()
            pendingStart = null
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var sinceSave = 0
            while (isActive) {
                val c = controller
                if (c != null) {
                    positionMs = c.currentPosition.coerceAtLeast(0)
                    c.duration.takeIf { it > 0 }?.let { durationMs = it }
                    if (c.isPlaying && ++sinceSave >= 30) { sinceSave = 0; saveProgress() }
                }
                delay(500)
            }
        }
    }

    private fun saveProgress() {
        val item = current ?: return
        val c = controller ?: return
        library.savePosition(item.id, c.currentPosition)
    }

    private fun toMediaItem(a: AudioItem): MediaItem = MediaItem.Builder()
        .setMediaId(a.id)
        .setUri(a.url)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(a.title).setArtist(a.subtitle)
                .also { b -> a.artwork?.let { b.setArtworkUri(Uri.parse(it)) } }.build(),
        )
        .build()
}

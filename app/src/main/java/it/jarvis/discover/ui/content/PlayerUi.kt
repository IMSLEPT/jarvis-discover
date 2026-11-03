@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package it.jarvis.discover.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.jarvis.discover.playback.AudioController
import it.jarvis.discover.ui.components.ScreenPadding

private val Speeds = listOf(0.8f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SleepOptions = listOf(0, 15, 30, 60)

/** Barra del lettore audio, sempre visibile sopra la navigazione quando c'è qualcosa in ascolto. */
@Composable
fun MiniPlayer(audio: AudioController) {
    val item = audio.current ?: return
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { expanded = true },
    ) {
        val progress = if (audio.durationMs > 0) (audio.positionMs.toFloat() / audio.durationMs).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cover(item.artwork, 44.dp, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (audio.isBuffering) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            IconButton(onClick = audio::toggle) {
                Icon(if (audio.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (audio.isPlaying) "Pausa" else "Riproduci")
            }
            IconButton(onClick = audio::stop) { Icon(Icons.Rounded.Close, contentDescription = "Chiudi il lettore") }
        }
    }
    if (expanded) PlayerSheet(audio, onDismiss = { expanded = false })
}

@Composable
private fun PlayerSheet(audio: AudioController, onDismiss: () -> Unit) {
    val item = audio.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (item == null) return@ModalBottomSheet
        var dragging by remember { mutableStateOf<Float?>(null) }
        val duration = audio.durationMs.coerceAtLeast(1)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Cover(item.artwork, 200.dp, 200.dp, shape = MaterialTheme.shapes.extraLarge)
            Text(item.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Slider(
                value = dragging ?: audio.positionMs.toFloat().coerceAtMost(duration.toFloat()),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { audio.seekTo(it.toLong()) }; dragging = null },
                valueRange = 0f..duration.toFloat(),
                enabled = audio.durationMs > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatClock((dragging ?: audio.positionMs.toFloat()).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (audio.durationMs > 0) formatClock(audio.durationMs) else "--:--", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = audio::previous, enabled = audio.queue.size > 1) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Precedente") }
                IconButton(onClick = { audio.skip(-10_000) }) { Icon(Icons.Rounded.Replay10, contentDescription = "Indietro di 10 secondi") }
                FilledIconButton(onClick = audio::toggle, modifier = Modifier.size(64.dp)) {
                    if (audio.isBuffering) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(if (audio.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (audio.isPlaying) "Pausa" else "Riproduci", modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { audio.skip(30_000) }) { Icon(Icons.Rounded.Forward30, contentDescription = "Avanti di 30 secondi") }
                IconButton(onClick = audio::next, enabled = audio.queue.size > 1) { Icon(Icons.Rounded.SkipNext, contentDescription = "Successivo") }
            }
            Text("Velocità", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Speeds.forEach { s -> FilterChip(selected = audio.speed == s, onClick = { audio.setSpeed(s) }, label = { Text("${s}x") }, shape = CircleShape) }
            }
            Text(
                if (audio.sleepMinutesLeft > 0) "Timer di spegnimento: ${audio.sleepMinutesLeft} min" else "Timer di spegnimento",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepOptions.forEach { m ->
                    FilterChip(selected = (m == 0 && audio.sleepMinutesLeft == 0), onClick = { audio.setSleepTimer(m) }, label = { Text(if (m == 0) "Spento" else "$m min") }, shape = CircleShape)
                }
            }
            audio.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = audio::dismissError) { Text("Ok") }
            }
        }
    }
}

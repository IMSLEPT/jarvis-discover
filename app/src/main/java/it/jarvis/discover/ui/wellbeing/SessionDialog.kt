package it.jarvis.discover.ui.wellbeing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.jarvis.discover.data.wellbeing.PlayState
import it.jarvis.discover.data.wellbeing.SessionPlayer
import it.jarvis.discover.ui.components.BrandBackground
import it.jarvis.discover.ui.components.CompassMark
import it.jarvis.discover.ui.components.ScreenPadding

/** Sessione guidata a tutto schermo: un passo per volta, con voce italiana (se disponibile), pausa e salto. */
@Composable
fun SessionDialog(player: SessionPlayer) {
    val session = player.session ?: return
    Dialog(
        onDismissRequest = { if (player.state == PlayState.Finished) player.dismiss() else player.stop() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        // Lo schermo resta acceso finché la sessione è aperta.
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        BrandBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = ScreenPadding, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (player.state == PlayState.Finished) player.dismiss() else player.stop() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Chiudi")
                    }
                }

                if (player.state == PlayState.Finished) {
                    Finished(session.title, onClose = player::dismiss)
                } else {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = player.step?.text.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                        if (player.pauseLeft > 0) {
                            Text(
                                text = "Silenzio · ${player.pauseLeft} s",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        LinearProgressIndicator(
                            progress = { (player.index + 1).toFloat() / session.steps.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Passo ${player.index + 1} di ${session.steps.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = player::previous) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Passo precedente", modifier = Modifier.size(32.dp)) }
                            FilledIconButton(
                                onClick = { if (player.state == PlayState.Running) player.pause() else player.resume() },
                                modifier = Modifier.size(72.dp),
                            ) {
                                Icon(
                                    if (player.state == PlayState.Running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (player.state == PlayState.Running) "Pausa" else "Riprendi",
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            IconButton(onClick = player::next) { Icon(Icons.Rounded.SkipNext, contentDescription = "Passo successivo", modifier = Modifier.size(32.dp)) }
                        }
                        FilterChip(
                            selected = player.voiceOn && player.voiceAvailable,
                            onClick = { player.voiceOn = !player.voiceOn },
                            label = { Text(if (player.voiceOn && player.voiceAvailable) "Voce attiva" else "Solo testo") },
                            shape = CircleShape,
                        )
                        if (player.voiceOn && !player.voiceAvailable) {
                            Text(
                                "La voce italiana non è disponibile su questo telefono: leggi il testo. Puoi installarla da Impostazioni › Sintesi vocale.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Finished(title: String, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) { CompassMark(size = 120.dp) }
        Spacer(Modifier.height(20.dp))
        Text("Fatto.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Hai dedicato questo tempo a te: «$title».",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onClose, shape = CircleShape) { Text("Chiudi") }
    }
}

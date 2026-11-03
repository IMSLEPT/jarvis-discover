package it.jarvis.discover.ui.wellbeing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.wellbeing.NoiseKind
import it.jarvis.discover.data.wellbeing.NoisePlayer
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.SoftCard

private val Timers = listOf(15, 30, 60)

/** Audio rilassanti generati sul telefono, con timer di spegnimento. */
@Composable
fun NoiseCard(player: NoisePlayer) {
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    SoftCard(contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("AUDIO RILASSANTI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            NoiseKind.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        val on = player.kind == k
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.medium)
                                .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clickable { if (on) player.stop() else player.play(k, minutes) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            EmojiBubble(emoji = k.emoji, size = 36, background = MaterialTheme.colorScheme.surfaceContainerLowest)
                            Text(k.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            val playing = player.kind
            if (playing != null) {
                Text(playing.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Volume", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(value = player.volume, onValueChange = player::setVolume, modifier = Modifier.weight(1f))
                }
                Text("Si spegne da solo tra ${player.minutesLeft} min.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = player::stop, shape = CircleShape) { Text("Ferma") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Timer", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Timers.forEach { m ->
                        FilterChip(selected = m == minutes, onClick = { minutes = m }, label = { Text("$m min") }, shape = CircleShape)
                    }
                }
            }
            Text(
                "I suoni sono calcolati sul telefono: non serve Internet e non c'è niente da scaricare. Suonano finché l'app resta aperta; il timer li spegne da solo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

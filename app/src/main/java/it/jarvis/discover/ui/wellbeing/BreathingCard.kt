package it.jarvis.discover.ui.wellbeing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.wellbeing.BreathPattern
import it.jarvis.discover.data.wellbeing.Breathing
import it.jarvis.discover.data.wellbeing.BreathingPlayer
import it.jarvis.discover.ui.components.SoftCard
import it.jarvis.discover.ui.theme.Palette

private val Durations = listOf(1, 3, 5, 10)

/** Respirazione guidata: un cerchio che si espande e si restringe seguendo il ritmo scelto. */
@Composable
fun BreathingCard(player: BreathingPlayer) {
    var patternId by rememberSaveable { mutableStateOf(Breathing.patterns.first().id) }
    var minutes by rememberSaveable { mutableIntStateOf(3) }
    val pattern = Breathing.byId(patternId) ?: Breathing.patterns.first()

    SoftCard(contentPadding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RESPIRAZIONE GUIDATA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())

            val active = player.pattern
            if (active != null && (player.running || player.finished)) {
                Running(player)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(Breathing.patterns, key = { it.id }) { p ->
                        FilterChip(selected = p.id == patternId, onClick = { patternId = p.id }, label = { Text("${p.title} · ${p.rhythm}") }, shape = CircleShape)
                    }
                }
                Text(pattern.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(Durations) { m ->
                        FilterChip(selected = m == minutes, onClick = { minutes = m }, label = { Text("$m min") }, shape = CircleShape)
                    }
                }
                Button(onClick = { player.start(pattern, minutes) }, shape = CircleShape, modifier = Modifier.height(48.dp)) {
                    Text("Inizia", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "Respira dal naso, senza forzare. Se senti vertigini o fastidio, smetti e respira normalmente. Non farlo mentre guidi, in acqua o in piedi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Running(player: BreathingPlayer) {
    val pattern = player.pattern ?: return
    if (player.finished) {
        Text("Ben fatto.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text("${player.totalMs / 60_000} minuti di respirazione ${pattern.title.lowercase()}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = player::stop, shape = CircleShape) { Text("Chiudi") }
        return
    }
    val point = Breathing.pointAt(pattern, player.elapsedMs)
    Box(modifier = Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Disc(point.scale)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(point.phase.label, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text("${point.secondsLeft}", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha = 0.9f))
        }
    }
    val left = ((player.totalMs - player.elapsedMs) / 1000).coerceAtLeast(0)
    Text("Mancano %d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = player::stop, shape = CircleShape) { Text("Ferma") }
}

/** Il cerchio: aloni azzurri e arancioni intorno a un disco blu, come l'anello dell'icona. */
@Composable
internal fun Disc(scale: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val max = size.minDimension / 2f
        val r = max * scale
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Palette.Sky.copy(alpha = 0.16f), radius = r, center = c)
        drawCircle(Palette.Orange.copy(alpha = 0.14f), radius = r * 0.86f, center = c)
        drawCircle(
            brush = Brush.radialGradient(listOf(Palette.Sky, Palette.Blue, Palette.NavyDeep), center = c, radius = r * 0.7f),
            radius = r * 0.7f,
            center = c,
        )
    }
}

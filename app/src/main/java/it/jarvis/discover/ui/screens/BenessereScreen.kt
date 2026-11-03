package it.jarvis.discover.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.PhaseChip
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SectionTitle
import it.jarvis.discover.ui.components.SoftCard
import it.jarvis.discover.ui.theme.Palette
import kotlinx.coroutines.delay

private data class WellnessTile(val emoji: String, val title: String, val phase: Int)

private val UpcomingTiles = listOf(
    WellnessTile("🧘", "Meditazioni guidate", 6),
    WellnessTile("🌿", "Mindfulness", 6),
    WellnessTile("🌙", "Riflessione serale", 6),
    WellnessTile("💛", "Gestire le emozioni", 6),
)

@Composable
fun BenessereScreen(onSaveGratitude: (List<String>) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "intro") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Benessere", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    text = "Qualche minuto per te, senza fretta.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "breathing") { BreathingCard() }
        item(key = "gratitude") { GratitudeCard(onSave = onSaveGratitude) }
        item(key = "upcoming-title") {
            SectionTitle(title = "Presto qui", subtitle = "Meditazioni e percorsi guidati, in italiano.")
        }
        item(key = "upcoming") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                UpcomingTiles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tile ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                EmojiBubble(emoji = tile.emoji, size = 44)
                                Text(tile.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                PhaseChip(tile.phase)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Respirazione lenta guidata: 4 secondi di inspirazione, 6 di espirazione.
 * È una pratica di rilassamento semplice e sicura; non include tecniche di trattenimento del respiro.
 */
@Composable
private fun BreathingCard() {
    var running by rememberSaveable { mutableStateOf(false) }
    var phase by remember { mutableStateOf("Pronto?") }
    var cycles by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0.55f) }

    LaunchedEffect(running) {
        if (running) {
            cycles = 0
            while (true) {
                phase = "Inspira"
                progress.animateTo(1f, tween(durationMillis = 4000, easing = FastOutSlowInEasing))
                phase = "Espira"
                progress.animateTo(0.55f, tween(durationMillis = 6000, easing = FastOutSlowInEasing))
                cycles += 1
            }
        } else {
            phase = "Pronto?"
            progress.animateTo(0.55f, tween(durationMillis = 500))
        }
    }

    val green = Palette.Green
    val ringColor = MaterialTheme.colorScheme.tertiary

    SoftCard(contentPadding = PaddingValues(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "RESPIRAZIONE LENTA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.minDimension / 2f
                    val radius = maxRadius * progress.value
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(color = ringColor.copy(alpha = 0.10f), radius = radius, center = center)
                    drawCircle(color = ringColor.copy(alpha = 0.18f), radius = radius * 0.84f, center = center)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(green.copy(alpha = 0.95f), Palette.GreenDeep),
                            center = center,
                            radius = radius * 0.66f,
                        ),
                        radius = radius * 0.66f,
                        center = center,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phase, style = MaterialTheme.typography.titleLarge, color = Color.White)
                    if (running) {
                        Text(
                            text = if (cycles == 1) "1 ciclo" else "$cycles cicli",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
            Button(
                onClick = { running = !running },
                shape = CircleShape,
                modifier = Modifier.height(48.dp),
            ) {
                Text(if (running) "Ferma" else "Inizia", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = "Se senti vertigini o fastidio, smetti e respira normalmente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GratitudeCard(onSave: (List<String>) -> Unit) {
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var third by rememberSaveable { mutableStateOf("") }
    var justSaved by remember { mutableStateOf(false) }

    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(2500)
            justSaved = false
        }
    }

    SoftCard(contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "GRATITUDINE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Quali sono tre cose belle successe oggi?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            GratitudeField(value = first, onChange = { first = it }, label = "Prima cosa", last = false)
            GratitudeField(value = second, onChange = { second = it }, label = "Seconda cosa", last = false)
            GratitudeField(value = third, onChange = { third = it }, label = "Terza cosa", last = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onSave(listOf(first, second, third))
                        first = ""
                        second = ""
                        third = ""
                        justSaved = true
                    },
                    enabled = listOf(first, second, third).any { it.isNotBlank() },
                    shape = CircleShape,
                ) {
                    Text("Salva nel diario", style = MaterialTheme.typography.labelLarge)
                }
                if (justSaved) {
                    Text(
                        text = "Salvato ✓",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                text = "Le tue risposte restano solo su questo telefono. Le trovi in La mia raccolta › Diario.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GratitudeField(value: String, onChange: (String) -> Unit, label: String, last: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
    )
}

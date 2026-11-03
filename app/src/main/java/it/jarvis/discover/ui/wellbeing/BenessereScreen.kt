package it.jarvis.discover.ui.wellbeing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.wellbeing.Breathing
import it.jarvis.discover.data.wellbeing.BreathingPlayer
import it.jarvis.discover.data.wellbeing.Emotions
import it.jarvis.discover.data.wellbeing.NoisePlayer
import it.jarvis.discover.data.wellbeing.Session
import it.jarvis.discover.data.wellbeing.SessionCategory
import it.jarvis.discover.data.wellbeing.SessionPlayer
import it.jarvis.discover.data.wellbeing.Sessions
import it.jarvis.discover.data.wellbeing.StressPath
import it.jarvis.discover.data.wellbeing.WellbeingStore
import it.jarvis.discover.data.wellbeing.WimHofPlayer
import it.jarvis.discover.ui.components.BrandHeadline
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SectionTitle
import it.jarvis.discover.ui.components.SoftCard
import it.jarvis.discover.ui.components.openUrl
import it.jarvis.discover.ui.theme.Brand
import it.jarvis.discover.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.LocalTime

@Composable
fun BenessereScreen(
    store: WellbeingStore,
    sessions: SessionPlayer,
    breathing: BreathingPlayer,
    noise: NoisePlayer,
    wimHof: WimHofPlayer,
    onSaveGratitude: (List<String>) -> Unit,
) {
    var cautionFor by remember { mutableStateOf<Session?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var emotionId by rememberSaveable { mutableStateOf<String?>(null) }

    fun begin(s: Session) {
        // Il suono di sottofondo e la sessione parlata si sovrapporrebbero: si ferma il rumore.
        noise.stop()
        breathing.stop()
        wimHof.stop()
        if (s.caution != null) cautionFor = s else sessions.start(s)
    }

    fun beginBreath(id: String, minutes: Int) {
        val p = Breathing.byId(id) ?: return
        noise.stop()
        wimHof.stop()
        breathing.start(p, minutes)
    }

    val suggestion = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> Sessions.byId("respiro_ancora")
            in 12..17 -> Sessions.byId("pausa_minuto")
            else -> Sessions.byId("sera")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BrandHeadline("Benessere")
                Text(
                    "Un momento per te: respira, ascolta, rallenta.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (suggestion != null) {
            item(key = "suggestion") { SuggestionCard(suggestion, onStart = { begin(suggestion) }) }
        }

        item(key = "emotions") { EmotionsCard(emotionId, onPick = { emotionId = if (emotionId == it) null else it }, onSession = ::begin, onBreath = { beginBreath(it, 3) }) }

        item(key = "sessions-title") { SectionTitle(title = "Meditazioni e mindfulness", subtitle = "Con voce italiana oppure solo testo. Testi scritti per Discover.") }
        item(key = "sessions-filter") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Tutte") }, shape = CircleShape) }
                items(SessionCategory.entries) { c ->
                    FilterChip(selected = category == c.name, onClick = { category = c.name }, label = { Text("${c.emoji} ${c.label}") }, shape = CircleShape)
                }
            }
        }
        items(Sessions.all.filter { category == null || it.category.name == category }, key = { "s-" + it.id }) { s ->
            SessionRow(s, onStart = { begin(s) })
        }

        item(key = "breathing") { BreathingCard(breathing) }
        item(key = "noise") { NoiseCard(noise) }
        item(key = "path") { PathCard(store, onSession = ::begin, onBreath = { beginBreath(it, 3) }) }
        item(key = "gratitude") { GratitudeCard(onSave = onSaveGratitude) }
        item(key = "progress") { ProgressCard(store) }
        item(key = "wimhof") { WimHofCard(wimHof, store.entries, onBeforeStart = { noise.stop(); breathing.stop(); sessions.stop() }) }
        item(key = "disclaimer") {
            Text(
                "Questi esercizi sono pensati per il benessere di ogni giorno e non sostituiscono cure mediche o psicologiche. " +
                    "Se stai male, se un'emozione è molto intensa o dura da tempo, parlane con un medico o con un professionista.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    cautionFor?.let { s ->
        AlertDialog(
            onDismissRequest = { cautionFor = null },
            title = { Text("Prima di iniziare") },
            text = { Text(s.caution.orEmpty()) },
            confirmButton = { TextButton(onClick = { cautionFor = null; sessions.start(s) }) { Text("Ho capito, inizia") } },
            dismissButton = { TextButton(onClick = { cautionFor = null }) { Text("Annulla") } },
        )
    }
    if (sessions.session != null) SessionDialog(sessions)
}

@Composable
private fun SuggestionCard(s: Session, onStart: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Palette.BlueDeep, Palette.PlumDeep, Palette.OrangeDeep)))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PER QUESTO MOMENTO", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f))
            Text(s.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("${s.minutes} min · ${s.about}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
            Button(
                onClick = onStart,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Palette.Navy),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Inizia", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun EmotionsCard(selected: String?, onPick: (String) -> Unit, onSession: (Session) -> Unit, onBreath: (String) -> Unit) {
    SoftCard(modifier = Modifier.animateContentSize(), contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("COME TI SENTI?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Scegli quello che ti somiglia di più: ti propongo un esercizio.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Emotions.all, key = { it.id }) { e ->
                    FilterChip(selected = selected == e.id, onClick = { onPick(e.id) }, label = { Text("${e.emoji} ${e.label}") }, shape = CircleShape)
                }
            }
            val e = selected?.let(Emotions::byId)
            AnimatedVisibility(visible = e != null) {
                if (e != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(e.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        e.sessionIds.mapNotNull(Sessions::byId).forEach { s ->
                            OutlinedButton(onClick = { onSession(s) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                                Text("${s.title} · ${s.minutes} min")
                            }
                        }
                        e.breathId?.let(Breathing::byId)?.let { p ->
                            OutlinedButton(onClick = { onBreath(p.id) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                                Text("${p.title} · 3 min")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: Session, onStart: () -> Unit) {
    SoftCard(modifier = Modifier.clickable(onClick = onStart), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EmojiBubble(emoji = s.category.emoji, size = 48, background = MaterialTheme.colorScheme.primaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(s.about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${s.minutes} min · ${s.category.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Inizia", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PathCard(store: WellbeingStore, onSession: (Session) -> Unit, onBreath: (String) -> Unit) {
    val done = store.pathDone.toSet()
    SoftCard(contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PERCORSO ANTISTRESS · 7 PASSI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(
                "${done.size} di ${StressPath.steps.size} fatti. Nessun ordine obbligato e nessuna scadenza: fai quello che ti va, quando ti va.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StressPath.steps.forEach { st ->
                val isDone = st.refId in done
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            if (st.isBreath) onBreath(st.refId) else Sessions.byId(st.refId)?.let(onSession)
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDone) Icon(Icons.Rounded.Check, contentDescription = "Fatto", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        else Text("${st.day}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(st.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Inizia", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (done.isNotEmpty()) TextButton(onClick = store::resetPath) { Text("Ricomincia il percorso") }
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
            Text("GRATITUDINE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("Quali sono tre cose belle successe oggi?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            GratitudeField(first, { first = it }, "Prima cosa", last = false)
            GratitudeField(second, { second = it }, "Seconda cosa", last = false)
            GratitudeField(third, { third = it }, "Terza cosa", last = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onSave(listOf(first, second, third))
                        first = ""; second = ""; third = ""
                        justSaved = true
                    },
                    enabled = listOf(first, second, third).any { it.isNotBlank() },
                    shape = CircleShape,
                ) { Text("Salva nel diario", style = MaterialTheme.typography.labelLarge) }
                if (justSaved) Text("Salvato ✓", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Le tue risposte restano solo su questo telefono. Le trovi in La mia raccolta › Diario.",
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
        onValueChange = { onChange(it.take(200)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
    )
}

private val DayNames = listOf("L", "M", "M", "G", "V", "S", "D")

@Composable
private fun ProgressCard(store: WellbeingStore) {
    val week = store.last7Days()
    val minutes = store.weekMinutes()
    val days30 = store.activeDaysLast30()
    var confirm by remember { mutableStateOf(false) }
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val max = (week.maxOf { it.seconds }).coerceAtLeast(60)

    SoftCard(contentPadding = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("I TUOI MOMENTI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (store.entries.isEmpty()) {
                Text(
                    "Quando vorrai, qui vedrai il tempo che dedichi a te. Non c'è niente da raggiungere: è solo un modo per accorgertene.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Questa settimana: $minutes ${if (minutes == 1) "minuto" else "minuti"}. Negli ultimi 30 giorni hai trovato tempo per te in $days30 ${if (days30 == 1) "giorno" else "giorni"}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    week.forEach { d ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                                val h = size.height * (d.seconds.toFloat() / max)
                                drawRoundRect(trackColor, Offset.Zero, size, CornerRadius(12f, 12f))
                                if (d.seconds > 0) {
                                    drawRoundRect(barColor, Offset(0f, size.height - h.coerceAtLeast(8f)), Size(size.width, h.coerceAtLeast(8f)), CornerRadius(12f, 12f))
                                }
                            }
                            val dow = java.time.LocalDate.ofEpochDay(d.epochDay).dayOfWeek.value
                            Text(DayNames[dow - 1], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = { confirm = true }) { Text("Azzera la cronologia") }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Azzerare la cronologia?") },
            text = { Text("Cancella il tempo registrato e i passi del percorso antistress. Il diario della gratitudine non viene toccato.") },
            confirmButton = { TextButton(onClick = { store.clearAll(); confirm = false }) { Text("Azzera") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } },
        )
    }
}

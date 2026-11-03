@file:OptIn(ExperimentalLayoutApi::class)

package it.jarvis.discover.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.reco.DurationBucket
import it.jarvis.discover.data.reco.Exploration
import it.jarvis.discover.data.reco.ProfileStore
import it.jarvis.discover.data.reco.Recommender
import it.jarvis.discover.data.youtube.VideoItem
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SectionTitle
import it.jarvis.discover.ui.components.SoftCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class Confirm(val title: String, val body: String, val action: String) {
    Learning(
        "Dimenticare ciò che ho imparato?",
        "Argomenti, creator e durate preferite tornano a zero. Cronologia e impostazioni restano.",
        "Dimentica",
    ),
    History("Cancellare la cronologia?", "L'elenco dei video guardati viene svuotato.", "Cancella"),
    All(
        "Azzerare tutto il profilo?",
        "Vengono cancellati ciò che ho imparato, la cronologia e le impostazioni di apprendimento. Interessi, video salvati e creator seguiti non vengono toccati.",
        "Azzera tutto",
    ),
}

/** "Il tuo profilo": ciò che Discover ha imparato di te, modificabile voce per voce e azzerabile. */
@Composable
fun ProfileTab(profile: ProfileStore, onPlay: (VideoItem) -> Unit, wellbeingMinutes: Int, notesThisWeek: Int) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var showAllHistory by remember { mutableStateOf(false) }

    val topics = profile.topics.entries.filter { abs(it.value) >= 0.3 }.sortedByDescending { it.value }
    val channels = profile.channels.entries.filter { abs(it.value) >= 0.3 }.sortedByDescending { it.value }
    val weekAgo = System.currentTimeMillis() - 7 * 86_400_000L
    val watchedThisWeek = profile.history.count { it.at >= weekAgo }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "stats") {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("La tua settimana", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Video guardati: $watchedThisWeek  ·  Minuti per il benessere: $wellbeingMinutes  ·  Note scritte: $notesThisWeek",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Numeri solo per te, senza obiettivi né serie da mantenere: usa l'app quanto ti serve.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "switch") {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Impara da ciò che faccio", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (profile.learning) "Tengo conto di video guardati, «mi piace», salvati e scartati."
                                else "Non registro più nulla di nuovo. Ciò che ho già imparato resta finché non lo azzeri.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = profile.learning, onCheckedChange = profile::changeLearning)
                    }
                    Text(
                        text = if (profile.history.isEmpty()) "Nessun video guardato finora."
                        else "Video guardati negli ultimi 7 giorni: $watchedThisWeek · in cronologia: ${profile.history.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Tutto resta su questo telefono. Un video conta come guardato dopo qualche secondo in vista.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "topics") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(
                    title = "Argomenti",
                    subtitle = "Il peso cresce con ciò che guardi e apprezzi e cala con ciò che scarti. Puoi correggerlo.",
                )
                if (topics.isEmpty()) {
                    Hint("Ancora niente: guarda, apprezza o salva qualche video e qui compariranno gli argomenti che ti interessano davvero.")
                } else {
                    SoftCard(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            topics.forEach { (name, weight) ->
                                WeightRow(
                                    name = name.replaceFirstChar { it.uppercase() },
                                    weight = weight,
                                    onMore = { profile.adjustTopic(name, 1.0) },
                                    onLess = { profile.adjustTopic(name, -1.0) },
                                    onRemove = { profile.removeTopic(name) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (channels.isNotEmpty()) {
            item(key = "channels") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(title = "Creator", subtitle = "Canali che hai premiato o scartato.")
                    SoftCard(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            channels.forEach { (id, weight) ->
                                WeightRow(
                                    name = profile.channelNames[id] ?: "Creator",
                                    weight = weight,
                                    onMore = null,
                                    onLess = null,
                                    onRemove = { profile.removeChannel(id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "durations") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(title = "Durata", subtitle = "Quanto sono lunghi i video che apprezzi.")
                val total = profile.durations.values.sum()
                if (total < 3) {
                    Hint("Servono ancora alcuni video per capire che durata preferisci.")
                } else {
                    SoftCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DurationBucket.entries.forEach { bucket ->
                                val share = (profile.durations[bucket] ?: 0).toFloat() / total
                                ShareBar(bucket.label, share)
                            }
                        }
                    }
                }
            }
        }

        item(key = "explore") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(
                    title = "Scoperta",
                    subtitle = "Ogni tanto propongo un argomento nuovo, per non chiuderti in una bolla. Scegli quanto spesso.",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Exploration.entries.forEach { level ->
                        FilterChip(
                            selected = profile.exploration == level,
                            onClick = { profile.changeExploration(level) },
                            label = { Text(level.label, style = MaterialTheme.typography.labelMedium) },
                            shape = CircleShape,
                        )
                    }
                }
                Text(
                    text = when (profile.exploration) {
                        Exploration.Off -> "Vedrai solo ciò che somiglia ai tuoi interessi e alle tue abitudini."
                        else -> "Circa un video ogni ${profile.exploration.every} sarà un argomento nuovo, segnalato come tale."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "history-title") {
            SectionTitle(title = "Cronologia", subtitle = "Gli ultimi video guardati. Tocca per rivederli.")
        }
        if (profile.history.isEmpty()) {
            item(key = "history-empty") { Hint("La cronologia è vuota.") }
        } else {
            val shown = if (showAllHistory) profile.history.toList() else profile.history.take(8)
            item(key = "history") {
                SoftCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    Column {
                        shown.forEach { entry ->
                            HistoryRow(entry.video, entry.at, onPlay = { onPlay(entry.video) }, onRemove = { profile.removeFromHistory(entry.video.id) })
                        }
                    }
                }
            }
            if (profile.history.size > 8) {
                item(key = "history-more") {
                    TextButton(onClick = { showAllHistory = !showAllHistory }) {
                        Text(if (showAllHistory) "Mostra meno" else "Mostra tutti (${profile.history.size})")
                    }
                }
            }
        }

        item(key = "how") {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Come scelgo i video", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Nessuna scatola nera: ogni video riceve punti da regole semplici, e sotto al video vedi i motivi principali.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(
                        "Corrisponde a un tuo interesse o all'umore di oggi",
                        "Argomento e creator che hai già apprezzato (o scartato)",
                        "Creator che segui",
                        "Durata simile a quella che guardi di solito",
                        "Pubblicato di recente",
                        "Segnali di qualità: rapporto «mi piace»/visualizzazioni, sottotitoli, lingua italiana certa",
                        "Meno punti se il titolo sembra clickbait (TUTTO MAIUSCOLO, «!!», frasi da esca)",
                        "Molti meno punti se l'hai già visto",
                        "Variazione: evito troppi video di fila sullo stesso argomento o canale",
                        "Ogni tanto un argomento nuovo, segnalato",
                    ).forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
                    Text(
                        text = "Limite importante: non posso verificare se un video dice il vero. I segnali di qualità sono indizi, non una garanzia: " +
                            "valuta sempre tu le fonti, soprattutto su salute, scienza e notizie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item(key = "reset") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirm = Confirm.Learning }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                    Text("Dimentica ciò che ho imparato")
                }
                OutlinedButton(onClick = { confirm = Confirm.History }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                    Text("Cancella la cronologia")
                }
                OutlinedButton(onClick = { confirm = Confirm.All }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                    Text("Azzera tutto il profilo", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.body) },
            confirmButton = {
                TextButton(onClick = {
                    when (c) {
                        Confirm.Learning -> profile.resetLearning()
                        Confirm.History -> profile.clearHistory()
                        Confirm.All -> profile.resetAll()
                    }
                    confirm = null
                }) { Text(c.action) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WeightRow(
    name: String,
    weight: Double,
    onMore: (() -> Unit)?,
    onLess: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val positive = weight > 0
            val level = (abs(weight) / ProfileStore.LIMIT).toFloat().coerceIn(0.04f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(level)
                        .clip(CircleShape)
                        .background(if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                )
            }
            Text(
                text = if (positive) "Ti interessa" else "Meno interesse",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onLess != null) {
            IconButton(onClick = onLess) { Icon(Icons.Rounded.Remove, contentDescription = "Meno di $name") }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) { Icon(Icons.Rounded.Add, contentDescription = "Più di $name") }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = "Dimentica $name") }
    }
}

@Composable
private fun ShareBar(label: String, share: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun HistoryRow(video: VideoItem, at: Long, onPlay: () -> Unit, onRemove: () -> Unit) {
    val date = remember(at) { SimpleDateFormat("d MMM", Locale.ITALIAN).format(Date(at)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOf(video.channelTitle, date).filter { it.isNotBlank() }.joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = "Rimuovi dalla cronologia", modifier = Modifier.size(20.dp))
        }
    }
}

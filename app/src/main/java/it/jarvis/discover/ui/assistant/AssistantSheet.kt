@file:OptIn(ExperimentalLayoutApi::class)

package it.jarvis.discover.ui.assistant

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.jarvis.discover.data.assistant.AssistantEngine
import it.jarvis.discover.data.assistant.AssistantRules
import it.jarvis.discover.data.assistant.Card
import it.jarvis.discover.data.assistant.ChatMessage
import it.jarvis.discover.data.wellbeing.Session
import it.jarvis.discover.data.youtube.VideoFeed
import it.jarvis.discover.data.youtube.VideoItem
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.VideoPlayerDialog
import it.jarvis.discover.ui.components.VideoRow
import it.jarvis.discover.ui.components.openUrl

@Composable
fun AssistantSheet(
    engine: AssistantEngine,
    feed: VideoFeed,
    onStartSession: (Session) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    val listState = rememberLazyListState()
    val messages = engine.messages

    LaunchedEffect(messages.size, engine.busy) {
        val last = messages.size + (if (engine.busy) 1 else 0)
        if (last > 0) listState.animateScrollToItem(last) // 0 = intestazione
    }

    fun send() {
        if (input.isBlank() || engine.busy) return
        engine.ask(input)
        input = ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .imePadding(),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Header(engine, onOpenSettings) }
            if (messages.isEmpty()) {
                item(key = "examples") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Prova a chiedere", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Suggestions(AssistantRules.EXAMPLES.take(6)) { engine.ask(it) }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageView(
                    message = message,
                    feed = feed,
                    onSuggestion = { engine.ask(it) },
                    onPlayVideo = { playing = it },
                    onStartSession = onStartSession,
                    engine = engine,
                )
            }
            if (engine.busy) {
                item(key = "busy") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Sto cercando…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.take(500) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Chiedi un video, un libro, una pausa…") },
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            IconButton(onClick = ::send, enabled = input.isNotBlank() && !engine.busy) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Invia", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    playing?.let { VideoPlayerDialog(item = it, onDismiss = { playing = null }) }

    if (engine.pendingConsent) {
        AlertDialog(
            onDismissRequest = { engine.declineConsent() },
            title = { Text("Usare l'IA di Google?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Per rispondere, l'app invia a Gemini (Google) il testo della tua richiesta, le ultime battute della conversazione e i tuoi interessi scelti. Per «spiegami questo video» invia anche titolo e descrizione del video.")
                    Text("Non invia la cronologia dei video, il diario né le chiavi. Con la chiave gratuita Google può usare ciò che invii per migliorare i suoi prodotti e dei revisori umani possono leggerlo: non scrivere dati personali o riservati.")
                    Text("Puoi cambiare idea in Impostazioni.")
                }
            },
            confirmButton = { TextButton(onClick = { engine.acceptConsent() }) { Text("Accetto e continuo") } },
            dismissButton = { TextButton(onClick = { engine.declineConsent() }) { Text("Non ora") } },
        )
    }
}

@Composable
private fun Header(engine: AssistantEngine, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiBubble(emoji = "✨", size = 44)
            Column(modifier = Modifier.weight(1f)) {
                Text("Il tuo assistente", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (engine.aiActive) "Cerca contenuti reali · IA Gemini attiva" else "Cerca contenuti reali · IA non attiva",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (engine.messages.isNotEmpty()) {
                TextButton(onClick = engine::newConversation) { Text("Nuova") }
            }
        }
        if (engine.messages.isEmpty()) {
            Text(
                text = "Trovo video, libri, audiolibri, podcast e notizie veri, non inventati, e ti guido in una pausa di mindfulness. " +
                    if (engine.aiActive) "Con l'IA posso anche rispondere a domande libere e spiegarti un video."
                    else "Per domande libere e spiegazioni puoi attivare l'IA (opzionale) nelle impostazioni.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!engine.aiActive) {
                TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) {
                    Text("Apri le impostazioni", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Suggestions(texts: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        texts.forEach { t ->
            SuggestionChip(
                onClick = { onPick(t) },
                label = { Text(t, style = MaterialTheme.typography.labelMedium) },
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun MessageView(
    message: ChatMessage,
    feed: VideoFeed,
    engine: AssistantEngine,
    onSuggestion: (String) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onStartSession: (Session) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    when {
                        message.fromUser -> MaterialTheme.colorScheme.primaryContainer
                        message.isError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
                if (message.ai) {
                    Text(
                        "Scritto dall'IA · può contenere errori",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        message.cards.forEach { card ->
            when (card) {
                is Card.Video -> VideoRow(
                    video = card.item,
                    saved = feed.isSaved(card.item.id),
                    onPlay = { onPlayVideo(card.item) },
                    onToggleSave = { feed.toggleSave(card.item) },
                )
                is Card.BookCard -> ResultRow(
                    image = card.book.coverUrl,
                    emoji = "📚",
                    title = card.book.title,
                    subtitle = listOfNotNull(card.book.authors.firstOrNull(), card.book.year?.toString()).joinToString(" · "),
                    action = "Apri la scheda",
                    onClick = { openUrl(context, card.book.archiveUrl ?: card.book.pageUrl) },
                )
                is Card.AudiobookCard -> ResultRow(
                    image = null,
                    emoji = "🎙️",
                    title = card.book.title,
                    subtitle = card.book.author + " · LibriVox",
                    action = "Ascolta",
                    onClick = { engine.playAudiobook(card.book) },
                )
                is Card.PodcastCard -> ResultRow(
                    image = card.podcast.artwork.ifBlank { null },
                    emoji = "🎧",
                    title = card.podcast.title,
                    subtitle = card.podcast.author,
                    action = "Ascolta",
                    onClick = { engine.playPodcast(card.podcast) },
                )
                is Card.Article -> ResultRow(
                    image = card.article.entry.imageUrl,
                    emoji = "🌞",
                    title = card.article.entry.title,
                    subtitle = card.article.sourceName,
                    action = "Leggi sul sito",
                    onClick = { openUrl(context, card.article.entry.link) },
                )
                is Card.SessionCard -> ResultRow(
                    image = null,
                    emoji = card.session.category.emoji,
                    title = card.session.title,
                    subtitle = "${card.session.category.label} · ${card.session.minutes} min",
                    action = "Inizia",
                    onClick = { onStartSession(card.session) },
                )
            }
        }

        if (message.suggestions.isNotEmpty()) Suggestions(message.suggestions, onSuggestion)
    }
}

@Composable
private fun ResultRow(image: String?, emoji: String, title: String, subtitle: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        } else {
            EmojiBubble(emoji = emoji, size = 56)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package it.jarvis.discover.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.content.AudioItem
import it.jarvis.discover.data.content.AudioResult
import it.jarvis.discover.data.content.LibraryStore
import it.jarvis.discover.data.content.Podcast
import it.jarvis.discover.data.content.PodcastClient
import it.jarvis.discover.playback.AudioController
import it.jarvis.discover.ui.components.ScreenPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

private val PodcastIdeas = listOf("psicologia", "filosofia", "scienza", "intelligenza artificiale", "crescita personale", "storia", "mindfulness", "genitorialità")

@Composable
fun PodcastsScreen(library: LibraryStore, audio: AudioController, interests: List<String>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = remember { PodcastClient() }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Podcast?>(null) }
    var lastTerm by remember { mutableStateOf("") }

    fun search(term: String) {
        if (term.isBlank()) return
        loading = true; error = null; title = "Podcast per «${term.trim()}»"; lastTerm = term
        scope.launch {
            when (val r = withContext(Dispatchers.IO) { client.search(term) }) {
                is AudioResult.Ok -> { results = r.value; loading = false }
                is AudioResult.Err -> { results = emptyList(); error = r.message; loading = false }
            }
        }
    }
    LaunchedEffect(Unit) { if (results.isEmpty() && library.podcasts.isEmpty()) search(interests.firstOrNull() ?: "psicologia") }

    Column(Modifier.fillMaxSize()) {
        ContentTopBar("Podcast", "Ricerca su Apple Podcasts · audio dal server dell'editore", onBack)
        SearchField(query, { query = it }, "Cerca un podcast o un argomento", onSearch = { search(query) })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    (interests.take(3) + PodcastIdeas).distinct().take(9).forEach { t -> AssistChip(onClick = { query = t; search(t) }, label = { Text(t) }, shape = CircleShape) }
                }
            }
            if (library.podcasts.isNotEmpty()) {
                item { Text("I tuoi podcast", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(library.podcasts.toList(), key = { "s" + it.id }) { p -> PodcastRow(p, true, { open = p }, { library.toggleSubscribe(p) }) }
            }
            if (title.isNotBlank()) item { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            item { LoadState(loading, error, onRetry = { search(lastTerm) }) }
            items(results, key = { "r" + it.id }) { p -> PodcastRow(p, library.isSubscribed(p.id), { open = p }, { library.toggleSubscribe(p) }) }
            if (!loading && error == null && title.isNotBlank() && results.isEmpty()) item {
                Text("Nessun podcast con feed riproducibile. Prova altre parole.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    open?.let { PodcastSheet(it, library, audio, onDismiss = { open = null }) }
}

@Composable
fun PodcastRow(p: Podcast, subscribed: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(p.artwork, 64.dp, 64.dp)
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOf(p.author, p.genre).filter { it.isNotBlank() }.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggle) {
            Icon(
                if (subscribed) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = if (subscribed) "Non seguire più" else "Segui",
                tint = if (subscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PodcastSheet(podcast: Podcast, library: LibraryStore, audio: AudioController, onDismiss: () -> Unit) {
    val client = remember { PodcastClient() }
    var episodes by remember(podcast.id) { mutableStateOf<List<AudioItem>>(emptyList()) }
    var loading by remember(podcast.id) { mutableStateOf(true) }
    var error by remember(podcast.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(podcast.id, reload) {
        loading = true; error = null
        when (val r = withContext(Dispatchers.IO) { client.episodes(podcast) }) {
            is AudioResult.Ok -> episodes = r.value
            is AudioResult.Err -> error = r.message
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 720.dp),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(podcast.artwork, 80.dp, 80.dp)
                    Column(Modifier.weight(1f)) {
                        Text(podcast.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(podcast.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (library.isSubscribed(podcast.id)) OutlinedButton(onClick = { library.toggleSubscribe(podcast) }, shape = CircleShape) { Text("Non seguire più") }
                    else Button(onClick = { library.toggleSubscribe(podcast) }, shape = CircleShape) { Text("Segui") }
                    if (episodes.isNotEmpty()) OutlinedButton(onClick = { audio.play(episodes, 0) }, shape = CircleShape) { Text("Ascolta l'ultimo") }
                }
            }
            item { LoadState(loading, error, onRetry = { reload++ }) }
            items(episodes, key = { it.id }) { ep -> EpisodeRow(ep, library, audio, onPlay = { audio.play(episodes, episodes.indexOf(ep)) }) }
            if (!loading && error == null && episodes.isEmpty()) item {
                Text("Nessun episodio riproducibile in questo feed (servono audio in https).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EpisodeRow(ep: AudioItem, library: LibraryStore, audio: AudioController, onPlay: () -> Unit) {
    val playing = audio.current?.id == ep.id && audio.isPlaying
    val saved = library.isAudioSaved(ep.id)
    val resume = library.positionOf(ep.id)
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(ep.title, style = MaterialTheme.typography.titleSmall, color = if (audio.current?.id == ep.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val date = ep.publishedMillis.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
                ?.let { it.substring(8, 10) + "/" + it.substring(5, 7) + "/" + it.substring(0, 4) }
            Text(
                listOfNotNull(date, formatMinutes(ep.durationSec).takeIf { it.isNotBlank() }, if (resume > 5000) "ripresa da ${formatClock(resume)}" else null).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ep.summary.isNotBlank()) Text(ep.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { library.toggleAudioSaved(ep) }) {
            Icon(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, contentDescription = if (saved) "Rimuovi dai salvati" else "Salva", tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = if (playing) audio::toggle else onPlay) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = if (playing) "In riproduzione" else "Ascolta", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

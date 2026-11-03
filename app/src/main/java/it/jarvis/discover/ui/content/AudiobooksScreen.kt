@file:OptIn(ExperimentalMaterial3Api::class)

package it.jarvis.discover.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.content.AudioResult
import it.jarvis.discover.data.content.Audiobook
import it.jarvis.discover.data.content.AudiobookClient
import it.jarvis.discover.data.content.AudiobookDetail
import it.jarvis.discover.data.content.LibraryStore
import it.jarvis.discover.playback.AudioController
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AudiobooksScreen(library: LibraryStore, audio: AudioController, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = remember { AudiobookClient() }
    var query by remember { mutableStateOf("") }
    var books by remember { mutableStateOf<List<Audiobook>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Audiobook?>(null) }
    var term by remember { mutableStateOf("") }

    fun load(q: String) {
        loading = true; error = null; term = q
        scope.launch {
            when (val r = withContext(Dispatchers.IO) { client.list(q) }) {
                is AudioResult.Ok -> books = r.value
                is AudioResult.Err -> { books = emptyList(); error = r.message }
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load("") }

    Column(Modifier.fillMaxSize()) {
        ContentTopBar("Audiolibri", "LibriVox in italiano · letti da volontari, pubblico dominio", onBack)
        SearchField(query, { query = it }, "Titolo o autore", onSearch = { load(query) })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    "Sono opere di pubblico dominio lette da volontari (progetto LibriVox, ospitato da Internet Archive). " +
                        "Il catalogo in italiano è piccolo e la qualità delle letture varia: non ci sono best seller moderni, protetti da copyright.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item { LoadState(loading, error, onRetry = { load(term) }) }
            items(books, key = { it.id }) { b ->
                Row(
                    Modifier.fillMaxWidth().clickable { open = b }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(null, 48.dp, 48.dp, shape = CircleShape)
                    Column(Modifier.weight(1f)) {
                        Text(b.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (b.author.isNotBlank()) Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Icon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (!loading && error == null && books.isEmpty()) item {
                Text("Nessun audiolibro trovato per questa ricerca.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    open?.let { AudiobookSheet(it, library, audio, onDismiss = { open = null }) }
}

@Composable
fun AudiobookSheet(book: Audiobook, library: LibraryStore, audio: AudioController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val client = remember { AudiobookClient() }
    var detail by remember(book.id) { mutableStateOf<AudiobookDetail?>(null) }
    var loading by remember(book.id) { mutableStateOf(true) }
    var error by remember(book.id) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(book.id, reload) {
        loading = true; error = null
        when (val r = withContext(Dispatchers.IO) { client.detail(book) }) {
            is AudioResult.Ok -> detail = r.value
            is AudioResult.Err -> error = r.message
        }
        loading = false
    }
    val tracks = detail?.tracks.orEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 720.dp),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(book.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                detail?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp), maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tracks.isNotEmpty()) {
                        val resumeIndex = tracks.indexOfLast { library.positionOf(it.id) > 5000 }.coerceAtLeast(0)
                        Button(onClick = { audio.play(tracks, resumeIndex) }, shape = CircleShape) { Text(if (resumeIndex > 0 || library.positionOf(tracks[0].id) > 5000) "Riprendi" else "Ascolta dall'inizio") }
                    }
                    OutlinedButton(onClick = { openUrl(context, book.pageUrl) }, shape = CircleShape) { Text("Pagina del libro") }
                }
                Text("Letto da volontari di LibriVox · pubblico dominio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { LoadState(loading, error, onRetry = { reload++ }) }
            if (!loading && error == null && tracks.isEmpty()) item {
                Text("Non trovo file audio riproducibili per questo libro.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(tracks, key = { it.id }) { t ->
                val current = audio.current?.id == t.id
                Row(Modifier.fillMaxWidth().clickable { audio.play(tracks, tracks.indexOf(t)) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.bodyLarge, color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = listOf(formatMinutes(t.durationSec), library.positionOf(t.id).takeIf { it > 5000 }?.let { "ripresa da ${formatClock(it)}" }.orEmpty()).filter { it.isNotBlank() }.joinToString("  ·  ")
                        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { audio.play(tracks, tracks.indexOf(t)) }) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Ascolta", tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

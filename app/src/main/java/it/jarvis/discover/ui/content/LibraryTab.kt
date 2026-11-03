package it.jarvis.discover.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.content.Book
import it.jarvis.discover.data.content.LibraryStore
import it.jarvis.discover.data.content.Podcast
import it.jarvis.discover.data.content.Shelf
import it.jarvis.discover.playback.AudioController
import it.jarvis.discover.ui.components.EmptyState
import it.jarvis.discover.ui.components.ScreenPadding

/** Scheda "Libri e ascolto" della Raccolta: scaffali dei libri, podcast seguiti e audio salvati. */
@Composable
fun LibraryTab(library: LibraryStore, audio: AudioController, interests: List<String>) {
    var shelf by rememberSaveable { mutableStateOf(Shelf.ToRead.name) }
    var openBook by remember { mutableStateOf<Book?>(null) }
    var openPodcast by remember { mutableStateOf<Podcast?>(null) }
    val current = Shelf.valueOf(shelf)
    val books = library.books.filter { it.shelf == current }

    if (library.books.isEmpty() && library.podcasts.isEmpty() && library.savedAudio.isEmpty()) {
        EmptyState(
            emoji = "📚",
            title = "La tua libreria è vuota",
            body = "In Scopri → Libri puoi mettere i libri su «Da leggere», «Sto leggendo» e «Letto»; podcast ed episodi salvati compariranno qui.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { Text("Libri", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Shelf.entries.forEach { s ->
                    val n = library.books.count { it.shelf == s }
                    FilterChip(selected = current == s, onClick = { shelf = s.name }, label = { Text("${s.label} ($n)") }, shape = CircleShape)
                }
            }
        }
        if (books.isEmpty()) item {
            Text("Nessun libro in «${current.label}».", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        items(books, key = { "b" + it.book.key }) { sb -> BookRow(sb.book, sb.shelf, onClick = { openBook = sb.book }) }

        if (library.podcasts.isNotEmpty()) {
            item { Text("Podcast che segui", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp)) }
            items(library.podcasts.toList(), key = { "p" + it.id }) { p -> PodcastRow(p, true, { openPodcast = p }, { library.toggleSubscribe(p) }) }
        }
        if (library.savedAudio.isNotEmpty()) {
            item { Text("Episodi e capitoli salvati", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp)) }
            items(library.savedAudio.toList(), key = { "a" + it.id }) { ep ->
                Column(Modifier.fillMaxWidth()) {
                    Text(ep.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    EpisodeRow(ep, library, audio, onPlay = { audio.play(listOf(ep), 0) })
                }
            }
        }
    }
    openBook?.let { BookSheet(it, library, interests, onDismiss = { openBook = null }) }
    openPodcast?.let { PodcastSheet(it, library, audio, onDismiss = { openPodcast = null }) }
}

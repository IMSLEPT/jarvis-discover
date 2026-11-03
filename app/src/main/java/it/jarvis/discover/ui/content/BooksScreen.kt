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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import it.jarvis.discover.data.content.Book
import it.jarvis.discover.data.content.BooksResult
import it.jarvis.discover.data.content.LibraryStore
import it.jarvis.discover.data.content.OpenLibraryClient
import it.jarvis.discover.data.content.ReadingPath
import it.jarvis.discover.data.content.ReadingPaths
import it.jarvis.discover.data.content.reasonsFor
import it.jarvis.discover.data.content.Shelf
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.openUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BooksScreen(library: LibraryStore, interests: List<String>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = remember { OpenLibraryClient() }
    var query by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf("") }          // titolo della lista mostrata
    var about by remember { mutableStateOf("") }
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var italianOnly by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<Book?>(null) }
    var lastQuery by remember { mutableStateOf("") }

    fun load(q: String, title: String, why: String) {
        if (q.isBlank()) return
        loading = true; error = null; shown = title; about = why; lastQuery = q
        scope.launch {
            val r = withContext(Dispatchers.IO) { client.search(q, italianOnly) }
            loading = false
            when (r) {
                is BooksResult.Ok -> books = r.books
                is BooksResult.Err -> { books = emptyList(); error = r.message }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (books.isEmpty() && interests.isNotEmpty()) load(interests.first(), "Per il tuo interesse «${interests.first()}»", "Risultati di Open Library per un argomento che hai scelto.")
    }

    Column(Modifier.fillMaxSize()) {
        ContentTopBar("Libri", "Catalogo Open Library · filtro lingua italiana", onBack)
        SearchField(query, { query = it }, "Titolo, autore o argomento", onSearch = { load(query, "Risultati per «${query.trim()}»", "") })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Solo libri con edizione italiana", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = italianOnly, onCheckedChange = { italianOnly = it; if (lastQuery.isNotBlank()) load(lastQuery, shown, about) })
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text("Percorsi tematici", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                Text(
                    "Raccolte automatiche: i titoli arrivano da Open Library per argomento, non sono una classifica di esperti.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(ReadingPaths, key = { it.id }) { p -> PathChip(p) { load(p.query, p.title, p.about) } }
                }
            }
            if (shown.isNotBlank()) item {
                Text(shown, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                if (about.isNotBlank()) Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { LoadState(loading, error, onRetry = { load(lastQuery, shown, about) }) }
            if (!loading && error == null && shown.isNotBlank() && books.isEmpty()) item {
                Text(
                    "Nessun libro trovato. Prova con altre parole, oppure disattiva il filtro sull'italiano.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(books, key = { it.key }) { b ->
                BookRow(b, library.shelfOf(b.key), onClick = { detail = b })
            }
        }
    }
    detail?.let { BookSheet(it, library, interests, onDismiss = { detail = null }) }
}

@Composable
private fun PathChip(p: ReadingPath, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text("${p.emoji} ${p.title}") }, shape = CircleShape)
}

@Composable
fun BookRow(book: Book, shelf: Shelf?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(book.coverUrl, 64.dp, 96.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(book.authors.joinToString(", ").takeIf { it.isNotBlank() }, book.year?.toString()).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            )
            Text(
                if (book.inItalian) "Edizione italiana disponibile" else "Edizione italiana non indicata",
                style = MaterialTheme.typography.labelSmall,
                color = if (book.inItalian) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (shelf != null) Text("Sullo scaffale: ${shelf.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
fun BookSheet(book: Book, library: LibraryStore, interests: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val client = remember { OpenLibraryClient() }
    var description by remember(book.key) { mutableStateOf<String?>(null) }
    LaunchedEffect(book.key) { description = withContext(Dispatchers.IO) { client.description(book.key) } }
    val reasons = remember(book.key, interests) { reasonsFor(book, interests) }
    val shelf = library.shelfOf(book.key)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(book.coverUrl, 96.dp, 144.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(book.authors.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOfNotNull(book.year?.let { "Prima pubblicazione $it" }, "${book.editionCount} edizioni").joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (book.inItalian) "Disponibile in italiano" else "Edizione italiana non indicata nel catalogo",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text("Il tuo scaffale", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Shelf.entries.forEach { s ->
                    FilterChip(selected = shelf == s, onClick = { library.setShelf(book, if (shelf == s) null else s) }, label = { Text(s.label) }, shape = CircleShape)
                }
            }
            if (reasons.isNotEmpty()) {
                Text("Perché potrebbe interessarti", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Tra i suoi argomenti ci sono parole vicine ai tuoi interessi: " + reasons.joinToString(", ") { "«$it»" } + ".",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text("Descrizione", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                when {
                    description == null -> "Carico la descrizione…"
                    description!!.isBlank() -> "Open Library non ha una descrizione per quest'opera."
                    else -> description!!
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            )
            if (description?.isNotBlank() == true) {
                Text("Testo di Open Library: può essere in inglese o non tradotto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (book.subjects.isNotEmpty()) {
                Text("Argomenti principali", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    book.subjects.forEach { AssistChip(onClick = {}, label = { Text(it, maxLines = 1) }, shape = CircleShape) }
                }
            }
            Text("Dove trovarlo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Button(onClick = { openUrl(context, book.pageUrl) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Scheda su Open Library (edizioni e prestito)") }
            book.archiveUrl?.let { url ->
                OutlinedButton(onClick = { openUrl(context, url) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
                    Text(if (book.hasFulltext) "Edizione digitale su Internet Archive" else "Edizione su Internet Archive")
                }
            }
            OutlinedButton(onClick = { openUrl(context, book.searchOnGoogleBooksUrl) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Cerca su Google Libri (anteprime e acquisto)") }
            Text(
                "L'app non contiene né distribuisce libri: mostra le schede del catalogo e rimanda ai servizi che li offrono legalmente.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

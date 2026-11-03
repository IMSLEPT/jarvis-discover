package it.jarvis.discover.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.creators.Creator
import it.jarvis.discover.data.creators.FollowLevel
import it.jarvis.discover.data.creators.UniversalResult
import it.jarvis.discover.data.youtube.ApiResult
import it.jarvis.discover.data.youtube.FeedFilter
import it.jarvis.discover.data.youtube.VideoFeed
import it.jarvis.discover.data.youtube.VideoItem
import it.jarvis.discover.data.youtube.YtError
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.VideoPlayerDialog
import it.jarvis.discover.ui.components.VideoRow
import it.jarvis.discover.ui.creators.CreatorCard
import kotlinx.coroutines.launch

/**
 * Suggerimenti di ricerca ispirati agli esempi del progetto. Sono SOLO parole da cercare: l'app non
 * conosce in anticipo i canali ufficiali, li trova al momento su YouTube e li mostra con i loro dati reali.
 */
val SearchSuggestions = listOf(
    "Raffaele Gaito", "TED italiano", "TEDx", "Steve Jobs discorso", "David Goggins", "Wim Hof",
    "psicologia", "filosofia", "intelligenza artificiale", "educazione dei figli", "divulgazione scientifica",
)

enum class SearchSort(val label: String) { Relevance("Rilevanza"), Recent("Più recenti"), Viewed("Più visti") }

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val query: String, val result: UniversalResult) : SearchState
    data class Failed(val error: YtError?) : SearchState
}

/** Ricerca universale: persone, canali, argomenti e conferenze. Una richiesta consuma 1 ricerca gratuita; @handle no. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchSheet(feed: VideoFeed, acceptSubtitled: Boolean, onOpenSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var filter by remember { mutableStateOf(FeedFilter.All) }
    var sort by remember { mutableStateOf(SearchSort.Relevance) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }

    fun run(text: String) {
        val q = text.trim()
        if (q.length < 2) return
        if (!feed.hasKey) { state = SearchState.Failed(null); return }
        state = SearchState.Loading
        scope.launch {
            val result: ApiResult<UniversalResult>? = if (q.startsWith("@") && q.length > 2 && ' ' !in q) {
                // @handle: channels.list, non consuma ricerche.
                feed.withKey { key, client ->
                    when (val r = client.channelByHandle(key, q)) {
                        is ApiResult.Ok -> ApiResult.Ok(UniversalResult(listOfNotNull(r.value), emptyList()))
                        is ApiResult.Err -> r
                    }
                }
            } else {
                feed.withKey { key, client -> client.universalSearch(key, q) }.also { if (it is ApiResult.Ok) feed.noteSearches(1) }
            }
            state = when (result) {
                null -> SearchState.Failed(null)
                is ApiResult.Err -> { feed.noteError(result.error); SearchState.Failed(result.error) }
                is ApiResult.Ok -> SearchState.Done(q, result.value)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = ScreenPadding).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Cerca", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Persona, canale, argomento o conferenza") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = ""; state = SearchState.Idle }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancella")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { run(query) }),
        )

        when {
            !feed.hasKey -> NeedKey(onOpenSettings)
            else -> when (val s = state) {
                SearchState.Idle -> Suggestions(onPick = { query = it; run(it) }, remaining = feed.remainingSearches())
                SearchState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is SearchState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        s.error?.userMessage ?: "Non riesco a cercare: manca la chiave di YouTube.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { run(query) }, shape = CircleShape) { Text("Riprova") }
                }
                is SearchState.Done -> Results(
                    state = s, feed = feed, acceptSubtitled = acceptSubtitled,
                    filter = filter, onFilter = { filter = it }, sort = sort, onSort = { sort = it },
                    onPlay = { playing = it },
                )
            }
        }
    }

    playing?.let { VideoPlayerDialog(item = it, onDismiss = { playing = null }) }
}

@Composable
private fun NeedKey(onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "La ricerca usa YouTube e ha bisogno della tua chiave API gratuita, che resta cifrata sul telefono.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onOpenSettings, shape = CircleShape) { Text("Apri le impostazioni") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(onPick: (String) -> Unit, remaining: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Prova a cercare", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchSuggestions.forEach { s ->
                AssistChip(onClick = { onPick(s) }, label = { Text(s) }, shape = CircleShape)
            }
        }
        Text(
            "Ogni ricerca usa 1 delle 100 ricerche gratuite giornaliere di YouTube (oggi ne restano circa $remaining). " +
                "Scrivendo un @nome esatto (es. @nomecanale) trovi il canale senza consumarne. " +
                "Quelli sopra sono solo spunti di ricerca, non canali garantiti: l'app mostra ciò che YouTube trova davvero.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Results(
    state: SearchState.Done,
    feed: VideoFeed,
    acceptSubtitled: Boolean,
    filter: FeedFilter,
    onFilter: (FeedFilter) -> Unit,
    sort: SearchSort,
    onSort: (SearchSort) -> Unit,
    onPlay: (VideoItem) -> Unit,
) {
    val store = feed.creators
    val creators = state.result.creators.filter { store.get(it.id)?.level != FollowLevel.Excluded }
    val videos = state.result.videos
        .filter { it.isShown(acceptSubtitled) && it.id !in feed.hiddenIds && store.get(it.channelId)?.level != FollowLevel.Excluded }
        .filter { filter.matches(it) }
        .let { list ->
            when (sort) {
                SearchSort.Relevance -> list
                SearchSort.Recent -> list.sortedByDescending { it.publishedAt }
                SearchSort.Viewed -> list.sortedByDescending { it.views ?: 0L }
            }
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { onFilter(f) }, label = { Text(f.label) }, shape = CircleShape)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchSort.entries.forEach { s ->
                FilterChip(selected = sort == s, onClick = { onSort(s) }, label = { Text(s.label) }, shape = CircleShape)
            }
        }
        if (creators.isEmpty() && videos.isEmpty()) {
            Text(
                "Nessun risultato adatto per «${state.query}». Prova con altre parole oppure, se hai attivato i sottotitolati, controlla i filtri.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(modifier = Modifier.heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (creators.isNotEmpty()) {
                item { Text("Creator e canali", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                items(creators, key = { "c" + it.id }) { c ->
                    CreatorCard(
                        creator = c,
                        following = store.isFollowing(c.id),
                        onOpen = { store.open(c) },
                        onToggleFollow = { if (store.isFollowing(c.id)) store.unfollow(c.id) else store.follow(c) },
                    )
                }
            }
            if (videos.isNotEmpty()) {
                item { Text("Video", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                items(videos, key = { "v" + it.id }) { v ->
                    VideoRow(
                        video = v,
                        saved = feed.isSaved(v.id),
                        onPlay = { onPlay(v) },
                        onToggleSave = { feed.toggleSave(v) },
                    )
                }
            }
        }
    }
}

package it.jarvis.discover.ui.creators

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.jarvis.discover.data.creators.Creator
import it.jarvis.discover.data.creators.CreatorRef
import it.jarvis.discover.data.creators.CreatorUpdates
import it.jarvis.discover.data.creators.FollowLevel
import it.jarvis.discover.data.youtube.ApiResult
import it.jarvis.discover.data.youtube.VideoFeed
import it.jarvis.discover.data.youtube.VideoItem
import it.jarvis.discover.data.youtube.YtError
import it.jarvis.discover.data.youtube.YtErrorKind
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.VideoPlayerDialog
import it.jarvis.discover.ui.components.VideoRow
import it.jarvis.discover.ui.components.openUrl

private sealed interface PageState {
    data object Loading : PageState
    data class Ready(val videos: List<VideoItem>) : PageState
    data class Failed(val error: YtError) : PageState
}

/**
 * Pagina di un creator: seguirlo, scegliere quanto vederlo, filtrare per argomenti, attivare le
 * notifiche e sfogliare i suoi video (recenti oppure i più visti tra gli ultimi 50 caricati).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatorPage(ref: CreatorRef, feed: VideoFeed, acceptSubtitled: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = feed.creators
    var creator by remember(ref.id) { mutableStateOf(ref.creator) }
    var state by remember(ref.id) { mutableStateOf<PageState>(PageState.Loading) }
    var mostViewed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var topicInput by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(ref.id, reload, feed.keyVersion) {
        state = PageState.Loading
        // Dettagli del canale, se arrivo da un video e non li ho ancora.
        var c = creator
        if (c == null) {
            val r = feed.withKey { key, client -> client.channels(key, listOf(ref.id)) }
            when (r) {
                null -> { state = PageState.Failed(YtError(YtErrorKind.InvalidKey)); return@LaunchedEffect }
                is ApiResult.Err -> { feed.noteError(r.error); state = PageState.Failed(r.error); return@LaunchedEffect }
                is ApiResult.Ok -> c = r.value.firstOrNull()
            }
            if (c == null) { state = PageState.Failed(YtError(YtErrorKind.Other, detail = "canale non trovato")); return@LaunchedEffect }
            creator = c
        }
        val target = c
        when (val r = feed.withKey { key, client -> client.creatorVideos(key, target) }) {
            null -> state = PageState.Failed(YtError(YtErrorKind.InvalidKey))
            is ApiResult.Err -> { feed.noteError(r.error); state = PageState.Failed(r.error) }
            is ApiResult.Ok -> {
                if (r.value.searchesUsed > 0) feed.noteSearches(r.value.searchesUsed)
                state = PageState.Ready(r.value.videos)
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            store.setNotify(ref.id, true)
            notice = "Notifiche attivate. Controllo circa ogni 6 ore; Android decide l'orario esatto."
        } else {
            notice = "Senza il permesso per le notifiche non posso avvisarti."
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val follow = store.get(ref.id)
            val shown = (state as? PageState.Ready)?.videos.orEmpty()
                .filter { it.isShown(acceptSubtitled) }
                .let { list -> if (mostViewed) list.sortedByDescending { it.views ?: 0L } else list }

            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Indietro") }
                        Text("Creator", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    val c = creator
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CreatorAvatar(c?.thumbnail.orEmpty(), 72.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(c?.title ?: ref.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                                if (c != null) Text(creatorStats(c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (c != null && c.description.isNotBlank()) {
                            Text(
                                c.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5,
                            )
                        }
                        if (c != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (follow == null) {
                                    Button(onClick = { store.follow(c) }, shape = CircleShape) { Text("Segui") }
                                } else {
                                    OutlinedButton(onClick = { store.unfollow(ref.id) }, shape = CircleShape) { Text("Non seguire più") }
                                }
                                OutlinedButton(onClick = { openUrl(context, c.channelUrl) }, shape = CircleShape) { Text("Apri su YouTube") }
                            }
                        }
                        Text(
                            "Seguire una persona non significa che tutto ciò che dice sia corretto: ascolta con spirito critico e verifica i fatti importanti.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (follow != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            Text("Quanto vederlo nel feed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FollowLevel.entries.forEach { level ->
                                    FilterChip(
                                        selected = follow.level == level,
                                        onClick = { store.setLevel(ref.id, level) },
                                        label = { Text(level.label) },
                                        shape = CircleShape,
                                    )
                                }
                            }
                            Text("Solo certi argomenti", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "Se aggiungi parole (es. «intelligenza artificiale»), nel feed vedrai di questo creator solo i video che le nominano nel titolo o nella descrizione.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (follow.topics.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    follow.topics.forEach { t ->
                                        InputChip(
                                            selected = true,
                                            onClick = { store.setTopics(ref.id, follow.topics - t) },
                                            label = { Text(t) },
                                            trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Rimuovi $t", modifier = Modifier.padding(0.dp)) },
                                            shape = CircleShape,
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = topicInput,
                                onValueChange = { topicInput = it.take(40) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = { Text("Aggiungi un argomento") },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (topicInput.isNotBlank()) {
                                        store.setTopics(ref.id, follow.topics + topicInput)
                                        topicInput = ""
                                    }
                                }),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Avvisami per i nuovi video", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "Facoltativo: una notifica per creator, al massimo ogni qualche ora.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = follow.notify,
                                    onCheckedChange = { on ->
                                        if (!on) {
                                            store.setNotify(ref.id, false)
                                        } else if (CreatorUpdates.canNotify(context)) {
                                            store.setNotify(ref.id, true)
                                            notice = "Notifiche attivate. Controllo circa ogni 6 ore; Android decide l'orario esatto."
                                        } else if (Build.VERSION.SDK_INT >= 33) {
                                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            notice = "Le notifiche di Discover sono disattivate nelle impostazioni di Android."
                                        }
                                    },
                                )
                            }
                            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        FilterChip(selected = !mostViewed, onClick = { mostViewed = false }, label = { Text("Più recenti") }, shape = CircleShape)
                        FilterChip(selected = mostViewed, onClick = { mostViewed = true }, label = { Text("Più visti") }, shape = CircleShape)
                    }
                    if (mostViewed) {
                        Text(
                            "Ordinati per visualizzazioni tra gli ultimi 50 video caricati, non in assoluto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when (val s = state) {
                    PageState.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    is PageState.Failed -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                            Text(s.error.userMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = { reload++ }, shape = CircleShape) { Text("Riprova") }
                        }
                    }
                    is PageState.Ready -> {
                        if (shown.isEmpty()) {
                            item {
                                Text(
                                    "Nessun video adatto: restano esclusi quelli non incorporabili, in diretta, troppo brevi o in lingua straniera senza sottotitoli (puoi accettare i sottotitolati dalle impostazioni).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                        }
                        items(shown, key = { it.id }) { v ->
                            VideoRow(
                                video = v,
                                saved = feed.isSaved(v.id),
                                onPlay = { playing = v },
                                onToggleSave = { feed.toggleSave(v) },
                                showChannel = false,
                            )
                        }
                    }
                }
            }
        }
    }

    playing?.let { VideoPlayerDialog(item = it, onDismiss = { playing = null }) }
}

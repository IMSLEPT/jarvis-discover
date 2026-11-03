@file:OptIn(ExperimentalLayoutApi::class)

package it.jarvis.discover.ui.notes

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.notes.Note
import it.jarvis.discover.data.notes.NoteKind
import it.jarvis.discover.data.notes.NotesStore
import it.jarvis.discover.ui.components.EmptyState
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SectionTitle
import it.jarvis.discover.ui.components.SoftCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Il quaderno: note libere, «cose imparate» e il riepilogo della settimana. */
@Composable
fun NotesTab(store: NotesStore) {
    var kind by remember { mutableStateOf(NoteKind.Learned) }
    var text by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Note?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    val learnedThisWeek = store.recent(7, NoteKind.Learned)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "composer") {
            SoftCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cosa vuoi ricordare?", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NoteKind.entries.forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = { kind = k },
                                label = { Text(k.label, style = MaterialTheme.typography.labelMedium) },
                                shape = CircleShape,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(NotesStore.MAX_TEXT) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (kind == NoteKind.Learned) "Oggi ho imparato che…" else "Scrivi una nota…") },
                        minLines = 3,
                        maxLines = 8,
                        shape = MaterialTheme.shapes.medium,
                    )
                    OutlinedTextField(
                        value = ref,
                        onValueChange = { ref = it.take(NotesStore.MAX_REF) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Da dove viene (facoltativo)") },
                        placeholder = { Text("Un video, un libro, una conversazione…") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                    Button(
                        onClick = { if (store.add(kind, text, ref)) { text = ""; ref = "" } },
                        enabled = text.isNotBlank(),
                        shape = CircleShape,
                    ) { Text("Salva nel quaderno", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        if (learnedThisWeek.isNotEmpty()) {
            item(key = "recap") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle(title = "Cosa hai imparato questa settimana", subtitle = "Le tue note «Ho imparato» degli ultimi 7 giorni.")
                    SoftCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            learnedThisWeek.forEach { Text("•  ${it.text}", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }

        if (store.notes.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    emoji = "📓",
                    title = "Il quaderno è vuoto",
                    body = "Quando impari qualcosa, scrivilo qui con parole tue: è il modo migliore per ricordarlo. Nessun obbligo, nessun contatore.",
                )
            }
        } else {
            item(key = "list-title") { SectionTitle(title = "Il tuo quaderno") }
            items(store.notes, key = { it.id }) { note ->
                NoteCard(note, onEdit = { editing = note }, onDelete = { deleting = note })
            }
        }
    }

    editing?.let { note ->
        var draft by remember(note.id) { mutableStateOf(note.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Modifica la nota") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(NotesStore.MAX_TEXT) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                )
            },
            confirmButton = { TextButton(onClick = { store.update(note.id, draft); editing = null }, enabled = draft.isNotBlank()) { Text("Salva") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annulla") } },
        )
    }

    deleting?.let { note ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminare la nota?") },
            text = { Text("Verrà cancellata dal telefono.") },
            confirmButton = { TextButton(onClick = { store.delete(note.id); deleting = null }) { Text("Elimina") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun NoteCard(note: Note, onEdit: () -> Unit, onDelete: () -> Unit) {
    val date = remember(note.at) { SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).format(Date(note.at)) }
    SoftCard(contentPadding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "${note.kind.label} · $date" + (note.ref?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(note.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Modifica") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Elimina") }
            }
        }
    }
}

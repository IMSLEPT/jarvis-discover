package it.jarvis.discover.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.GratitudeEntry
import it.jarvis.discover.ui.components.EmptyState
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SoftCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CollectionTabs = listOf("Video", "Libri", "Diario", "Creator")

@Composable
fun RaccoltaScreen(
    gratitude: List<GratitudeEntry>,
    onDeleteGratitude: (GratitudeEntry) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("La mia raccolta", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                text = "Ciò che hai salvato e scritto, tutto in un posto.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TabRow(
            selectedTabIndex = tab,
            modifier = Modifier.padding(top = 12.dp),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            CollectionTabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        when (tab) {
            0 -> EmptyState(
                emoji = "🔖",
                title = "Nessun video salvato",
                body = "Tocca il segnalibro su un video del feed per ritrovarlo qui.",
            )
            1 -> EmptyState(
                emoji = "📚",
                title = "La tua libreria è vuota",
                body = "Qui troverai i libri da leggere, in lettura e già letti.",
            )
            2 -> DiaryList(gratitude, onDeleteGratitude)
            else -> EmptyState(
                emoji = "🌟",
                title = "Nessun creator seguito",
                body = "Le persone che segui per ricevere nuovi contenuti compariranno qui.",
            )
        }
    }
}

@Composable
private fun DiaryList(entries: List<GratitudeEntry>, onDelete: (GratitudeEntry) -> Unit) {
    if (entries.isEmpty()) {
        EmptyState(
            emoji = "🌿",
            title = "Il diario è ancora vuoto",
            body = "Scrivi tre cose belle della tua giornata dalla sezione Benessere: resteranno qui.",
        )
        return
    }
    val format = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.epochMillis }) { entry ->
            SoftCard(contentPadding = PaddingValues(start = 18.dp, top = 14.dp, bottom = 16.dp, end = 6.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = format.format(Date(entry.epochMillis)).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        IconButton(onClick = { onDelete(entry) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Elimina voce",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    entry.items.forEach { item ->
                        Text(
                            text = "•  $item",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

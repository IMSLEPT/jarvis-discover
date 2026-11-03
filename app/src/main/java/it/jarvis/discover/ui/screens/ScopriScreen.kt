@file:OptIn(ExperimentalLayoutApi::class)

package it.jarvis.discover.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.InterestCategories
import it.jarvis.discover.data.InterestCategory
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SectionTitle
import it.jarvis.discover.ui.components.SoftCard
import it.jarvis.discover.ui.theme.Palette

private data class Shelf(val emoji: String, val title: String, val subtitle: String, val gradient: List<Color>, val onOpen: () -> Unit)

@Composable
fun ScopriScreen(
    selected: List<String>,
    onToggleTopic: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onManageInterests: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenBooks: () -> Unit,
    onOpenPodcasts: () -> Unit,
    onOpenAudiobooks: () -> Unit,
    onOpenVideos: () -> Unit,
) {
    val shelves = listOf(
        Shelf("🌞", "Notizie", "Dalle testate italiane", listOf(Palette.OrangeDeep, Palette.OrangeLight), onOpenNews),
        Shelf("📚", "Libri", "Catalogo Open Library", listOf(Palette.BlueDeep, Palette.BlueLight), onOpenBooks),
        Shelf("🎧", "Podcast", "Apple Podcasts + feed", listOf(Palette.PlumDeep, Palette.PlumLight), onOpenPodcasts),
        Shelf("🎙️", "Audiolibri", "LibriVox in italiano", listOf(Palette.Ink, Palette.InkLight), onOpenAudiobooks),
        Shelf("🎤", "Creator ed esperti", "Cerca e segui", listOf(Palette.RoseDeep, Palette.RoseLight), onOpenSearch),
        Shelf("🎓", "Conferenze", "Nel feed Video", listOf(Palette.TealDeep, Palette.TealLight), onOpenVideos),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "search") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Cerca un argomento, una persona, una conferenza…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        item(key = "my-interests") {
            SoftCard(contentPadding = PaddingValues(0.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onManageInterests)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    EmojiBubble(emoji = "🎯", size = 48, background = MaterialTheme.colorScheme.primaryContainer)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("I tuoi interessi", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = when (selected.size) {
                                0 -> "Non ne hai ancora scelto nessuno"
                                1 -> "1 argomento scelto"
                                else -> "${selected.size} argomenti scelti"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Gestisci", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item(key = "shelves-title") {
            SectionTitle(title = "Cosa vuoi scoprire?")
        }
        items(shelves.chunked(2), key = { row -> row.first().title }) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { shelf -> ShelfTile(shelf, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }

        item(key = "topics-title") {
            SectionTitle(
                title = "Esplora per argomento",
                subtitle = "Tocca una categoria e scegli i sottoargomenti che ti interessano.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(InterestCategories, key = { it.id }) { category ->
            CategoryCard(category, selected, onToggleTopic)
        }
    }
}

@Composable
private fun ShelfTile(shelf: Shelf, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(shelf.gradient))
            .clickable(onClick = shelf.onOpen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EmojiBubble(emoji = shelf.emoji, size = 44, background = Color.White.copy(alpha = 0.2f))
        Text(shelf.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
        Text(shelf.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun CategoryCard(category: InterestCategory, selected: List<String>, onToggleTopic: (String) -> Unit) {
    var expanded by rememberSaveable(category.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val chosen = category.topics.count { it in selected }

    SoftCard(
        modifier = Modifier.animateContentSize(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EmojiBubble(emoji = category.emoji, size = 48, background = category.accent.copy(alpha = 0.18f))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(category.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (chosen == 0) "${category.topics.size} argomenti" else "$chosen di ${category.topics.size} scelti",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (chosen == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Comprimi" else "Espandi",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation),
                )
            }
            AnimatedVisibility(visible = expanded) {
                FlowRow(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    category.topics.forEach { topic ->
                        FilterChip(
                            selected = topic in selected,
                            onClick = { onToggleTopic(topic) },
                            label = { Text(topic, style = MaterialTheme.typography.labelMedium) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }
    }
}

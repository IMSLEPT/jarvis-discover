package it.jarvis.discover.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import it.jarvis.discover.data.youtube.Format
import it.jarvis.discover.data.youtube.VideoItem
import it.jarvis.discover.ui.components.VideoPlayerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import it.jarvis.discover.data.content.LibraryStore
import it.jarvis.discover.data.creators.Creator
import it.jarvis.discover.data.reco.ProfileStore
import it.jarvis.discover.playback.AudioController
import it.jarvis.discover.ui.profile.ProfileTab
import it.jarvis.discover.ui.content.LibraryTab
import it.jarvis.discover.data.creators.FollowedCreator
import it.jarvis.discover.ui.creators.CreatorAvatar
import it.jarvis.discover.data.InterestCategories
import it.jarvis.discover.data.categoryOf
import it.jarvis.discover.ui.components.EmptyState
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SoftCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CollectionTabs = listOf("Video", "Libri e ascolto", "Diario", "Creator", "Interessi", "Profilo")

@Composable
fun RaccoltaScreen(
    savedVideos: List<VideoItem>,
    onRemoveVideo: (VideoItem) -> Unit,
    gratitude: List<GratitudeEntry>,
    onDeleteGratitude: (GratitudeEntry) -> Unit,
    interests: List<String>,
    onRemoveInterest: (String) -> Unit,
    onManageInterests: () -> Unit,
    follows: List<FollowedCreator>,
    onOpenCreator: (Creator) -> Unit,
    library: LibraryStore,
    audio: AudioController,
    profile: ProfileStore,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<VideoItem?>(null) }

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

        ScrollableTabRow(
            selectedTabIndex = tab,
            modifier = Modifier.padding(top = 12.dp),
            containerColor = MaterialTheme.colorScheme.background,
            edgePadding = 12.dp,
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
            0 -> SavedVideos(savedVideos, onPlay = { playing = it }, onRemove = onRemoveVideo)
            1 -> LibraryTab(library, audio, interests)
            2 -> DiaryList(gratitude, onDeleteGratitude)
            3 -> FollowedList(follows, onOpenCreator)
            4 -> InterestsTab(interests, onRemoveInterest, onManageInterests)
            else -> ProfileTab(profile, onPlay = { playing = it })
        }
    }

    playing?.let { VideoPlayerDialog(item = it, onDismiss = { playing = null }) }
}

@Composable
private fun InterestsTab(
    interests: List<String>,
    onRemove: (String) -> Unit,
    onManage: () -> Unit,
) {
    if (interests.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                emoji = "🎯",
                title = "Non hai ancora scelto interessi",
                body = "Dimmi cosa ti appassiona e costruirò il tuo feed attorno a questo.",
            )
            Button(onClick = onManage, shape = CircleShape) {
                Text("Scegli i tuoi interessi", style = MaterialTheme.typography.labelLarge)
            }
        }
        return
    }

    val groups = InterestCategories.mapNotNull { category ->
        val topics = interests.filter { it in category.topics }
        if (topics.isEmpty()) null else category to topics
    }
    val others = interests.filter { categoryOf(it) == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "manage") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${interests.size} ${if (interests.size == 1) "argomento" else "argomenti"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onManage, shape = CircleShape) {
                    Text("Modifica", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        items(groups, key = { it.first.id }) { (category, topics) ->
            InterestGroup(emoji = category.emoji, title = category.title, topics = topics, onRemove = onRemove)
        }
        if (others.isNotEmpty()) {
            item(key = "others") {
                InterestGroup(emoji = "✍️", title = "I tuoi argomenti", topics = others, onRemove = onRemove)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestGroup(emoji: String, title: String, topics: List<String>, onRemove: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$emoji  $title",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            topics.forEach { topic ->
                InputChip(
                    selected = true,
                    onClick = { onRemove(topic) },
                    label = { Text(topic, style = MaterialTheme.typography.labelMedium) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Rimuovi $topic",
                            modifier = Modifier.size(InputChipDefaults.IconSize),
                        )
                    },
                    shape = CircleShape,
                )
            }
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

@Composable
private fun SavedVideos(videos: List<VideoItem>, onPlay: (VideoItem) -> Unit, onRemove: (VideoItem) -> Unit) {
    if (videos.isEmpty()) {
        EmptyState(
            emoji = "🔖",
            title = "Nessun video salvato",
            body = "Tocca «Salva» su un video del feed per ritrovarlo qui.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(videos, key = { it.id }) { video ->
            SoftCard(contentPadding = PaddingValues(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPlay(video) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        AsyncImage(
                            model = video.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text(
                            text = Format.duration(video.durationSec),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            video.channelTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onRemove(video) }) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkRemove,
                            contentDescription = "Rimuovi dalla raccolta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowedList(follows: List<FollowedCreator>, onOpen: (Creator) -> Unit) {
    if (follows.isEmpty()) {
        EmptyState(
            emoji = "🌟",
            title = "Nessun creator seguito",
            body = "Cerca una persona o un canale con la lente in alto e premi «Segui»: i suoi nuovi video compariranno nel feed.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(follows, key = { it.creator.id }) { f ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onOpen(f.creator) }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CreatorAvatar(f.creator.thumbnail, 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(f.creator.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val details = buildList {
                        add(f.level.label)
                        if (f.topics.isNotEmpty()) add("solo: " + f.topics.joinToString(", "))
                        if (f.notify) add("con notifiche")
                    }.joinToString("  ·  ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

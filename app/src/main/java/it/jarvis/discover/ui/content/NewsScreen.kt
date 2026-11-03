package it.jarvis.discover.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.jarvis.discover.data.content.NewsArticle
import it.jarvis.discover.data.content.NewsGroup
import it.jarvis.discover.data.content.NewsRepository
import it.jarvis.discover.data.content.NewsSources
import it.jarvis.discover.data.content.NewsStatus
import it.jarvis.discover.data.youtube.Format
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SoftCard
import it.jarvis.discover.ui.components.openUrl
import java.time.Instant
import java.time.ZoneId

@Composable
fun NewsScreen(news: NewsRepository, onBack: () -> Unit) {
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { news.refresh() }
    val shown = news.articles.filter { group == null || it.group.name == group }

    Column(Modifier.fillMaxSize()) {
        ContentTopBar(
            title = "Notizie",
            subtitle = "Dalle testate italiane, in tempo reale",
            onBack = onBack,
            actions = {
                IconButton(onClick = { news.refresh(force = true) }, enabled = news.status != NewsStatus.Loading) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna")
                }
            },
        )
        Row(Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = group == null, onClick = { group = null }, label = { Text("Tutte") }, shape = CircleShape)
            NewsGroup.entries.forEach { g ->
                FilterChip(selected = group == g.name, onClick = { group = g.name }, label = { Text(g.label) }, shape = CircleShape)
            }
        }
        if (news.status == NewsStatus.Loading && news.articles.isEmpty()) {
            LoadState(loading = true, error = null, onRetry = {})
        } else if (news.status == NewsStatus.Failed) {
            LoadState(loading = false, error = "Non riesco a leggere le testate. Controlla la connessione e riprova.", onRetry = { news.refresh(force = true) })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.entry.link }) { a -> ArticleCard(a) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (news.failedSources.isNotEmpty()) {
                            Text(
                                "Non hanno risposto: ${news.failedSources.joinToString(", ")}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            "Titoli e anteprime sono quelli dei feed pubblici delle testate; l'articolo completo si apre sul loro sito. " +
                                "Fonti: " + NewsSources.joinToString(", ") { it.name } + ". Le testate si propongono cose diverse: l'app non verifica né giudica le singole notizie.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(a: NewsArticle) {
    val context = LocalContext.current
    SoftCard(modifier = Modifier.clickable { openUrl(context, a.entry.link) }, contentPadding = PaddingValues(0.dp)) {
        Column {
            a.entry.imageUrl?.let { img ->
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.large),
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val date = a.entry.publishedMillis.takeIf { it > 0 }?.let {
                    Format.published(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                }.orEmpty()
                Text(
                    listOf(a.sourceName, date).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(a.entry.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (a.entry.summary.isNotBlank()) {
                    Text(
                        a.entry.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("Leggi sul sito di ${a.sourceName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

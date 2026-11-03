package it.jarvis.discover.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import it.jarvis.discover.data.content.NewsArticle
import it.jarvis.discover.data.content.NewsGroup
import it.jarvis.discover.data.content.NewsRepository
import it.jarvis.discover.data.content.NewsStatus
import it.jarvis.discover.data.content.TodayBook
import it.jarvis.discover.data.moodById
import it.jarvis.discover.data.today.DailyContent
import it.jarvis.discover.data.youtube.FeedFilter
import it.jarvis.discover.data.youtube.VideoFeed
import it.jarvis.discover.ui.components.BrandHeadline
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SectionTitle
import it.jarvis.discover.ui.components.SoftCard
import it.jarvis.discover.ui.components.openUrl
import it.jarvis.discover.ui.content.Cover
import it.jarvis.discover.ui.content.formatMinutes
import it.jarvis.discover.ui.theme.Brand
import it.jarvis.discover.ui.theme.Palette
import java.time.LocalTime

private enum class HomeSection { Thought, News, Video, Reflection, Gratitude, Science, Relation, Book, Idea }

private val TimeOptions = listOf(2, 5, 10, 20, 0)

/** Quante sezioni mostrare in base al tempo che l'utente ha scelto di dedicare all'app. */
private fun sectionCountFor(minutes: Int): Int = when (minutes) {
    2 -> 3
    5 -> 5
    10 -> 7
    else -> HomeSection.entries.size
}

@Composable
fun OggiScreen(
    modes: List<String>,
    minutes: Int,
    interests: List<String>,
    acceptSubtitled: Boolean,
    news: NewsRepository,
    videos: VideoFeed,
    todayBook: TodayBook,
    onMinutesChange: (Int) -> Unit,
    onOpenVideo: () -> Unit,
    onOpenBenessere: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenBooks: () -> Unit,
) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..12 -> "Buongiorno!"
            in 13..17 -> "Buon pomeriggio!"
            else -> "Buonasera!"
        }
    }
    val day = remember { DailyContent.today() }
    val sections = HomeSection.entries.take(sectionCountFor(minutes))

    LaunchedEffect(Unit) { news.refresh() }
    LaunchedEffect(interests) { todayBook.load(interests) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BrandHeadline(greeting)
                Text(
                    text = "Oggi scopri qualcosa di nuovo.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (modes.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        items(modes) { id ->
                            moodById(id)?.let { mood ->
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(mood.gradient))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(mood.emoji, fontSize = 14.sp)
                                    Text(mood.title, style = MaterialTheme.typography.labelMedium, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "time") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Quanto tempo hai?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TimeOptions) { option ->
                        FilterChip(
                            selected = option == minutes,
                            onClick = { onMinutesChange(option) },
                            label = { Text(if (option == 0) "Libero" else "$option min", style = MaterialTheme.typography.labelLarge) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }

        items(sections, key = { it.name }) { section ->
            when (section) {
                HomeSection.Thought -> ThoughtCard(DailyContent.pick(DailyContent.thoughts, day))
                HomeSection.News -> NewsShelf(news, onOpenNews)
                HomeSection.Video -> VideoSpotlight(videos, interests, modes, acceptSubtitled, onOpenVideo)
                HomeSection.Reflection -> ReflectionCard(DailyContent.pick(DailyContent.questions, day, salt = 1))
                HomeSection.Gratitude -> GratitudeCard(onOpenBenessere)
                HomeSection.Science -> ScienceCard(news, onOpenNews)
                HomeSection.Relation -> TipCard("🤝", "UN PICCOLO GESTO PER LE RELAZIONI", DailyContent.pick(DailyContent.relationTips, day, salt = 2))
                HomeSection.Book -> BookCard(todayBook, onOpenBooks)
                HomeSection.Idea -> TipCard("💡", "UN'IDEA DA SPERIMENTARE OGGI", DailyContent.pick(DailyContent.ideas, day, salt = 4))
            }
        }
    }
}

/** Etichetta che dice chiaramente che il testo è dell'app e non di una persona o di una testata. */
@Composable
private fun WrittenForDiscover() {
    Text("Scritto per Discover", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ThoughtCard(text: String) {
    // Bordo a gradiente blu→arancione, come l'anello dell'icona.
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .border(BorderStroke(1.5.dp, Brand.gradient), MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PENSIERO DEL GIORNO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 30.sp), color = MaterialTheme.colorScheme.onSurface)
            WrittenForDiscover()
        }
    }
}

@Composable
private fun NewsShelf(news: NewsRepository, onOpenNews: () -> Unit) {
    val context = LocalContext.current
    // Tre notizie dalle testate "positive", una per testata quando possibile (l'elenco arriva già alternato).
    val picks = news.articles.filter { it.group == NewsGroup.Positive }.distinctBy { it.sourceId }.take(3)
        .ifEmpty { news.articles.take(3) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(title = "Notizie dal mondo che costruisce", modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenNews) { Text("Tutte") }
        }
        when {
            picks.isNotEmpty() -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = ScreenPadding),
            ) {
                items(picks, key = { it.entry.link }) { a -> NewsMini(a) { openUrl(context, a.entry.link) } }
            }
            news.status == NewsStatus.Failed -> InfoLine("Non riesco a leggere le testate in questo momento. Controlla la connessione.")
            else -> InfoLine("Carico le notizie…")
        }
        Text(
            "Titoli e foto sono quelli delle testate (Italia che Cambia, Vita, LifeGate); l'articolo completo si apre sul loro sito.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewsMini(a: NewsArticle, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            a.entry.imageUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(a.sourceName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(a.entry.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun VideoSpotlight(
    videos: VideoFeed,
    interests: List<String>,
    modes: List<String>,
    acceptSubtitled: Boolean,
    onOpen: () -> Unit,
) {
    // Il feed si prepara in sfondo solo se c'è una chiave YouTube; qui si legge soltanto ciò che è già in memoria.
    LaunchedEffect(videos.hasKey, interests, modes) { if (videos.hasKey) videos.refresh(modes, interests) }
    val video = if (videos.hasKey) videos.visible(FeedFilter.All, acceptSubtitled).firstOrNull() else null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = "Il video consigliato per te")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(Palette.NavyDeep, Palette.BlueDeep)))
                .clickable(onClick = onOpen),
        ) {
            if (video != null) {
                AsyncImage(model = video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC041937)))))
            }
            Box(
                modifier = Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Apri il feed video", tint = Palette.Blue, modifier = Modifier.size(38.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (video != null) {
                    Text(video.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(video.channelTitle, formatMinutes(video.durationSec)).filter { it.isNotBlank() }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                } else {
                    Text(
                        if (videos.hasKey) "Sto preparando il tuo feed: tocca per aprirlo." else "Collega la tua chiave YouTube gratuita per vedere qui un video in italiano.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReflectionCard(question: String) {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("UNA DOMANDA SU CUI RIFLETTERE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(question, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            WrittenForDiscover()
        }
    }
}

@Composable
private fun GratitudeCard(onOpenBenessere: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Palette.BlueDeep, Palette.BlueLight)))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ESERCIZIO DI GRATITUDINE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.92f))
            Text("Quali sono tre cose belle successe oggi?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Button(
                onClick = onOpenBenessere,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Palette.Navy),
            ) {
                Text("Scrivi nel diario", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ScienceCard(news: NewsRepository, onOpenNews: () -> Unit) {
    val context = LocalContext.current
    val article = news.articles.firstOrNull { it.group == NewsGroup.Science } ?: return
    SoftCard(modifier = Modifier.clickable { openUrl(context, article.entry.link) }, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EmojiBubble(emoji = "🔬", size = 48, background = MaterialTheme.colorScheme.primaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("DAL MONDO DELLA SCIENZA · ${article.sourceName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(article.entry.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TipCard(emoji: String, label: String, text: String) {
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            EmojiBubble(emoji = emoji, size = 48, background = MaterialTheme.colorScheme.secondaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                WrittenForDiscover()
            }
        }
    }
}

@Composable
private fun BookCard(today: TodayBook, onOpenBooks: () -> Unit) {
    val context = LocalContext.current
    val book = today.book
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = "Un libro per oggi")
        if (book == null) {
            InfoLine(
                if (today.failed) "Non riesco a consultare il catalogo dei libri in questo momento." else "Cerco un libro nel catalogo Open Library…",
            )
            if (today.failed) OutlinedButton(onClick = onOpenBooks, shape = CircleShape) { Text("Sfoglia i libri") }
        } else {
            SoftCard(modifier = Modifier.clickable { openUrl(context, book.pageUrl) }, contentPadding = PaddingValues(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Cover(book.coverUrl, 82.dp, 120.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(book.authors.firstOrNull(), book.year?.toString()).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        today.path?.let {
                            Text("Dal percorso: ${it.title}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Scheda su Open Library", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                "Il libro è scelto da un percorso di lettura legato ai tuoi interessi e cambia ogni giorno: non è una valutazione di qualità.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenBooks) { Text("Altri libri") }
        }
    }
}

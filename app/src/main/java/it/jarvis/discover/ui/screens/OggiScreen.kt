package it.jarvis.discover.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.jarvis.discover.data.moodById
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.SampleBadge
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SectionTitle
import it.jarvis.discover.ui.components.SoftCard
import it.jarvis.discover.ui.theme.Lora
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
    onMinutesChange: (Int) -> Unit,
    onOpenVideo: () -> Unit,
    onOpenBenessere: () -> Unit,
) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..12 -> "Buongiorno!"
            in 13..17 -> "Buon pomeriggio!"
            else -> "Buonasera!"
        }
    }
    val sections = HomeSection.entries.take(sectionCountFor(minutes))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Oggi scopri qualcosa di nuovo.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (modes.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
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
                Text(
                    text = "Quanto tempo hai?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TimeOptions) { option ->
                        FilterChip(
                            selected = option == minutes,
                            onClick = { onMinutesChange(option) },
                            label = {
                                Text(
                                    text = if (option == 0) "Libero" else "$option min",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }

        items(sections, key = { it.name }) { section ->
            when (section) {
                HomeSection.Thought -> ThoughtCard()
                HomeSection.News -> NewsShelf()
                HomeSection.Video -> VideoSpotlight(onOpenVideo)
                HomeSection.Reflection -> ReflectionCard()
                HomeSection.Gratitude -> GratitudeCard(onOpenBenessere)
                HomeSection.Science -> PreviewRow("🔬", "Una scoperta scientifica", "Una novità dal mondo della ricerca, spiegata semplice.")
                HomeSection.Relation -> PreviewRow("🤝", "Un consiglio per le relazioni", "Un piccolo gesto per migliorare come partner e genitore.")
                HomeSection.Book -> PreviewRow("📖", "Un libro interessante", "Una lettura scelta in base ai tuoi interessi.")
                HomeSection.Idea -> PreviewRow("💡", "Un'idea da sperimentare", "Qualcosa di pratico da provare oggi, in pochi minuti.")
            }
        }
    }
}

@Composable
private fun ThoughtCard() {
    SoftCard(contentPadding = PaddingValues(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PENSIERO DEL GIORNO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                SampleBadge(text = "ESEMPIO")
            }
            Text(
                text = "“Non serve sentirti pronto per iniziare: la chiarezza arriva mentre fai il primo passo.”",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = Lora,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 30.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NewsShelf() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(
            title = "Tre belle notizie",
            subtitle = "Vere, verificate e con la fonte — in arrivo con la fase 5.",
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = ScreenPadding),
        ) {
            items(listOf("🔬" to "Scienza", "🌍" to "Ambiente", "🩺" to "Medicina")) { (emoji, topic) ->
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EmojiBubble(emoji = emoji, size = 40)
                        SampleBadge()
                    }
                    Text(topic, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "Qui compariranno titolo, fonte, data e riassunto di una notizia reale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSpotlight(onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title = "Il video consigliato per te")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(MaterialTheme.shapes.large)
                .background(Brush.linearGradient(listOf(Palette.BlueDeep, Palette.PlumDeep)))
                .clickable(onClick = onOpen),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Apri il feed video",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            SampleBadge(modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
            Text(
                text = "Il feed con video reali in italiano arriva con la fase 3.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ReflectionCard() {
    SoftCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "UNA DOMANDA SU CUI RIFLETTERE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                SampleBadge(text = "ESEMPIO")
            }
            Text(
                text = "Quale piccola abitudine, ripetuta ogni giorno, cambierebbe di più la tua vita tra un anno?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun GratitudeCard(onOpenBenessere: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Palette.GreenDeep, Color(0xFF68804D))))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🌿  ESERCIZIO DI GRATITUDINE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
            Text(
                text = "Quali sono tre cose belle successe oggi?",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Button(
                onClick = onOpenBenessere,
                shape = CircleShape,
            ) {
                Text("Scrivi nel diario", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PreviewRow(emoji: String, title: String, hint: String) {
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EmojiBubble(emoji = emoji, size = 48)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SampleBadge()
        }
    }
}

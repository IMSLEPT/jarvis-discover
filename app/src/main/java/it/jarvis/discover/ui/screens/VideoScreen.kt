@file:OptIn(ExperimentalMaterial3Api::class)

package it.jarvis.discover.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.jarvis.discover.data.Moods
import it.jarvis.discover.ui.components.SampleBadge
import it.jarvis.discover.ui.components.ScreenPadding

/** Pagina di esempio del feed: layout definitivo, contenuto segnaposto (fase 3 = video reali). */
private data class PosterSample(val id: String, val emoji: String, val category: String, val gradient: List<Color>)

private val Posters = Moods.filter { it.id != "per_te" }.take(6).map {
    PosterSample(it.id, it.emoji, it.title, it.gradient)
}

private val VideoFilters = listOf("Tutti", "Brevi", "Conferenze")

@Composable
fun VideoScreen() {
    val pager = rememberPagerState(pageCount = { Posters.size })
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var showSummary by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            VideoPage(poster = Posters[page], onSummary = { showSummary = true })
        }

        // Filtri in alto: lunghezza dei contenuti e lingua.
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(VideoFilters.size) { index ->
                FilterChip(
                    selected = filter == index,
                    onClick = { filter = index },
                    label = { Text(VideoFilters[index], style = MaterialTheme.typography.labelLarge) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        labelColor = Color.White,
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color(0xFF141413),
                    ),
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text("🇮🇹 Italiano", style = MaterialTheme.typography.labelLarge) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        labelColor = Color.White,
                    ),
                )
            }
        }
    }

    if (showSummary) {
        ModalBottomSheet(onDismissRequest = { showSummary = false }) {
            SummarySheetContent()
        }
    }
}

@Composable
private fun VideoPage(poster: PosterSample, onSummary: () -> Unit) {
    var liked by rememberSaveable(poster.id) { mutableStateOf(false) }
    var saved by rememberSaveable(poster.id) { mutableStateOf(false) }
    var captions by rememberSaveable(poster.id) { mutableStateOf(true) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Sfondo: gradiente + alone di luce.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(poster.gradient + Color(0xFF0E0E0D))),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        radius = 900f,
                    ),
                ),
        )
        Text(
            text = poster.emoji,
            fontSize = 112.sp,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 80.dp),
        )

        // Sfumatura scura in basso per la leggibilità del testo.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
        )

        // Testi.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = ScreenPadding, end = 88.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = poster.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                SampleBadge()
            }
            Text(
                text = "Anteprima del feed video",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                text = "Autore · Fonte · Durata",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = "Con la fase 3 qui scorrono video reali in italiano, con sottotitoli e fonte autorizzata.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        // Azioni a destra.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ActionButton(
                icon = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = "Utile",
                tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
                onClick = { liked = !liked },
            )
            ActionButton(
                icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                label = "Salva",
                tint = if (saved) MaterialTheme.colorScheme.primary else Color.White,
                onClick = { saved = !saved },
            )
            ActionButton(
                icon = Icons.Rounded.ClosedCaption,
                label = "ITA",
                tint = if (captions) MaterialTheme.colorScheme.primary else Color.White,
                onClick = { captions = !captions },
            )
            ActionButton(icon = Icons.Rounded.AutoAwesome, label = "Riassunto", onClick = onSummary)
            ActionButton(
                icon = Icons.Rounded.Share,
                label = "Condividi",
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Scoperto con Discover")
                    }
                    context.startActivity(Intent.createChooser(send, "Condividi"))
                },
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.32f), CircleShape),
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun SummarySheetContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Riassunto AI", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        SummaryBlock("In sintesi", "Il riassunto del video, in parole semplici.")
        SummaryBlock("Punti chiave da ricordare", "Tre o quattro idee da portare con te.")
        SummaryBlock("Approfondimenti correlati", "Altri contenuti per andare più a fondo sull'argomento.")
        Text(
            text = "Disponibile con la fase 8 (assistente AI).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package it.jarvis.discover.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.jarvis.discover.data.youtube.FeedFilter
import it.jarvis.discover.data.youtube.FeedStatus
import it.jarvis.discover.data.youtube.Format
import it.jarvis.discover.data.youtube.LangClass
import it.jarvis.discover.data.youtube.VideoFeed
import it.jarvis.discover.data.youtube.VideoItem
import it.jarvis.discover.data.youtube.YtErrorKind
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.YouTubePlayer
import it.jarvis.discover.ui.components.openUrl
import it.jarvis.discover.ui.components.shareText
import it.jarvis.discover.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val Night = Color(0xFF050E20)
private val SpeedSteps = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)

/** Sotto questo numero di video per un filtro, l'app cerca altri video di quel tipo. */
private const val MIN_FILTER_ITEMS = 6

/** Tetto di ricerche automatiche per ogni visita alla schermata (ognuna è un blocco di 4 ricerche di quota). */
private const val MAX_AUTO_LOADS = 5

/** Dopo quanto tempo in vista un video conta come guardato. */
private const val WATCH_MARK_MS = 8_000L

@Composable
fun VideoScreen(
    feed: VideoFeed,
    modes: List<String>,
    interests: List<String>,
    acceptSubtitled: Boolean,
    onOpenSettings: () -> Unit,
    onOpenCreator: (channelId: String, title: String) -> Unit = { _, _ -> },
) {
    var filter by rememberSaveable { mutableStateOf(FeedFilter.All) }
    var followedOnly by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var details by remember { mutableStateOf<VideoItem?>(null) }
    var autoLoads by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(feed.hasKey, feed.keyVersion) {
        if (feed.hasKey) feed.refresh(modes, interests)
    }

    val hasFollows = feed.creators.follows.isNotEmpty()
    val items = feed.visible(filter, acceptSubtitled).let { all ->
        if (followedOnly && hasFollows) all.filter { feed.creators.isFollowing(it.channelId) } else all
    }
    val pager = rememberPagerState(pageCount = { items.size })

    fun requestMore() {
        if (autoLoads < MAX_AUTO_LOADS && feed.loadMore(modes, interests, filter)) autoLoads++
    }

    LaunchedEffect(filter, followedOnly) {
        if (items.isNotEmpty()) pager.scrollToPage(0)
        if (feed.hasKey && feed.pool.isNotEmpty() && filter != FeedFilter.All && items.size < MIN_FILTER_ITEMS) requestMore()
    }
    LaunchedEffect(pager.settledPage) {
        if (items.isNotEmpty() && pager.settledPage >= items.size - 3) requestMore()
    }

    // Un video conta come "guardato" solo se resta in vista per qualche secondo (non basta scorrerlo).
    val currentId = items.getOrNull(pager.settledPage)?.id
    LaunchedEffect(currentId) {
        val watched = items.getOrNull(pager.settledPage) ?: return@LaunchedEffect
        feed.focus = watched
        delay(WATCH_MARK_MS)
        feed.markWatched(watched)
    }

    val error = feed.lastError
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(error.userMessage)
            feed.clearError()
        }
    }

    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Night)) {
        when {
            !feed.hasKey -> NoKeyState(onOpenSettings)
            items.isEmpty() -> FeedEmptyState(
                feed = feed,
                filtered = feed.pool.isNotEmpty(),
                onRetry = { feed.refresh(modes, interests, force = true) },
                onMore = { feed.loadMore(modes, interests, filter) },
                onOpenSettings = onOpenSettings,
            )
            else -> VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { index -> items.getOrNull(index)?.id ?: index },
            ) { page ->
                val item = items.getOrNull(page) ?: return@VerticalPager
                VideoPage(
                    video = item,
                    why = feed.whyThis(item),
                    isCurrent = page == pager.settledPage,
                    speed = speed,
                    liked = feed.isLiked(item.id),
                    saved = feed.isSaved(item.id),
                    onCycleSpeed = { speed = SpeedSteps[(SpeedSteps.indexOf(speed) + 1).mod(SpeedSteps.size)] },
                    onLike = { feed.toggleLike(item) },
                    onSave = {
                        val wasSaved = feed.isSaved(item.id)
                        feed.toggleSave(item)
                        say(if (wasSaved) "Rimosso dalla Raccolta" else "Salvato in Raccolta")
                    },
                    onMoreLike = {
                        if (feed.moreLike(item)) say("Ne cercherò altri su «${item.topic}»")
                    },
                    onHide = {
                        feed.hide(item)
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar("Non vedrai più questo video", actionLabel = "Annulla")
                            if (result == SnackbarResult.ActionPerformed) feed.unhide(item.id)
                        }
                    },
                    onDetails = { details = item },
                    onShare = { shareText(context, item.title, "${item.title}\n${item.shareUrl}") },
                    onOpenYouTube = { if (!openUrl(context, item.watchUrl)) say("Nessuna app può aprire il link") },
                    onOpenCreator = { onOpenCreator(item.channelId, item.channelTitle) },
                )
            }
        }

        if (feed.hasKey) {
            FilterBar(
                filter = filter,
                onFilter = { filter = it },
                followedOnly = followedOnly,
                onFollowedOnly = if (hasFollows) ({ followedOnly = !followedOnly }) else null,
                busy = feed.busy,
                onRefresh = {
                    if (feed.remainingSearches() <= 0) {
                        say("Ricerche gratuite finite per oggi: si rinnovano verso le 9:00.")
                    } else {
                        feed.refresh(modes, interests, force = true)
                        say("Aggiorno il feed (usa ${minOf(4, feed.remainingSearches())} ricerche gratuite)")
                    }
                },
                onSettings = onOpenSettings,
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )
    }

    details?.let { item ->
        ModalBottomSheet(onDismissRequest = { details = null }) {
            DetailsSheet(item = item, onOpenYouTube = { openUrl(context, item.watchUrl) })
        }
    }
}

// --------------------------------------------------------------------------- barra filtri

@Composable
private fun FilterBar(
    filter: FeedFilter,
    onFilter: (FeedFilter) -> Unit,
    followedOnly: Boolean,
    onFollowedOnly: (() -> Unit)?,
    busy: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(FeedFilter.entries) { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(option.label, style = MaterialTheme.typography.labelLarge) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        labelColor = Color.White,
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Palette.Dark,
                    ),
                )
            }
            if (onFollowedOnly != null) {
                item {
                    FilterChip(
                        selected = followedOnly,
                        onClick = onFollowedOnly,
                        label = { Text("Seguiti", style = MaterialTheme.typography.labelLarge) },
                        shape = CircleShape,
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.12f),
                            labelColor = Color.White,
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Palette.Dark,
                        ),
                    )
                }
            }
        }
        if (busy) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            }
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna il feed", tint = Color.White)
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Impostazioni", tint = Color.White)
        }
    }
}

// --------------------------------------------------------------------------- pagina video

@Composable
private fun VideoPage(
    video: VideoItem,
    why: List<String>,
    isCurrent: Boolean,
    speed: Float,
    liked: Boolean,
    saved: Boolean,
    onCycleSpeed: () -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onMoreLike: () -> Unit,
    onHide: () -> Unit,
    onDetails: () -> Unit,
    onShare: () -> Unit,
    onOpenYouTube: () -> Unit,
    onOpenCreator: () -> Unit,
) {
    // Per i video con sottotitoli ma audio non italiano si parte con i sottotitoli accesi.
    var captionsChoice by remember(video.id) { mutableStateOf<Boolean?>(null) }
    val captions = captionsChoice ?: (video.lang == LangClass.Subtitled)
    var playerError by remember(video.id) { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.3f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Night.copy(alpha = 0.6f), Night.copy(alpha = 0.93f), Night)),
            ),
        )

        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            PlayerArea(
                item = video,
                isCurrent = isCurrent,
                captions = captions,
                speed = speed,
                error = playerError,
                onError = { playerError = it },
                onOpenYouTube = onOpenYouTube,
                onHide = onHide,
            )

            Text(
                text = "Il video non si vede? Aprilo su YouTube",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .padding(horizontal = ScreenPadding, vertical = 6.dp)
                    .clickable(onClick = onOpenYouTube),
            )

            Column(
                modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(languageLabel(video.lang))
                    Pill(Format.duration(video.durationSec))
                    video.views?.let { Pill("${Format.count(it)} visualizzazioni") }
                }
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(video.channelTitle, Format.published(video.publishedAt)).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenCreator),
                )
                if (why.isNotEmpty()) {
                    Text(
                        text = "Perché lo vedi: " + why.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Orange,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            LazyRow(
                modifier = Modifier.padding(top = 18.dp),
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { ActionButton(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Mi piace", liked, onLike) }
                item { ActionButton(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "Salva", saved, onSave) }
                item {
                    ActionButton(
                        icon = if (captions) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionOff,
                        label = "Sottotitoli",
                        active = captions,
                        onClick = { captionsChoice = !captions },
                    )
                }
                item { ActionButton(Icons.Rounded.Speed, speedLabel(speed), speed != 1f, onCycleSpeed) }
                item { ActionButton(Icons.Rounded.ThumbUp, "Più così", false, onMoreLike) }
                item { ActionButton(Icons.Rounded.VisibilityOff, "Non mi interessa", false, onHide) }
                item { ActionButton(Icons.Rounded.Info, "Dettagli", false, onDetails) }
                item { ActionButton(Icons.Rounded.Share, "Condividi", false, onShare) }
                item { ActionButton(Icons.Rounded.OpenInNew, "YouTube", false, onOpenYouTube) }
            }

            if (video.lang == LangClass.Subtitled) {
                Text(
                    text = "Audio non italiano: i sottotitoli italiani compaiono solo se il video li offre (la lingua delle tracce non è verificabile in anticipo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 12.dp),
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
            Text("Scorri per il prossimo", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun PlayerArea(
    item: VideoItem,
    isCurrent: Boolean,
    captions: Boolean,
    speed: Float,
    error: Int?,
    onError: (Int) -> Unit,
    onOpenYouTube: () -> Unit,
    onHide: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            error != null -> Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Questo video non si può riprodurre qui (codice $error).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenYouTube, shape = CircleShape) {
                        Text("Apri su YouTube", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onHide) {
                        Text("Nascondi", color = Palette.Orange, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            isCurrent -> YouTubePlayer(
                videoId = item.id,
                modifier = Modifier.fillMaxSize(),
                captions = captions,
                speed = speed,
                onError = onError,
            )
            else -> {
                AsyncImage(item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(78.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) Palette.Orange else Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active) Palette.Dark else Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun languageLabel(lang: LangClass): String = when (lang) {
    LangClass.Italian -> "🇮🇹 Audio italiano"
    LangClass.ItalianGuess -> "🇮🇹 Probabile italiano"
    LangClass.Subtitled, LangClass.Foreign -> "Con sottotitoli"
}

private fun speedLabel(speed: Float): String =
    String.format(Locale.ITALIAN, "%.2f", speed).trimEnd('0').trimEnd(',') + "×"

// --------------------------------------------------------------------------- stati vuoti

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun NoKeyState(onOpenSettings: () -> Unit) {
    CenteredMessage {
        EmojiBubble(emoji = "🎬", size = 80, background = Color.White.copy(alpha = 0.12f))
        Text("Per vedere video veri serve la tua chiave YouTube", style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Text(
            "È gratuita, si crea in pochi minuti e resta cifrata sul tuo telefono: nessun altro la vede. Finché non c'è, qui non mostro video inventati.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onOpenSettings,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Orange, contentColor = Palette.Dark),
        ) { Text("Inserisci la chiave", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun FeedEmptyState(
    feed: VideoFeed,
    filtered: Boolean,
    onRetry: () -> Unit,
    onMore: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val status = feed.status
    CenteredMessage {
        if (feed.busy || status == FeedStatus.Loading || status == FeedStatus.Idle) {
            CircularProgressIndicator(color = Palette.Orange)
            Text("Cerco video in italiano per te…", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            return@CenteredMessage
        }
        if (status is FeedStatus.Failed) {
            EmojiBubble(emoji = if (status.error.kind == YtErrorKind.QuotaExceeded) "⏳" else "⚠️", size = 72, background = Color.White.copy(alpha = 0.12f))
            Text(status.error.userMessage, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.error.kind != YtErrorKind.QuotaExceeded) {
                    Button(
                        onClick = onRetry,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Orange, contentColor = Palette.Dark),
                    ) { Text("Riprova", style = MaterialTheme.typography.labelLarge) }
                }
                if (status.error.isKeyProblem) {
                    OutlinedButton(onClick = onOpenSettings, shape = CircleShape) {
                        Text("Controlla la chiave", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            return@CenteredMessage
        }
        EmojiBubble(emoji = "🔍", size = 72, background = Color.White.copy(alpha = 0.12f))
        Text(
            text = if (filtered) "Nessun video corrisponde ai filtri attuali." else "YouTube non ha restituito video adatti (in italiano e riproducibili qui).",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = if (filtered) onMore else onRetry,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Orange, contentColor = Palette.Dark),
        ) { Text(if (filtered) "Cerca altri video" else "Riprova", style = MaterialTheme.typography.labelLarge) }
    }
}

// --------------------------------------------------------------------------- dettagli

@Composable
private fun DetailsSheet(item: VideoItem, onOpenYouTube: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = listOf(item.channelTitle, Format.published(item.publishedAt)).filter { it.isNotBlank() }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val stats = listOfNotNull(
            Format.duration(item.durationSec),
            item.views?.let { "${Format.count(it)} visualizzazioni" },
            item.likes?.let { "${Format.count(it)} mi piace" },
        ).joinToString("  ·  ")
        Text(stats, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        Text(
            text = when (item.lang) {
                LangClass.Italian -> "Lingua: audio italiano, dichiarato dall'autore."
                LangClass.ItalianGuess -> "Lingua: l'autore non l'ha dichiarata; titolo e testo sembrano italiani (stima)."
                else -> "Lingua: audio non italiano. Ha una traccia di sottotitoli, ma YouTube non dice in che lingua."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.description.isNotBlank()) {
            Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        OutlinedButton(onClick = onOpenYouTube, shape = CircleShape) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Apri su YouTube", style = MaterialTheme.typography.labelLarge)
        }
    }
}

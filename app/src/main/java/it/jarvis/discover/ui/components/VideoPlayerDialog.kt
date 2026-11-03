package it.jarvis.discover.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.jarvis.discover.data.youtube.Format
import it.jarvis.discover.data.youtube.LangClass
import it.jarvis.discover.data.youtube.VideoItem

/** Riproduce un video salvato a tutto schermo, con lo stesso player del feed. */
@Composable
fun VideoPlayerDialog(item: VideoItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var error by remember(item.id) { mutableStateOf<Int?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF050E20)).safeDrawingPadding(),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Chiudi", tint = Color.White)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (error == null) {
                    YouTubePlayer(
                        videoId = item.id,
                        modifier = Modifier.fillMaxSize(),
                        captions = item.lang == LangClass.Subtitled,
                        onError = { error = it },
                    )
                } else {
                    Text(
                        "Questo video non si può riprodurre qui (codice $error).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOf(item.channelTitle, Format.published(item.publishedAt)).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
                OutlinedButton(onClick = { openUrl(context, item.watchUrl) }, shape = CircleShape) {
                    Text("Apri su YouTube", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

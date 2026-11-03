package it.jarvis.discover.ui.creators

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.jarvis.discover.data.creators.Creator
import it.jarvis.discover.data.youtube.Format

@Composable
fun CreatorAvatar(url: String, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url.ifBlank { null },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

fun creatorStats(c: Creator): String = listOfNotNull(
    c.handle,
    c.subscribers?.let { "${Format.count(it)} iscritti" },
    c.videoCount?.let { "$it video" },
).joinToString("  ·  ")

/** Scheda di un creator con pulsante "Segui". */
@Composable
fun CreatorCard(
    creator: Creator,
    following: Boolean,
    onOpen: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CreatorAvatar(creator.thumbnail, 52.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                creator.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                creatorStats(creator),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (following) {
            OutlinedButton(onClick = onToggleFollow, shape = CircleShape) {
                Text("Segui già", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(onClick = onToggleFollow, shape = CircleShape, modifier = Modifier.padding(start = 4.dp)) {
                Text("Segui", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

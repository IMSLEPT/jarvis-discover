package it.jarvis.discover.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.Mood
import it.jarvis.discover.data.Moods
import it.jarvis.discover.ui.components.BrandHeadline
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.Wordmark

/**
 * Schermata d'apertura: "Di cosa hai bisogno oggi?".
 * La scelta non è obbligatoria: si può entrare subito nel feed personalizzato.
 */
@Composable
fun MoodScreen(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Wordmark(modifier = Modifier.padding(bottom = 10.dp))
                BrandHeadline("Di cosa hai bisogno oggi?")
                Text(
                    text = "Scegli una o più modalità, oppure entra direttamente nel tuo feed.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            }

            val regular = Moods.filter { it.id != "per_te" }
            val forYou = Moods.first { it.id == "per_te" }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(regular, key = { it.id }) { mood ->
                    MoodCard(
                        mood = mood,
                        isSelected = mood.id in selected,
                        anySelected = selected.isNotEmpty(),
                        wide = false,
                        onToggle = { onToggle(mood.id) },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    MoodCard(
                        mood = forYou,
                        isSelected = forYou.id in selected,
                        anySelected = selected.isNotEmpty(),
                        wide = true,
                        onToggle = { onToggle(forYou.id) },
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = CircleShape,
                ) {
                    Text(
                        text = if (selected.isEmpty()) "Entra nel feed personalizzato" else "Continua",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = "Potrai cambiare modalità in qualsiasi momento.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MoodCard(
    mood: Mood,
    isSelected: Boolean,
    anySelected: Boolean,
    wide: Boolean,
    onToggle: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "moodScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.Transparent,
        label = "moodBorder",
    )
    val shape = MaterialTheme.shapes.large

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (wide) 96.dp else 152.dp)
            .scale(scale)
            .alpha(if (anySelected && !isSelected) 0.72f else 1f)
            .clip(shape)
            .background(Brush.linearGradient(mood.gradient))
            .border(BorderStroke(2.5.dp, borderColor), shape)
            .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EmojiBubble(emoji = mood.emoji, size = 52, background = Color.White.copy(alpha = 0.18f))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(mood.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        mood.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }
                SelectionDot(isSelected)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    EmojiBubble(emoji = mood.emoji, size = 48, background = Color.White.copy(alpha = 0.18f))
                    SelectionDot(isSelected)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(mood.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        mood.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionDot(isSelected: Boolean) {
    val fill by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.22f),
        label = "dotFill",
    )
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selezionato",
                tint = Color(0xFF041937),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

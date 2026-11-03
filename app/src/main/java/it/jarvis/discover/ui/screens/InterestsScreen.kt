@file:OptIn(ExperimentalLayoutApi::class)

package it.jarvis.discover.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.CatalogTopics
import it.jarvis.discover.data.InterestCategories
import it.jarvis.discover.data.InterestCategory
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SoftCard

/**
 * Scelta degli interessi. Si usa al primo avvio (onboarding) e, in seguito, per modificarli:
 * nessun limite al numero di argomenti, ricerca nel catalogo e possibilità di aggiungerne di propri.
 */
@Composable
fun InterestsScreen(
    onboarding: Boolean,
    selected: List<String>,
    customTopics: List<String>,
    onToggle: (String) -> Unit,
    onAddCustom: (String) -> Unit,
    onDone: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val trimmed = query.trim()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(scheme.secondaryContainer.copy(alpha = 0.6f), scheme.background, scheme.background),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (onboarding) "Cosa ti appassiona?" else "I tuoi interessi",
                    style = MaterialTheme.typography.displaySmall,
                    color = scheme.onBackground,
                )
                Text(
                    text = if (onboarding) {
                        "Scegli tutto ciò che vuoi: puoi cambiare idea in qualsiasi momento."
                    } else {
                        "Aggiungi o togli argomenti quando vuoi. Nessun limite."
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = scheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding),
                placeholder = { Text("Cerca o aggiungi un argomento") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancella ricerca")
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (trimmed.isEmpty()) {
                    if (customTopics.isNotEmpty()) {
                        item(key = "custom") {
                            SoftCard(contentPadding = PaddingValues(16.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "I TUOI ARGOMENTI",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.primary,
                                    )
                                    TopicChips(customTopics, selected, onToggle)
                                }
                            }
                        }
                    }
                    items(InterestCategories.size, key = { InterestCategories[it].id }) { index ->
                        CategoryPicker(
                            category = InterestCategories[index],
                            selected = selected,
                            onToggle = onToggle,
                            initiallyExpanded = index == 0,
                        )
                    }
                } else {
                    val results = (CatalogTopics + customTopics)
                        .filter { it.contains(trimmed, ignoreCase = true) }
                        .distinct()
                    val exactMatch = (CatalogTopics + customTopics).any { it.equals(trimmed, ignoreCase = true) }

                    if (results.isNotEmpty()) {
                        item(key = "results") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "${results.size} ${if (results.size == 1) "risultato" else "risultati"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                                TopicChips(results, selected, onToggle)
                            }
                        }
                    } else {
                        item(key = "none") {
                            Text(
                                text = "Nessun argomento corrisponde a “$trimmed”.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!exactMatch) {
                        item(key = "add") {
                            OutlinedButton(
                                onClick = {
                                    onAddCustom(trimmed)
                                    query = ""
                                    keyboard?.hide()
                                },
                                shape = CircleShape,
                                modifier = Modifier.height(48.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "  Aggiungi “$trimmed”",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenPadding, vertical = 12.dp)
                    .height(56.dp),
                shape = CircleShape,
            ) {
                Text(
                    text = when {
                        !onboarding -> "Fatto"
                        selected.isEmpty() -> "Continua senza scegliere"
                        else -> "Continua · ${selected.size} ${if (selected.size == 1) "scelto" else "scelti"}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    category: InterestCategory,
    selected: List<String>,
    onToggle: (String) -> Unit,
    initiallyExpanded: Boolean,
) {
    var expanded by rememberSaveable(category.id) { mutableStateOf(initiallyExpanded) }
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
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    TopicChips(category.topics, selected, onToggle)
                }
            }
        }
    }
}

@Composable
private fun TopicChips(topics: List<String>, selected: List<String>, onToggle: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        topics.forEach { topic ->
            val isSelected = topic in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(topic) },
                label = { Text(topic, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
                shape = CircleShape,
            )
        }
    }
}

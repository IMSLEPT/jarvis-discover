package it.jarvis.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.DiscoverViewModel
import it.jarvis.discover.data.ThemeMode
import it.jarvis.discover.data.youtube.KeyTest
import it.jarvis.discover.data.youtube.VideoFeed
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.openUrl

private const val URL_API_LIBRARY = "https://console.cloud.google.com/apis/library"
private const val URL_CREDENTIALS = "https://console.cloud.google.com/apis/credentials"

@Composable
fun SettingsSheet(vm: DiscoverViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Aspetto")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = vm.themeMode == mode,
                        onClick = { vm.updateThemeMode(mode) },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelLarge) },
                        shape = CircleShape,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Lingua dei contenuti")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Accetta video stranieri sottotitolati",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Se disattivato, vedrai solo video in italiano. Se attivo, compaiono anche video con audio straniero che hanno una traccia di sottotitoli (YouTube non dice in che lingua sia).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.acceptSubtitled, onCheckedChange = vm::updateAcceptSubtitled)
            }
        }

        YouTubeKeySection(vm.videos)

        BackupSection(vm.backup)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun YouTubeKeySection(feed: VideoFeed) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var replacing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(!feed.hasKey) }

    fun save() {
        val error = feed.saveKey(input)
        if (error == null) {
            input = ""
            visible = false
            replacing = false
        } else {
            inputError = error
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Video da YouTube")

        if (feed.hasKey && !replacing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    text = "Chiave salvata" + (feed.keyHint?.let { " (termina con $it)" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                "È cifrata con una chiave del telefono che non si può esportare. Non viene salvata nei backup né inviata ad altri che a YouTube, per leggere i video.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = feed::testKey, enabled = feed.keyTest != KeyTest.Running, shape = CircleShape) {
                    if (feed.keyTest == KeyTest.Running) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Testa la chiave", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { replacing = true }) { Text("Sostituisci", style = MaterialTheme.typography.labelLarge) }
                if (confirmRemove) {
                    TextButton(onClick = { feed.removeKey(); confirmRemove = false; showGuide = true }) {
                        Text("Sì, rimuovi", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(onClick = { confirmRemove = true }) {
                        Text("Rimuovi", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            when (val test = feed.keyTest) {
                KeyTest.Ok -> Text(
                    "✓ La chiave funziona.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                is KeyTest.Failed -> Text(
                    test.error.userMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }
        } else {
            Text(
                "Per mostrare video veri serve una chiave API gratuita di YouTube. Incollala qui: resta sul telefono, cifrata.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filterNot(Char::isWhitespace); inputError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Chiave API YouTube") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) save() }),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (visible) "Nascondi la chiave" else "Mostra la chiave",
                        )
                    }
                },
                isError = inputError != null,
                supportingText = { Text(inputError ?: "Di solito inizia con «AIza» e ha circa 39 caratteri.") },
                shape = MaterialTheme.shapes.medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::save, enabled = input.isNotBlank(), shape = CircleShape) {
                    Text("Salva la chiave", style = MaterialTheme.typography.labelLarge)
                }
                if (replacing) {
                    TextButton(onClick = { replacing = false; input = ""; inputError = null }) {
                        Text("Annulla", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (feed.hasKey) {
            Text(
                "Ricerche usate oggi: ${feed.searchesToday} su ${VideoFeed.SEARCH_LIMIT} (stima di questa app; si azzera a mezzanotte californiana, circa le 9:00 in Italia). Ogni aggiornamento del feed ne usa fino a 4.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = { showGuide = !showGuide }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Come ottenere la chiave gratuita", style = MaterialTheme.typography.labelLarge)
            Icon(if (showGuide) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        if (showGuide) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideStep(1, "Apri Google Cloud Console con il tuo account Google e crea un progetto (non serve la carta di credito).")
                GuideStep(2, "Nella libreria delle API cerca «YouTube Data API v3» e abilitala.")
                OutlinedButton(onClick = { openUrl(context, URL_API_LIBRARY) }, shape = CircleShape) {
                    Text("Apri la libreria API", style = MaterialTheme.typography.labelLarge)
                }
                GuideStep(3, "In «Credenziali» scegli «Crea credenziali» → «Chiave API».")
                OutlinedButton(onClick = { openUrl(context, URL_CREDENTIALS) }, shape = CircleShape) {
                    Text("Apri le credenziali", style = MaterialTheme.typography.labelLarge)
                }
                GuideStep(4, "Restringi la chiave: nelle limitazioni delle API consenti solo «YouTube Data API v3». Come limitazione dell'applicazione lascia «Nessuna» (le versioni di prova dell'app cambiano firma).")
                GuideStep(5, "Copia la chiave e incollala qui sopra. I nomi esatti dei pulsanti di Google possono cambiare.")
                Text(
                    "La quota gratuita è di 100 ricerche al giorno più 10.000 unità per il resto: per l'uso personale di solito basta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$number.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

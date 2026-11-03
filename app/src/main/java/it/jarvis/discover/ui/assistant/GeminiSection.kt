package it.jarvis.discover.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import it.jarvis.discover.data.assistant.AiKeyTest
import it.jarvis.discover.data.assistant.AssistantEngine
import it.jarvis.discover.ui.SectionLabel
import it.jarvis.discover.ui.components.openUrl

private const val URL_AI_STUDIO = "https://aistudio.google.com/app/apikey"

/** Impostazioni dell'IA opzionale (Gemini): chiave cifrata sul telefono, interruttore e informativa sui dati. */
@Composable
fun GeminiSection(engine: AssistantEngine) {
    val context = LocalContext.current
    val store = engine.store
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var replacing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun save() {
        val error = engine.saveKey(input)
        if (error == null) { input = ""; visible = false; replacing = false } else inputError = error
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Assistente con IA (opzionale)")
        Text(
            "L'assistente cerca già da solo video, libri, podcast e notizie veri. Con una chiave gratuita di Google AI Studio può anche rispondere a domande libere, " +
                "spiegarti un video (da titolo e descrizione, non dal parlato) e trasformare un tema in suggerimenti pratici. Senza chiave tutto il resto funziona.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (engine.hasKey && !replacing) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    "Chiave salvata" + (engine.keyHint?.let { " (termina con $it)" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                "È cifrata con una chiave del telefono che non si può esportare. Viene inclusa solo nel file di backup protetto dalla tua passphrase, se ne crei uno.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Usa l'IA", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = store.aiEnabled, onCheckedChange = store::changeEnabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = engine::testKey, enabled = engine.keyTest != AiKeyTest.Running, shape = CircleShape) {
                    if (engine.keyTest == AiKeyTest.Running) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Testa la chiave", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = { replacing = true }) { Text("Sostituisci", style = MaterialTheme.typography.labelLarge) }
                if (confirmRemove) {
                    TextButton(onClick = { engine.removeKey(); confirmRemove = false }) {
                        Text("Sì, rimuovi", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(onClick = { confirmRemove = true }) {
                        Text("Rimuovi", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            when (val test = engine.keyTest) {
                is AiKeyTest.Ok -> Text("✓ La chiave funziona. Modello in uso: ${test.model}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                is AiKeyTest.Failed -> Text(test.error.userMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            store.model?.let { model ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Modello: $model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (store.available.size > 1) {
                        TextButton(onClick = engine::nextModel) { Text("Cambia", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Consenso all'invio dei testi a Google", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (store.consent) "Dato. Puoi ritirarlo in qualsiasi momento." else "Te lo chiederò al primo utilizzo dell'IA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = store.consent, onCheckedChange = store::changeConsent)
            }
        } else {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filterNot(Char::isWhitespace); inputError = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Chiave API Gemini") },
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
                supportingText = { Text(inputError ?: "Di solito inizia con «AIza».") },
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
            OutlinedButton(onClick = { openUrl(context, URL_AI_STUDIO) }, shape = CircleShape) {
                Text("Apri Google AI Studio", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "In AI Studio accedi con il tuo account Google, scegli «Get API key» e crea una chiave: esiste un livello gratuito senza carta di credito, con limiti di richieste che Google può cambiare. I nomi dei pulsanti possono variare.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            "Privacy: con il livello gratuito, secondo le condizioni di Google, ciò che invii può essere usato per migliorare i suoi prodotti e letto da revisori umani. " +
                "L'app invia solo il testo delle richieste a Gemini, mai la cronologia, il diario o le chiavi, e non salva le conversazioni.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Le risposte dell'IA possono contenere errori: l'app le segnala e non le usa mai per inventare titoli, link o fonti.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

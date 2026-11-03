package it.jarvis.discover.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import it.jarvis.discover.data.backup.BackupCodec
import it.jarvis.discover.data.backup.BackupManager
import it.jarvis.discover.data.youtube.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val MAX_BACKUP_BYTES = 8 * 1024 * 1024

/**
 * Backup e ripristino: un unico file cifrato (AES-256 con passphrase scelta da te) che contiene
 * preferenze, salvati e, se vuoi, le chiavi API. Lo salvi dove preferisci (telefono, Drive...) e
 * lo ripristini dopo una reinstallazione senza dover reinserire le chiavi.
 */
@Composable
internal fun BackupSection(backup: BackupManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var includeKeys by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    var restoredDone by remember { mutableStateOf(false) }
    var lastBackup by remember { mutableStateOf(backup.lastBackupAt) }

    val passphraseOk = passphrase.length >= BackupCodec.MIN_PASSPHRASE

    fun report(text: String, error: Boolean) {
        message = text
        isError = error
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val chars = passphrase.toCharArray()
        scope.launch {
            busy = true
            try {
                val bytes = backup.create(chars, includeKeys)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("impossibile aprire il file")
                }
                lastBackup = backup.lastBackupAt
                report("Backup salvato. Conserva la passphrase: senza non si può aprire.", false)
            } catch (e: Exception) {
                report("Backup non riuscito: ${e.message ?: "errore sconosciuto"}.", true)
            } finally {
                chars.fill('\u0000')
                busy = false
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val chars = passphrase.toCharArray()
        scope.launch {
            busy = true
            try {
                val bytes = withContext(Dispatchers.IO) { readLimited(context, uri) }
                val count = backup.restore(bytes, chars)
                restoredDone = true
                report("Ripristinati $count elementi. Riavvia l'app per vederli.", false)
            } catch (e: BackupCodec.WrongPassphraseException) {
                report("Passphrase errata oppure file danneggiato.", true)
            } catch (e: BackupCodec.InvalidFileException) {
                report("Questo file non è un backup di Discover.", true)
            } catch (e: Exception) {
                report("Ripristino non riuscito: ${e.message ?: "errore sconosciuto"}.", true)
            } finally {
                chars.fill('\u0000')
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Backup e ripristino")
        Text(
            "Salva in un file cifrato le tue impostazioni, i video salvati e, se vuoi, la chiave di YouTube: dopo una reinstallazione la ripristini senza reinserirla. Il file lo conservi tu, dove preferisci.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Passphrase del backup (almeno ${BackupCodec.MIN_PASSPHRASE} caratteri)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text("Non viene salvata e non si può recuperare: se la dimentichi il backup è perso.") },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Includi le chiavi API",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(checked = includeKeys, onCheckedChange = { includeKeys = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { createLauncher.launch("discover-backup-${LocalDate.now()}.dscbk") },
                enabled = passphraseOk && !busy,
                shape = CircleShape,
            ) { Text("Crea backup") }
            OutlinedButton(
                onClick = { confirmRestore = true },
                enabled = passphraseOk && !busy,
                shape = CircleShape,
            ) { Text("Ripristina") }
            if (busy) CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp), strokeWidth = 2.dp)
        }
        if (lastBackup > 0L) {
            Text(
                "Ultimo backup creato: ${Format.dateTime(lastBackup)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (restoredDone) {
            Button(onClick = { restartApp(context) }, shape = CircleShape) { Text("Riavvia ora") }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Ripristinare il backup?") },
            text = {
                Text("Le impostazioni, i salvati e le chiavi presenti nel backup sostituiranno quelli attuali su questo telefono. Scegli il file e usa la passphrase inserita qui sopra.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    openLauncher.launch(arrayOf("*/*"))
                }) { Text("Scegli il file") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Annulla") } },
        )
    }
}

private fun readLimited(context: Context, uri: Uri): ByteArray {
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "impossibile aprire il file" }
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BACKUP_BYTES) throw BackupCodec.InvalidFileException()
        }
        return out.toByteArray()
    }
}

private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
    Process.killProcess(Process.myPid())
}

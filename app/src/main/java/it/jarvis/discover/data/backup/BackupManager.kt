package it.jarvis.discover.data.backup

import android.content.Context
import it.jarvis.discover.data.youtube.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Quali dati entrano nel backup. Le cache (video, notizie) e i contatori di quota restano fuori. */
object BackupSpec {
    /** File di preferenze → chiavi escluse. */
    val files: Map<String, Set<String>> = mapOf(
        "discover_prefs" to setOf("last_backup_at"),
        "discover_videos" to setOf("cache", "quota_day", "quota_searches"),
        "discover_creators" to setOf("last_seen"),
        "discover_library" to emptySet(),
        "discover_profile" to emptySet(),
        "discover_wellbeing" to emptySet(),
        "discover_assistant" to emptySet(),
    )

    /** Nomi dei segreti nell'archivio cifrato. */
    val secrets: List<String> = listOf(SecretNames.YOUTUBE, SecretNames.GEMINI)
}

object SecretNames {
    const val YOUTUBE = "youtube_api_key"
    const val GEMINI = "gemini_api_key"
}

class BackupManager(context: Context, private val secure: SecureStore) {

    private val appContext = context.applicationContext
    private val meta = appContext.getSharedPreferences("discover_prefs", Context.MODE_PRIVATE)

    val lastBackupAt: Long get() = meta.getLong(LAST_KEY, 0L)

    /** Crea il file di backup cifrato. Operazione lenta (derivazione della chiave): va fatta fuori dal thread principale. */
    suspend fun create(passphrase: CharArray, includeKeys: Boolean): ByteArray = withContext(Dispatchers.IO) {
        val prefs = LinkedHashMap<String, Map<String, Any>>()
        BackupSpec.files.forEach { (file, excluded) ->
            val all = appContext.getSharedPreferences(file, Context.MODE_PRIVATE).all
            prefs[file] = all.filterKeys { it !in excluded }.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
        }
        val secrets = LinkedHashMap<String, String>()
        if (includeKeys) BackupSpec.secrets.forEach { name -> secure.get(name)?.let { secrets[name] = it } }
        val now = System.currentTimeMillis()
        val bytes = BackupCodec.encrypt(
            BackupPayload.toJson(BackupContent(now, prefs, secrets)).toByteArray(Charsets.UTF_8),
            passphrase,
        )
        meta.edit().putLong(LAST_KEY, now).apply()
        bytes
    }

    /**
     * Decifra e applica il backup sostituendo i dati attuali. Dopo il successo l'app va riavviata
     * perché gli schermi leggano i nuovi dati.
     * @throws BackupCodec.WrongPassphraseException passphrase errata o file alterato
     * @throws BackupCodec.InvalidFileException il file non è un backup di Discover
     */
    suspend fun restore(file: ByteArray, passphrase: CharArray): Int = withContext(Dispatchers.IO) {
        val content = BackupPayload.fromJson(String(BackupCodec.decrypt(file, passphrase), Charsets.UTF_8))
        var restored = 0
        content.prefs.forEach { (name, entries) ->
            val excluded = BackupSpec.files[name] ?: return@forEach // file sconosciuto: ignorato
            val prefs = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            prefs.all.keys.filter { it !in excluded }.forEach { edit.remove(it) }
            entries.forEach { (key, value) ->
                if (key in excluded) return@forEach
                when (value) {
                    is String -> edit.putString(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Float -> edit.putFloat(key, value)
                    is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
                restored++
            }
            edit.commit()
        }
        content.secrets.forEach { (name, value) ->
            if (name in BackupSpec.secrets) {
                secure.put(name, value)
                restored++
            }
        }
        restored
    }

    private companion object {
        const val LAST_KEY = "last_backup_at"
    }
}

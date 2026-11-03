package it.jarvis.discover.data

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.jarvis.discover.data.assistant.AssistantEngine
import it.jarvis.discover.data.backup.BackupManager
import it.jarvis.discover.data.content.LibraryStore
import it.jarvis.discover.data.content.NewsRepository
import it.jarvis.discover.data.content.TodayBook
import it.jarvis.discover.data.notes.NotesStore
import it.jarvis.discover.data.wellbeing.ActivityKind
import it.jarvis.discover.data.wellbeing.BreathingPlayer
import it.jarvis.discover.data.wellbeing.NoisePlayer
import it.jarvis.discover.data.wellbeing.SessionPlayer
import it.jarvis.discover.data.wellbeing.TtsSpeaker
import it.jarvis.discover.data.wellbeing.WellbeingStore
import it.jarvis.discover.playback.AudioController
import it.jarvis.discover.data.youtube.SecureStore
import it.jarvis.discover.data.youtube.VideoFeed
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode(val label: String) {
    System("Sistema"),
    Light("Chiaro"),
    Dark("Scuro");

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: System
    }
}

/** Una voce del diario della gratitudine. */
data class GratitudeEntry(val epochMillis: Long, val items: List<String>)

/**
 * Stato condiviso dell'app. Tutto ciò che l'utente imposta o scrive resta sul telefono
 * (SharedPreferences): nessun dato personale lascia il dispositivo.
 */
class DiscoverViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("discover_prefs", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(ThemeMode.fromName(prefs.getString(KEY_THEME, null)))
        private set

    /** Tempo che l'utente vuole dedicare all'app: 2, 5, 10, 20 minuti oppure 0 = libero. */
    var sessionMinutes by mutableIntStateOf(prefs.getInt(KEY_MINUTES, 5))
        private set

    /** Se vero, l'app accetterà anche video stranieri con sottotitoli italiani. */
    var acceptSubtitled by mutableStateOf(prefs.getBoolean(KEY_SUBTITLED, true))
        private set

    /** Modalità scelte all'apertura (vuoto = "Per te"). Valide per la sessione corrente. */
    var modes by mutableStateOf(emptyList<String>())
        private set

    val gratitude = mutableStateListOf<GratitudeEntry>()

    /** Video reali da YouTube: chiave cifrata, feed, salvati e preferenze. */
    val videos = VideoFeed(app, viewModelScope)

    /** Backup cifrato con passphrase: preferenze e chiavi API in un file che scegli tu dove salvare. */
    val backup = BackupManager(app, SecureStore(app))

    /** Notizie reali dai feed RSS, scaffali dei libri, podcast seguiti e lettore audio. */
    val news = NewsRepository(app, viewModelScope)
    val library = LibraryStore(app)
    val audio = AudioController(app, viewModelScope, library)
    val todayBook = TodayBook(app, viewModelScope)

    /** Benessere: diario del tempo dedicato a sé, sessioni guidate con voce, respirazione e rumori rilassanti. */
    val wellbeing = WellbeingStore.from(app)
    val sessions = SessionPlayer(viewModelScope, TtsSpeaker(app), wellbeing)
    val breathing = BreathingPlayer(viewModelScope, wellbeing)
    val noise = NoisePlayer(viewModelScope, wellbeing)

    /** Quaderno personale: note e cose imparate. */
    val notes = NotesStore.from(app)

    /** Assistente: regole in italiano sulle fonti reali dell'app e, se l'utente lo vuole, Gemini con la sua chiave. */
    val assistant = AssistantEngine(
        context = app,
        scope = viewModelScope,
        feed = videos,
        news = news,
        audio = audio,
        interests = { interests },
        acceptSubtitled = { acceptSubtitled },
    )

    /** Vero dopo che l'utente ha completato (o saltato) la scelta iniziale degli interessi. */
    var onboardingDone by mutableStateOf(prefs.getBoolean(KEY_ONBOARDED, false))
        private set

    /** Argomenti scelti dall'utente, del catalogo o personalizzati. Nessun limite di numero. */
    var interests by mutableStateOf(loadList(KEY_INTERESTS))
        private set

    /** Argomenti inventati dall'utente: esistono finché restano selezionati. */
    var customTopics by mutableStateOf(loadList(KEY_CUSTOM))
        private set

    init {
        loadGratitude()
    }

    fun completeOnboarding() {
        onboardingDone = true
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun toggleInterest(topic: String) {
        if (topic in interests) {
            interests = interests - topic
            if (topic in customTopics) {
                customTopics = customTopics - topic
                saveList(KEY_CUSTOM, customTopics)
            }
        } else {
            interests = interests + topic
        }
        saveList(KEY_INTERESTS, interests)
    }

    /** Aggiunge e seleziona un argomento libero; se esiste già (anche nel catalogo) seleziona quello. */
    fun addCustomTopic(raw: String) {
        val name = raw.trim().replace(Regex("\\s+"), " ").take(MAX_TOPIC_LENGTH)
        if (name.isEmpty()) return
        val existing = (CatalogTopics + customTopics).firstOrNull { it.equals(name, ignoreCase = true) }
        val topic = existing ?: name
        if (existing == null) {
            customTopics = customTopics + topic
            saveList(KEY_CUSTOM, customTopics)
        }
        if (topic !in interests) {
            interests = interests + topic
            saveList(KEY_INTERESTS, interests)
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun updateSessionMinutes(minutes: Int) {
        sessionMinutes = minutes
        prefs.edit().putInt(KEY_MINUTES, minutes).apply()
    }

    fun updateAcceptSubtitled(value: Boolean) {
        acceptSubtitled = value
        prefs.edit().putBoolean(KEY_SUBTITLED, value).apply()
    }

    fun toggleMode(id: String) {
        modes = if (id in modes) modes - id else modes + id
    }

    fun addGratitude(items: List<String>) {
        val clean = items.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        gratitude.add(0, GratitudeEntry(System.currentTimeMillis(), clean))
        persistGratitude()
        wellbeing.log(ActivityKind.Gratitude, "Gratitudine", 0)
    }

    fun deleteGratitude(entry: GratitudeEntry) {
        gratitude.remove(entry)
        persistGratitude()
    }

    private fun loadGratitude() {
        val raw = prefs.getString(KEY_GRATITUDE, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val items = obj.getJSONArray("items")
                gratitude.add(
                    GratitudeEntry(
                        epochMillis = obj.getLong("t"),
                        items = List(items.length()) { items.getString(it) },
                    ),
                )
            }
        }
    }

    private fun loadList(key: String): List<String> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun saveList(key: String, values: List<String>) {
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }

    private fun persistGratitude() {
        val array = JSONArray()
        gratitude.forEach { entry ->
            array.put(
                JSONObject()
                    .put("t", entry.epochMillis)
                    .put("items", JSONArray(entry.items)),
            )
        }
        prefs.edit().putString(KEY_GRATITUDE, array.toString()).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_MINUTES = "session_minutes"
        const val KEY_SUBTITLED = "accept_subtitled"
        const val KEY_GRATITUDE = "gratitude_entries"
        const val KEY_ONBOARDED = "onboarding_done"
        const val KEY_INTERESTS = "interests"
        const val KEY_CUSTOM = "custom_topics"
        const val MAX_TOPIC_LENGTH = 60
    }

    override fun onCleared() {
        audio.release()
        sessions.release()
        breathing.release()
        noise.release()
        super.onCleared()
    }
}

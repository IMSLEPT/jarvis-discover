package it.jarvis.discover.data

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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

    init {
        loadGratitude()
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
    }
}

package it.jarvis.discover.data.assistant

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Impostazioni dell'assistente (non la chiave, che sta nell'archivio cifrato). Nessun testo delle conversazioni viene salvato. */
class AssistantStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("discover_assistant", Context.MODE_PRIVATE)

    /** L'utente vuole usare l'IA (se ha una chiave). Spegnendolo l'assistente lavora solo con le regole e le fonti dell'app. */
    var aiEnabled by mutableStateOf(prefs.getBoolean(K_ENABLED, true)); private set

    /** L'utente ha letto e accettato che il testo delle richieste venga inviato a Google. */
    var consent by mutableStateOf(prefs.getBoolean(K_CONSENT, false)); private set

    /** Modello scelto in automatico (o dall'utente) per le risposte. */
    var model by mutableStateOf(prefs.getString(K_MODEL, null)); private set

    /** Modelli trovati nell'ultimo test della chiave, per poter cambiare. */
    var available by mutableStateOf(prefs.getString(K_AVAILABLE, "").orEmpty().split(',').filter { it.isNotBlank() }); private set

    fun changeEnabled(on: Boolean) { aiEnabled = on; prefs.edit().putBoolean(K_ENABLED, on).apply() }
    fun changeConsent(on: Boolean) { consent = on; prefs.edit().putBoolean(K_CONSENT, on).apply() }
    fun changeModel(name: String?) {
        model = name
        prefs.edit().apply { if (name == null) remove(K_MODEL) else putString(K_MODEL, name) }.apply()
    }
    fun changeAvailable(models: List<String>) {
        available = models
        prefs.edit().putString(K_AVAILABLE, models.joinToString(",")).apply()
    }

    private companion object {
        const val K_ENABLED = "ai_enabled"
        const val K_CONSENT = "ai_consent"
        const val K_MODEL = "model"
        const val K_AVAILABLE = "available"
    }
}

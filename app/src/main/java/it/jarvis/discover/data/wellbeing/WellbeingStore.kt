package it.jarvis.discover.data.wellbeing

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import it.jarvis.discover.data.today.DailyContent
import org.json.JSONArray
import org.json.JSONObject

enum class ActivityKind(val label: String) {
    Meditation("Meditazione"), Mindfulness("Mindfulness"), Evening("Sera e sonno"), Emotions("Emozioni"),
    Stress("Stress"), Breathing("Respirazione"), Sound("Audio rilassanti"), Gratitude("Gratitudine"),
}

/** Un'attività di benessere fatta: giorno (giorni dall'epoca), tipo, titolo e secondi dedicati. */
data class Activity(val epochDay: Long, val kind: ActivityKind, val title: String, val seconds: Int)

data class DayTotal(val epochDay: Long, val seconds: Int, val count: Int)

/**
 * Diario delle attività di benessere, solo su questo telefono. Serve a mostrarti con calma quanto tempo hai
 * dedicato a te: non ci sono obiettivi da rispettare né serie da non interrompere.
 */
class WellbeingStore(
    initialLog: String?,
    initialPath: String?,
    private val today: () -> Long = DailyContent::today,
    private val persist: (log: String, path: String) -> Unit,
) {
    val entries = mutableStateListOf<Activity>().apply { addAll(decode(initialLog)) }
    /** Passaggi del percorso antistress già fatti (id della sessione o dello schema di respirazione). */
    val pathDone = mutableStateListOf<String>().apply { addAll(decodeStrings(initialPath)) }

    /** Registra un'attività. Con [refId] di un passaggio del percorso antistress, lo segna come fatto. */
    fun log(kind: ActivityKind, title: String, seconds: Int, refId: String? = null) {
        entries += Activity(today(), kind, title.take(80), seconds.coerceAtLeast(0))
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        if (refId != null && refId in StressPath.ids && refId !in pathDone) pathDone += refId
        save()
    }

    fun resetPath() {
        pathDone.clear()
        save()
    }

    fun clearAll() {
        entries.clear()
        pathDone.clear()
        save()
    }

    fun last7Days(): List<DayTotal> = lastDays(entries, today(), 7)
    fun weekMinutes(): Int = last7Days().sumOf { it.seconds } / 60
    fun activeDaysLast30(): Int = activeDays(entries, today(), 30)

    private fun save() = persist(encode(entries), JSONArray(pathDone.toList()).toString())

    companion object {
        private const val K_LOG = "log"
        private const val K_PATH = "path_done"
        const val MAX_ENTRIES = 800

        /** Versione che salva nelle preferenze del telefono ("discover_wellbeing"). */
        fun from(context: Context): WellbeingStore {
            val prefs = context.applicationContext.getSharedPreferences("discover_wellbeing", Context.MODE_PRIVATE)
            return WellbeingStore(prefs.getString(K_LOG, null), prefs.getString(K_PATH, null)) { log, path ->
                prefs.edit().putString(K_LOG, log).putString(K_PATH, path).apply()
            }
        }

        /** Totali degli ultimi [n] giorni (oggi compreso), dal più vecchio al più recente. */
        fun lastDays(entries: List<Activity>, today: Long, n: Int): List<DayTotal> =
            (n - 1 downTo 0).map { back ->
                val day = today - back
                val of = entries.filter { it.epochDay == day }
                DayTotal(day, of.sumOf { it.seconds }, of.size)
            }

        /** In quanti degli ultimi [window] giorni c'è stata almeno un'attività. */
        fun activeDays(entries: List<Activity>, today: Long, window: Int): Int =
            entries.filter { it.epochDay in (today - window + 1)..today }.map { it.epochDay }.distinct().size

        fun encode(list: List<Activity>): String {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("d", it.epochDay).put("k", it.kind.name).put("t", it.title).put("s", it.seconds)) }
            return arr.toString()
        }

        fun decode(raw: String?): List<Activity> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val kind = ActivityKind.entries.firstOrNull { it.name == o.optString("k") } ?: return@mapNotNull null
                    Activity(o.optLong("d"), kind, o.optString("t"), o.optInt("s"))
                }
            }.getOrDefault(emptyList())
        }

        private fun decodeStrings(raw: String?): List<String> = runCatching {
            val arr = JSONArray(raw ?: return emptyList())
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }
}

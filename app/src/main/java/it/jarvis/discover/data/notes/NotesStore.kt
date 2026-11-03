package it.jarvis.discover.data.notes

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

enum class NoteKind(val label: String) {
    Note("Nota"),
    Learned("Ho imparato"),
}

data class Note(val id: Long, val kind: NoteKind, val text: String, val ref: String?, val at: Long)

/** Quaderno personale: note libere e «cose imparate». Resta sul telefono e rientra nel backup. */
class NotesStore(
    initial: String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val persist: (String) -> Unit,
) {
    val notes = mutableStateListOf<Note>().apply { addAll(decode(initial)) }

    /** Aggiunge una nota in cima. Falso se il testo è vuoto. */
    fun add(kind: NoteKind, text: String, ref: String? = null): Boolean {
        val clean = text.trim().take(MAX_TEXT)
        if (clean.isEmpty()) return false
        val id = maxOf(now(), (notes.maxOfOrNull { it.id } ?: 0L) + 1)
        notes.add(0, Note(id, kind, clean, ref?.trim()?.take(MAX_REF)?.ifBlank { null }, now()))
        while (notes.size > MAX_NOTES) notes.removeAt(notes.size - 1)
        save()
        return true
    }

    fun update(id: Long, text: String): Boolean {
        val clean = text.trim().take(MAX_TEXT)
        val i = notes.indexOfFirst { it.id == id }
        if (clean.isEmpty() || i < 0) return false
        notes[i] = notes[i].copy(text = clean)
        save()
        return true
    }

    fun delete(id: Long) {
        if (notes.removeAll { it.id == id }) save()
    }

    fun clearAll() {
        notes.clear()
        save()
    }

    /** Note scritte negli ultimi [days] giorni. */
    fun recent(days: Int = 7, kind: NoteKind? = null): List<Note> {
        val from = now() - days * 86_400_000L
        return notes.filter { it.at >= from && (kind == null || it.kind == kind) }
    }

    private fun save() = persist(encode(notes))

    companion object {
        const val MAX_TEXT = 2000
        const val MAX_REF = 120
        const val MAX_NOTES = 500

        fun encode(list: List<Note>): String {
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("id", it.id).put("k", it.kind.name).put("t", it.text).put("r", it.ref ?: "").put("at", it.at))
            }
            return arr.toString()
        }

        fun decode(raw: String?): List<Note> = runCatching {
            val arr = JSONArray(raw ?: return emptyList())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString("t").trim()
                if (text.isEmpty()) return@mapNotNull null
                Note(
                    id = o.optLong("id"),
                    kind = NoteKind.entries.firstOrNull { it.name == o.optString("k") } ?: NoteKind.Note,
                    text = text,
                    ref = o.optString("r").ifBlank { null },
                    at = o.optLong("at"),
                )
            }
        }.getOrDefault(emptyList())

        fun from(context: Context): NotesStore {
            val prefs = context.applicationContext.getSharedPreferences("discover_notes", Context.MODE_PRIVATE)
            return NotesStore(prefs.getString("data", null)) { prefs.edit().putString("data", it).apply() }
        }
    }
}

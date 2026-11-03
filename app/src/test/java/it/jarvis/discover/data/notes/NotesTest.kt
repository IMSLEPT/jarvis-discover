package it.jarvis.discover.data.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    private var clock = 1_800_000_000_000L
    private fun store(initial: String? = null, saved: MutableList<String> = mutableListOf()) =
        NotesStore(initial, now = { clock }, persist = { saved += it })

    @Test
    fun addTrimsRejectsEmptyAndKeepsNewestFirst() {
        val s = store()
        assertFalse(s.add(NoteKind.Note, "   "))
        assertTrue(s.add(NoteKind.Note, "  prima  "))
        clock += 1000
        assertTrue(s.add(NoteKind.Learned, "seconda", "Un libro"))
        assertEquals(listOf("seconda", "prima"), s.notes.map { it.text })
        assertEquals("Un libro", s.notes.first().ref)
        assertNull(s.notes.last().ref)
        assertEquals(2, s.notes.map { it.id }.toSet().size)
    }

    @Test
    fun idsStayUniqueEvenWithTheSameClock() {
        val s = store()
        repeat(5) { s.add(NoteKind.Note, "n$it") }
        assertEquals(5, s.notes.map { it.id }.toSet().size)
    }

    @Test
    fun updateDeleteAndClear() {
        val s = store()
        s.add(NoteKind.Note, "uno")
        val id = s.notes.first().id
        assertTrue(s.update(id, " modificata "))
        assertEquals("modificata", s.notes.first().text)
        assertFalse(s.update(id, ""))
        assertFalse(s.update(-5, "x"))
        s.delete(id)
        assertTrue(s.notes.isEmpty())
        s.add(NoteKind.Note, "due"); s.clearAll()
        assertTrue(s.notes.isEmpty())
    }

    @Test
    fun persistenceRoundTripAndCorruptData() {
        val saved = mutableListOf<String>()
        val s = store(saved = saved)
        s.add(NoteKind.Learned, "ho imparato", "Video")
        val back = store(initial = saved.last())
        assertEquals(s.notes, back.notes)
        assertTrue(store(initial = "rotto").notes.isEmpty())
        assertTrue(store(initial = null).notes.isEmpty())
    }

    @Test
    fun recentFiltersByAgeAndKind() {
        val s = store()
        s.add(NoteKind.Learned, "vecchia")
        clock += 10 * 86_400_000L
        s.add(NoteKind.Learned, "nuova")
        s.add(NoteKind.Note, "altra")
        assertEquals(setOf("nuova", "altra"), s.recent(7).map { it.text }.toSet())
        assertEquals(listOf("nuova"), s.recent(7, NoteKind.Learned).map { it.text })
    }

    @Test
    fun noteCountIsCapped() {
        val s = store()
        repeat(NotesStore.MAX_NOTES + 10) { s.add(NoteKind.Note, "n$it") }
        assertEquals(NotesStore.MAX_NOTES, s.notes.size)
        assertEquals("n${NotesStore.MAX_NOTES + 9}", s.notes.first().text)
    }
}

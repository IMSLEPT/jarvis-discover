package it.jarvis.discover.data.today

import it.jarvis.discover.data.content.Book
import it.jarvis.discover.data.content.TodayBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTest {
    @Test
    fun listsAreFilledWithDistinctEntries() {
        listOf(DailyContent.thoughts, DailyContent.questions, DailyContent.relationTips, DailyContent.ideas).forEach { l ->
            assertTrue(l.size >= 14)
            assertEquals(l.size, l.toSet().size)
            assertTrue(l.all { it.isNotBlank() && it.length < 200 })
        }
    }

    @Test
    fun pickIsStableWithinDayAndChangesNextDay() {
        val l = DailyContent.thoughts
        assertEquals(DailyContent.pick(l, 20000), DailyContent.pick(l, 20000))
        assertNotEquals(DailyContent.pick(l, 20000), DailyContent.pick(l, 20001))
        // ciclo completo senza ripetizioni
        assertEquals(l.size, (0 until l.size).map { DailyContent.pick(l, 20000L + it) }.toSet().size)
        // negativi non devono andare fuori indice
        DailyContent.pick(l, -5, salt = 3)
    }

    private fun book(key: String, it: Boolean, cover: Long?) =
        Book(key, key, emptyList(), null, cover, emptyList(), if (it) listOf("ita") else listOf("eng"), 1, emptyList(), false)

    @Test
    fun bookPickPrefersItalianWithCover() {
        val books = listOf(book("/works/1", false, 1), book("/works/2", true, null), book("/works/3", true, 9))
        assertEquals("/works/3", TodayBook.pickBook(books, 100)?.key)
        assertEquals("/works/2", TodayBook.pickBook(books.filter { it.key != "/works/3" }, 100)?.key)
        assertNull(TodayBook.pickBook(listOf(book("/works/1", false, 1)), 100))
    }

    @Test
    fun pathFollowsInterestsOtherwiseAnyPath() {
        val p = TodayBook.pathFor(listOf("Neuroscienze"), 7)
        assertEquals("psico", p.id)
        val any = TodayBook.pathFor(emptyList(), 7)
        assertTrue(any.id.isNotBlank())
    }
}

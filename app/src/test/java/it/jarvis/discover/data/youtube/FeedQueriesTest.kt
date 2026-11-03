package it.jarvis.discover.data.youtube

import it.jarvis.discover.data.youtube.TestData.item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedQueriesTest {

    @Test
    fun withoutChoicesUsesDefaults() {
        val c = FeedQueries.candidates(emptyList(), emptyList())
        assertEquals(FeedQueries.Defaults, c.map { it.text })
    }

    @Test
    fun perTeUsesInterestsOnly() {
        val c = FeedQueries.candidates(listOf("per_te"), listOf("Filosofia", "Astronomia"))
        assertEquals(listOf("Filosofia", "Astronomia"), c.map { it.text })
        assertEquals(listOf("Filosofia", "Astronomia"), c.map { it.topic })
    }

    @Test
    fun moodsAndInterestsAreAlternated() {
        val c = FeedQueries.candidates(listOf("rilassami"), listOf("Filosofia", "Astronomia"))
        assertEquals(
            listOf("meditazione guidata", "Filosofia", "mindfulness", "Astronomia", "respirazione e rilassamento"),
            c.map { it.text },
        )
        assertEquals("Rilassami", c.first().topic)
    }

    @Test
    fun duplicatesAreRemovedCaseInsensitively() {
        val c = FeedQueries.candidates(emptyList(), listOf("Mindfulness", "mindfulness"))
        assertEquals(1, c.size)
    }

    @Test
    fun boostedTopicsComeFirstAndOrderIsOtherwiseStable() {
        val c = FeedQueries.candidates(emptyList(), listOf("A", "B", "C"), boosts = mapOf("C" to 2, "B" to 1))
        assertEquals(listOf("C", "B", "A"), c.map { it.text })
    }

    @Test
    fun everyMoodHasSeeds() {
        FeedQueries.MoodSeeds.forEach { (id, seeds) -> assertTrue("$id senza ricerche", seeds.isNotEmpty()) }
        assertEquals(8, FeedQueries.MoodSeeds.size)
    }

    @Test
    fun windowWrapsAround() {
        val list = listOf("a", "b", "c", "d", "e").map { FeedQuery(it, it) }
        assertEquals(listOf("a", "b", "c", "d"), FeedQueries.window(list, 0).map { it.text })
        assertEquals(listOf("d", "e", "a", "b"), FeedQueries.window(list, 3).map { it.text })
        assertEquals(listOf("c", "d", "e", "a"), FeedQueries.window(list, 7).map { it.text })
        assertEquals(listOf("a", "b"), FeedQueries.window(list.take(2), 0).map { it.text })
        assertTrue(FeedQueries.window(emptyList(), 0).isEmpty())
        assertEquals(2, FeedQueries.window(list, 0, size = 2).size)
    }

    @Test
    fun signatureChangesWithQueries() {
        val a = FeedQueries.candidates(emptyList(), listOf("Filosofia"))
        val b = FeedQueries.candidates(emptyList(), listOf("Astronomia"))
        assertEquals(FeedQueries.signature(a), FeedQueries.signature(a))
        assertNotEquals(FeedQueries.signature(a), FeedQueries.signature(b))
    }

    @Test
    fun interleaveAlternatesAndDeduplicates() {
        val one = listOf(item("a1"), item("a2"), item("dup"))
        val two = listOf(item("b1"), item("dup"), item("b2"))
        val out = FeedQueries.interleave(listOf(one, two)).map { it.id }
        assertEquals(listOf("a1", "b1", "a2", "dup", "b2"), out)
    }

    @Test
    fun interleaveCapsVideosPerChannel() {
        val list = (1..6).map { item("v$it", channelId = "SAME") } + item("other", channelId = "UC2")
        val out = FeedQueries.interleave(listOf(list), maxPerChannel = 3).map { it.id }
        assertEquals(listOf("v1", "v2", "v3", "other"), out)
    }
}

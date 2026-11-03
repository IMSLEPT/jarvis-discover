package it.jarvis.discover.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FormatTest {

    @Test
    fun duration() {
        assertEquals("0:59", Format.duration(59))
        assertEquals("15:33", Format.duration(933))
        assertEquals("1:02:03", Format.duration(3723))
    }

    @Test
    fun compactCounts() {
        assertEquals("999", Format.count(999))
        assertEquals("1,5 mila", Format.count(1_500))
        assertEquals("12 mila", Format.count(12_300))
        assertEquals("1,3 mln", Format.count(1_250_000))
    }

    @Test
    fun publishedDate() {
        val text = Format.published("2024-03-12T10:00:00Z")
        assertTrue(text, text.startsWith("12 ") && text.endsWith("2024"))
        assertEquals("", Format.published("boh"))
    }

    @Test
    fun quotaDayFollowsPacificTime() {
        // A novembre la California è a UTC-8: l'1 novembre alle 07:00 UTC è ancora il 31 ottobre alle 23:00.
        fun day(iso: String) = Format.pacificDay(Instant.parse(iso).toEpochMilli())
        assertEquals("2026-11-02", day("2026-11-03T07:00:00Z"))
        assertEquals("2026-11-03", day("2026-11-03T08:00:00Z"))
        // In estate (UTC-7) il cambio giorno è alle 07:00 UTC.
        assertEquals("2026-07-14", day("2026-07-15T06:59:00Z"))
        assertEquals("2026-07-15", day("2026-07-15T07:00:00Z"))
    }
}

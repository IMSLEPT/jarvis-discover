package it.jarvis.discover.data.youtube

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

object Format {

    /** 933 → "15:33", 3723 → "1:02:03". */
    fun duration(sec: Int): String {
        val h = sec / 3600
        val m = sec % 3600 / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ITALIAN, h, m, s) else "%d:%02d".format(Locale.ITALIAN, m, s)
    }

    /** 1_250_000 → "1,3 mln", 12_300 → "12 mila", 950 → "950". */
    fun count(n: Long): String = when {
        n >= 1_000_000 -> "%.1f mln".format(Locale.ITALIAN, n / 1_000_000.0)
        n >= 10_000 -> "${n / 1000} mila"
        n >= 1_000 -> "%.1f mila".format(Locale.ITALIAN, n / 1000.0)
        else -> n.toString()
    }

    /** "2024-03-12T10:00:00Z" → "12 mar 2024"; stringa vuota se non leggibile. */
    fun published(iso: String): String = runCatching {
        LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
    }.getOrDefault("")

    /** Giorno (aaaa-mm-gg) nel fuso del Pacifico: è lì che YouTube azzera la quota. */
    fun pacificDay(epochMillis: Long): String {
        val zone = TimeZone.getTimeZone("America/Los_Angeles").toZoneId()
        return java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()
    }
}

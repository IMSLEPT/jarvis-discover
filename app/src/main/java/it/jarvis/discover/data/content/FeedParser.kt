package it.jarvis.discover.data.content

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/** Una voce di un feed RSS/Atom (articolo di notizie oppure episodio di podcast). */
data class FeedEntry(
    val id: String,
    val title: String,
    val link: String,
    val summary: String,
    val imageUrl: String?,
    val publishedMillis: Long,
    val audioUrl: String?,
    val durationSec: Int,
)

/** Lettura di feed RSS 2.0 e Atom. Nessuna dipendenza da Android; rifiuta le entità XML esterne. */
object FeedParser {

    fun parse(xml: String, maxItems: Int = 40): List<FeedEntry> {
        if (xml.isBlank() || xml.contains("<!ENTITY", ignoreCase = true)) return emptyList()
        val doc = runCatching {
            val factory = DocumentBuilderFactory.newInstance()
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) }
            runCatching { factory.isExpandEntityReferences = false }
            runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trimStart('﻿', ' ', '\n', '\r', '\t'))))
        }.getOrNull() ?: return emptyList()

        val rssItems = doc.getElementsByTagName("item")
        val nodes = if (rssItems.length > 0) rssItems else doc.getElementsByTagName("entry")
        val out = ArrayList<FeedEntry>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            parseItem(el)?.let(out::add)
            if (out.size >= maxItems) break
        }
        return out
    }

    private fun parseItem(el: Element): FeedEntry? {
        val title = clean(text(el, "title"))
        if (title.isBlank()) return null
        val link = text(el, "link").ifBlank { atomLink(el) }.trim()
        val full = text(el, "content:encoded")
        // Il riassunto viene dalla descrizione breve; il testo completo serve solo come ripiego e per trovare l'immagine.
        val rawBody = text(el, "description").ifBlank { text(el, "summary") }.ifBlank { full }.ifBlank { text(el, "content") }
        val htmlForImage = full + " " + rawBody
        val enclosure = enclosure(el)
        val audio = enclosure?.takeIf { it.second.startsWith("audio") || it.first.substringBefore('?').lowercase().let { u -> u.endsWith(".mp3") || u.endsWith(".m4a") } }?.first
        val guid = text(el, "guid").ifBlank { text(el, "id") }.ifBlank { audio ?: link.ifBlank { title } }
        val date = parseDate(text(el, "pubDate").ifBlank { text(el, "dc:date") }.ifBlank { text(el, "published") }.ifBlank { text(el, "updated") })
        return FeedEntry(
            id = guid.trim(),
            title = title,
            link = link,
            summary = clean(rawBody).take(SUMMARY_MAX),
            imageUrl = image(el, htmlForImage, enclosure),
            publishedMillis = date,
            audioUrl = audio?.takeIf { it.startsWith("http") },
            durationSec = parseDuration(text(el, "itunes:duration")),
        )
    }

    private const val SUMMARY_MAX = 320

    private fun children(el: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        var n: Node? = el.firstChild
        while (n != null) {
            if (n is Element && (n.tagName == name || n.nodeName == name)) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun text(el: Element, name: String): String = children(el, name).firstOrNull()?.textContent?.trim().orEmpty()

    private fun atomLink(el: Element): String {
        val links = children(el, "link")
        val alt = links.firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } } ?: links.firstOrNull()
        return alt?.getAttribute("href").orEmpty()
    }

    /** (url, tipo) dell'allegato principale. */
    private fun enclosure(el: Element): Pair<String, String>? {
        children(el, "enclosure").firstOrNull()?.let { return it.getAttribute("url") to it.getAttribute("type").lowercase() }
        children(el, "link").firstOrNull { it.getAttribute("rel") == "enclosure" }?.let { return it.getAttribute("href") to it.getAttribute("type").lowercase() }
        return null
    }

    private fun image(el: Element, body: String, enclosure: Pair<String, String>?): String? {
        children(el, "media:thumbnail").firstOrNull()?.getAttribute("url")?.takeIf { it.isNotBlank() }?.let { return it }
        children(el, "media:content").firstOrNull { it.getAttribute("medium") == "image" || it.getAttribute("type").startsWith("image") }
            ?.getAttribute("url")?.takeIf { it.isNotBlank() }?.let { return it }
        children(el, "itunes:image").firstOrNull()?.getAttribute("href")?.takeIf { it.isNotBlank() }?.let { return it }
        if (enclosure != null && enclosure.second.startsWith("image")) return enclosure.first
        return IMG.find(body)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
    }

    private val IMG = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val TAGS = Regex("<[^>]*>")
    private val SPACES = Regex("\\s+")
    private val NUMERIC = Regex("&#(x?)([0-9a-fA-F]+);")

    /** Toglie i tag HTML e decodifica le entità più comuni. */
    fun clean(html: String): String {
        var s = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        s = TAGS.replace(s, " ")
        s = NUMERIC.replace(s) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            if (code != null && code in 32..0x10FFFF) String(Character.toChars(code)) else " "
        }
        s = s.replace("&nbsp;", " ").replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&hellip;", "…").replace("&amp;", "&")
        return SPACES.replace(s, " ").trim()
    }

    /** "1:02:03", "12:30" o "3723" → secondi. */
    fun parseDuration(raw: String): Int {
        val s = raw.trim()
        if (s.isEmpty()) return 0
        val parts = s.split(":")
        return runCatching {
            when (parts.size) {
                1 -> parts[0].toDouble().toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toDouble().toInt()
                else -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toDouble().toInt()
            }
        }.getOrDefault(0)
    }

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss z", "dd MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'",
    )

    /** Data RFC 822 o ISO 8601 → millisecondi; 0 se non leggibile. */
    fun parseDate(raw: String): Long {
        val s = raw.trim()
        if (s.isEmpty()) return 0
        for (f in DATE_FORMATS) {
            val fmt = SimpleDateFormat(f, Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
            val d = runCatching { fmt.parse(s) }.getOrNull()
            if (d != null) return d.time
        }
        return 0
    }
}

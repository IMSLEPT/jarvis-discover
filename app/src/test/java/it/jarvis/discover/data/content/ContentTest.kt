package it.jarvis.discover.data.content

import it.jarvis.discover.data.youtube.HttpFetcher
import it.jarvis.discover.data.youtube.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ContentTest {

    private val rss = """<?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:media="http://search.yahoo.com/mrss/" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
        <channel><title>Feed</title>
          <item>
            <title>Una bella notizia &amp; altro</title>
            <link>https://example.it/a</link>
            <pubDate>Tue, 03 Nov 2026 10:30:00 +0100</pubDate>
            <description><![CDATA[<p>Un paese <b>ripopola</b> la scuola&nbsp;con 12 bambini.</p>]]></description>
            <content:encoded><![CDATA[<img src="https://example.it/img.jpg"/><p>Testo</p>]]></content:encoded>
            <guid>g-1</guid>
          </item>
          <item>
            <title>Episodio 5</title>
            <link>https://pod.it/5</link>
            <pubDate>Mon, 02 Nov 2026 08:00:00 GMT</pubDate>
            <enclosure url="https://cdn.pod.it/5.mp3" length="123" type="audio/mpeg"/>
            <itunes:duration>1:02:03</itunes:duration>
            <itunes:image href="https://pod.it/cover.jpg"/>
          </item>
          <item><title></title><link>https://x</link></item>
        </channel></rss>"""

    @Test
    fun rssItemsAreParsed() {
        val items = FeedParser.parse(rss)
        assertEquals(2, items.size) // la voce senza titolo viene scartata
        val a = items[0]
        assertEquals("Una bella notizia & altro", a.title)
        assertEquals("https://example.it/a", a.link)
        assertEquals("Un paese ripopola la scuola con 12 bambini.", a.summary)
        assertEquals("https://example.it/img.jpg", a.imageUrl)
        assertEquals("g-1", a.id)
        assertTrue(a.publishedMillis > 0)
        assertNull(a.audioUrl)
    }

    @Test
    fun podcastEpisodeHasAudioDurationAndCover() {
        val e = FeedParser.parse(rss)[1]
        assertEquals("https://cdn.pod.it/5.mp3", e.audioUrl)
        assertEquals(3723, e.durationSec)
        assertEquals("https://pod.it/cover.jpg", e.imageUrl)
    }

    @Test
    fun atomEntriesAreParsed() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Titolo atom</title>
            <link rel="alternate" href="https://a.it/1"/><id>tag:1</id><updated>2026-11-01T09:00:00Z</updated><summary>Riassunto</summary></entry></feed>"""
        val e = FeedParser.parse(atom).single()
        assertEquals("https://a.it/1", e.link)
        assertEquals("Riassunto", e.summary)
        assertTrue(e.publishedMillis > 0)
    }

    @Test
    fun feedsWithEntitiesOrGarbageAreRejected() {
        assertTrue(FeedParser.parse("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&a;</title></item></channel></rss>""").isEmpty())
        assertTrue(FeedParser.parse("non è xml").isEmpty())
        assertTrue(FeedParser.parse("").isEmpty())
    }

    @Test
    fun durationsAndDatesAreTolerant() {
        assertEquals(3723, FeedParser.parseDuration("1:02:03"))
        assertEquals(750, FeedParser.parseDuration("12:30"))
        assertEquals(95, FeedParser.parseDuration("95.4"))
        assertEquals(0, FeedParser.parseDuration("boh"))
        assertEquals(FeedParser.parseDate("Tue, 03 Nov 2026 10:30:00 +0100"), FeedParser.parseDate("2026-11-03T09:30:00Z"))
        assertEquals(0L, FeedParser.parseDate("ieri"))
    }

    @Test
    fun htmlIsCleaned() {
        assertEquals("Tom & Jerry \"sì\" – ok", FeedParser.clean("<p>Tom &amp; Jerry &quot;sì&quot; &#8211; <script>x()</script>ok</p>"))
    }

    // ---- notizie
    private fun art(link: String, src: String, at: Long) =
        NewsArticle(FeedEntry(link, "t$link", link, "", null, at, null, 0), src, src, NewsGroup.Positive)

    @Test
    fun newsAreInterleavedAndDeduplicated() {
        val out = NewsRepository.interleave(
            listOf(
                listOf(art("a1", "A", 3), art("a2", "A", 1)),
                listOf(art("b1", "B", 2), art("a1", "B", 2)),
            ),
        )
        assertEquals(listOf("a1", "b1", "a2"), out.map { it.entry.link })
    }

    @Test
    fun newsSourcesAreHttpsAndUnique() {
        assertTrue(NewsSources.all { it.feedUrl.startsWith("https://") })
        assertEquals(NewsSources.size, NewsSources.map { it.id }.toSet().size)
    }

    // ---- libri
    private val olJson = """{"docs":[
        {"key":"/works/OL1W","title":"Psicologia generale","author_name":["A. Rossi","B. Bianchi","C","D"],"first_publish_year":1999,"cover_i":240727,
         "subject":["Psicologia","Mente"],"language":["ita","eng"],"edition_count":12,"ia":["psico00ross"],"has_fulltext":true},
        {"key":"/authors/OL2A","title":"non è un'opera"},
        {"key":"/works/OL3W","title":""}]}"""

    @Test
    fun openLibraryResultsAreParsed() {
        val b = OpenLibraryClient.parseSearch(olJson).single()
        assertEquals("Psicologia generale", b.title)
        assertEquals(3, b.authors.size)
        assertTrue(b.inItalian)
        assertEquals("https://covers.openlibrary.org/b/id/240727-M.jpg", b.coverUrl)
        assertEquals("https://openlibrary.org/works/OL1W", b.pageUrl)
        assertEquals("https://archive.org/details/psico00ross", b.archiveUrl)
    }

    @Test
    fun descriptionsCanBeStringOrObject() {
        assertEquals("Testo", OpenLibraryClient.parseDescription("""{"description":"<p>Testo</p>"}"""))
        assertEquals("Valore", OpenLibraryClient.parseDescription("""{"description":{"type":"/type/text","value":"Valore"}}"""))
        assertEquals("", OpenLibraryClient.parseDescription("{}"))
    }

    @Test
    fun searchBuildsLanguageFilteredQueryAndHandlesErrors() {
        val urls = ArrayList<String>()
        val ok = OpenLibraryClient(HttpFetcher { urls += it; HttpResponse(200, olJson) })
        assertTrue(ok.search("mente", italianOnly = true) is BooksResult.Ok)
        assertTrue(urls.single().contains("language%3Aita"))
        urls.clear()
        ok.search("mente", italianOnly = false)
        assertFalse(urls.single().contains("language%3Aita"))
        assertTrue(OpenLibraryClient(HttpFetcher { HttpResponse(503, "") }).search("x") is BooksResult.Err)
        assertTrue(OpenLibraryClient(HttpFetcher { throw IOException("rete") }).search("x") is BooksResult.Err)
    }

    @Test
    fun readingReasonsMatchInterests() {
        val b = OpenLibraryClient.parseSearch(olJson).single()
        assertEquals(listOf("Psicologia"), reasonsFor(b, listOf("Psicologia", "Cucina", "AI")))
        assertTrue(reasonsFor(b, listOf("Cucina")).isEmpty())
    }

    // ---- podcast
    @Test
    fun podcastSearchKeepsOnlyPlayableHttpsFeeds() {
        val json = """{"resultCount":3,"results":[
            {"collectionId":1,"collectionName":"Mente locale","artistName":"Autore","feedUrl":"https://f.it/rss","artworkUrl600":"https://a/600.jpg","primaryGenreName":"Scienze sociali"},
            {"collectionId":2,"collectionName":"Senza feed","artistName":"X"},
            {"collectionId":3,"collectionName":"Feed http","feedUrl":"http://f.it/rss"}]}"""
        val p = PodcastClient.parseSearch(json).single()
        assertEquals("1", p.id)
        assertEquals("https://a/600.jpg", p.artwork)
    }

    @Test
    fun episodesRequireHttpsAudio() {
        val xml = """<rss><channel>
          <item><title>Ok</title><guid>1</guid><enclosure url="https://c.it/1.mp3" type="audio/mpeg"/></item>
          <item><title>Http</title><guid>2</guid><enclosure url="http://c.it/2.mp3" type="audio/mpeg"/></item>
          <item><title>Senza audio</title><guid>3</guid></item></channel></rss>"""
        val client = PodcastClient(HttpFetcher { HttpResponse(200, xml) })
        val eps = (client.episodes(Podcast("9", "Pod", "A", "", "https://f.it/rss", "")) as AudioResult.Ok).value
        assertEquals(listOf("Ok"), eps.map { it.title })
        assertEquals("9:1", eps.single().id)
    }

    // ---- audiolibri
    @Test
    fun audiobookListIsParsedAndIdsValidated() {
        val json = """{"response":{"numFound":2,"docs":[
            {"identifier":"mastro_don_gesualdo_1104_librivox","title":"Mastro-don Gesualdo","creator":"Giovanni Verga"},
            {"identifier":"../evil","title":"x"},
            {"identifier":"alice_librivox","title":["Alice","altro"],"creator":["Carroll"]}]}}"""
        val books = AudiobookClient.parseList(json)
        assertEquals(listOf("mastro_don_gesualdo_1104_librivox", "alice_librivox"), books.map { it.id })
        assertEquals("Alice", books[1].title)
        assertEquals("Giovanni Verga", books[0].author)
    }

    @Test
    fun audiobookTracksPreferOneMp3PerChapter() {
        val json = """{"metadata":{"description":"<p>Romanzo</p>"},"files":[
            {"name":"cap02_64kb.mp3","format":"64Kbps MP3","title":"Capitolo 2","length":"600.5"},
            {"name":"cap01_64kb.mp3","format":"64Kbps MP3","title":"Capitolo 1","length":"05:00"},
            {"name":"cap01.mp3","format":"VBR MP3","title":"Capitolo 1","length":"300"},
            {"name":"cover.jpg","format":"JPEG"}]}"""
        val d = AudiobookClient.parseDetail(Audiobook("alice_librivox", "Alice", "C"), json)
        assertEquals("Romanzo", d.description)
        assertEquals(listOf("Capitolo 1", "Capitolo 2"), d.tracks.map { it.title })
        assertEquals("https://archive.org/download/alice_librivox/cap01_64kb.mp3", d.tracks[0].url)
        assertEquals(300, d.tracks[0].durationSec)
    }

    @Test
    fun audiobookQueryIsSanitized() {
        assertEquals("Dante Alighieri", AudiobookClient.sanitize("Dante\" OR (Alighieri*)"))
            .let { }
    }

    @Test
    fun audiobookDetailRejectsUnsafeIds() {
        val c = AudiobookClient(HttpFetcher { HttpResponse(200, "{}") })
        assertTrue(c.detail(Audiobook("../x", "t", "a")) is AudioResult.Err)
    }

    // ---- salvataggi
    @Test
    fun contentJsonRoundTrips() {
        val b = OpenLibraryClient.parseSearch(olJson).single()
        assertEquals(b, ContentJson.bookFromJson(ContentJson.bookToJson(b)))
        val p = Podcast("1", "T", "A", "https://a/x.jpg", "https://f/rss", "G")
        assertEquals(p, ContentJson.podcastFromJson(ContentJson.podcastToJson(p)))
        val a = AudioItem("id", "t", "s", "https://u/x.mp3", null, 120, 5)
        assertEquals(a, ContentJson.audioFromJson(ContentJson.audioToJson(a)))
        assertEquals(listOf(p), ContentJson.listFromJson(ContentJson.listToJson(listOf(p), ContentJson::podcastToJson), ContentJson::podcastFromJson))
        assertTrue(ContentJson.listFromJson("rotto", ContentJson::podcastFromJson).isEmpty())
    }
}

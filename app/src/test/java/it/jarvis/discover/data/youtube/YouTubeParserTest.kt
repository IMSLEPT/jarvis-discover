package it.jarvis.discover.data.youtube

import it.jarvis.discover.data.youtube.TestData.video
import it.jarvis.discover.data.youtube.TestData.videosResponse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeParserTest {

    // --- durata ---

    @Test
    fun durationIso8601() {
        assertEquals(933, YouTubeParser.parseDurationSeconds("PT15M33S"))
        assertEquals(3723, YouTubeParser.parseDurationSeconds("PT1H2M3S"))
        assertEquals(45, YouTubeParser.parseDurationSeconds("PT45S"))
        assertEquals(600, YouTubeParser.parseDurationSeconds("PT10M"))
        assertEquals(90_000, YouTubeParser.parseDurationSeconds("P1DT1H"))
        assertEquals(0, YouTubeParser.parseDurationSeconds("P0D"))
        assertEquals(0, YouTubeParser.parseDurationSeconds(""))
        assertEquals(0, YouTubeParser.parseDurationSeconds(null))
        assertEquals(0, YouTubeParser.parseDurationSeconds("quindici minuti"))
    }

    // --- ricerca ---

    @Test
    fun searchIdsIgnoreChannelsAndDuplicates() {
        val json = """{"items":[
            {"id":{"kind":"youtube#video","videoId":"aaaaaaaaaaa"}},
            {"id":{"kind":"youtube#channel","channelId":"UC123"}},
            {"id":{"kind":"youtube#video","videoId":"bbbbbbbbbbb"}},
            {"id":{"kind":"youtube#video","videoId":"aaaaaaaaaaa"}}
        ]}"""
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), YouTubeParser.parseSearchIds(json))
        assertEquals(emptyList<String>(), YouTubeParser.parseSearchIds("non è json"))
        assertEquals(emptyList<String>(), YouTubeParser.parseSearchIds("{}"))
    }

    // --- filtri dei video ---

    @Test
    fun keepsValidItalianVideo() {
        val items = YouTubeParser.parseVideos(videosResponse(video("ok1")))
        assertEquals(1, items.size)
        val v = items.single()
        assertEquals("ok1", v.id)
        assertEquals(600, v.durationSec)
        assertEquals(LangClass.Italian, v.lang)
        assertEquals(12345L, v.views)
        assertEquals(100L, v.likes)
        assertEquals("https://i.ytimg.com/vi/ok1/mqdefault.jpg", v.thumbnail)
    }

    @Test
    fun dropsUnplayableOrUnsuitableVideos() {
        val response = videosResponse(
            video("good"),
            video("noembed", embeddable = false),
            video("private", privacy = "unlisted"),
            video("live", live = "live"),
            video("soon", live = "upcoming"),
            video("blockit", blocked = listOf("DE", "IT")),
            video("onlyus", allowed = listOf("US", "GB")),
            video("kids", kids = true),
            video("tiny", duration = "PT20S"),
            video("zero", duration = "P0D"),
            video("foreign", audio = "en", caption = false),
        )
        assertEquals(listOf("good"), YouTubeParser.parseVideos(response).map { it.id })
    }

    @Test
    fun regionRulesAllowItaly() {
        val response = videosResponse(
            video("blockother", blocked = listOf("DE", "FR")),
            video("allowit", allowed = listOf("it", "us")),
        )
        assertEquals(listOf("blockother", "allowit"), YouTubeParser.parseVideos(response).map { it.id })
    }

    @Test
    fun brokenInputGivesEmptyList() {
        assertTrue(YouTubeParser.parseVideos("").isEmpty())
        assertTrue(YouTubeParser.parseVideos("{\"items\": 3}").isEmpty())
        assertTrue(YouTubeParser.parseVideos("{\"items\":[{}, {\"id\":\"x\"}]}").isEmpty())
    }

    // --- lingua ---

    @Test
    fun languageClassification() {
        fun lang(video: JSONObject) = YouTubeParser.parseVideos(videosResponse(video)).singleOrNull()?.lang

        assertEquals(LangClass.Italian, lang(video("a", audio = "it")))
        assertEquals(LangClass.Italian, lang(video("a", audio = "it-IT")))
        assertEquals(LangClass.Subtitled, lang(video("b", audio = "en", caption = true)))
        assertNull(lang(video("c", audio = "en", caption = false)))
        assertEquals(LangClass.ItalianGuess, lang(video("d", audio = null, textLang = "it")))
        assertEquals(LangClass.ItalianGuess, lang(video("e", audio = null)))
        assertNull(lang(video("f", audio = null, title = "How the brain works and what you can do", description = "In this video we explain the memory")))
        assertEquals(LangClass.Subtitled, lang(video("g", audio = null, title = "How the brain works and what you can do", description = "the memory", caption = true)))
        assertNull(lang(video("h", audio = null, textLang = "es")))
    }

    @Test
    fun looksItalianHeuristic() {
        assertTrue(YouTubeParser.looksItalian("Come funziona il cervello? Una spiegazione semplice"))
        assertFalse(YouTubeParser.looksItalian("How the brain works and what you can do"))
        assertFalse(YouTubeParser.looksItalian("Cómo funciona el cerebro"))
        assertFalse(YouTubeParser.looksItalian(""))
    }

    @Test
    fun subtitledVideosFollowTheSetting() {
        val subtitled = TestData.item("s", lang = LangClass.Subtitled)
        assertTrue(subtitled.isShown(acceptSubtitled = true))
        assertFalse(subtitled.isShown(acceptSubtitled = false))
        assertTrue(TestData.item("i", lang = LangClass.Italian).isShown(false))
        assertTrue(TestData.item("g", lang = LangClass.ItalianGuess).isShown(false))
        assertFalse(TestData.item("f", lang = LangClass.Foreign).isShown(true))
    }

    // --- miniature ---

    @Test
    fun thumbnailPrefersMaxresThenMedium() {
        val both = JSONObject("""{"medium":{"url":"https://m"},"maxres":{"url":"https://x"}}""")
        val medium = JSONObject("""{"medium":{"url":"https://m"},"high":{"url":"https://h"}}""")
        assertEquals("https://x", YouTubeParser.pickThumbnail(both, "id"))
        assertEquals("https://m", YouTubeParser.pickThumbnail(medium, "id"))
        assertEquals("https://i.ytimg.com/vi/abc/mqdefault.jpg", YouTubeParser.pickThumbnail(null, "abc"))
    }

    // --- errori ---

    @Test
    fun quotaExceededIsRecognised() {
        val body = TestData.errorBody(403, "The request cannot be completed because you have exceeded your quota.", reason = "quotaExceeded")
        assertEquals(YtErrorKind.QuotaExceeded, YouTubeParser.classifyError(403, body).kind)
        assertEquals(YtErrorKind.QuotaExceeded, YouTubeParser.classifyError(429, null).kind)
    }

    @Test
    fun invalidKeyIsRecognised() {
        val body = TestData.errorBody(400, "API key not valid. Please pass a valid API key.", reason = "badRequest", status = "INVALID_ARGUMENT", detailReason = "API_KEY_INVALID")
        val error = YouTubeParser.classifyError(400, body)
        assertEquals(YtErrorKind.InvalidKey, error.kind)
        assertTrue(error.isKeyProblem)
    }

    @Test
    fun disabledApiAndRestrictionsAreRecognised() {
        val disabled = TestData.errorBody(403, "YouTube Data API v3 has not been used in project 123 before or it is disabled.", reason = "accessNotConfigured")
        assertEquals(YtErrorKind.ApiNotEnabled, YouTubeParser.classifyError(403, disabled).kind)
        val blocked = TestData.errorBody(403, "Requests to this API youtube method are blocked.", reason = "forbidden", detailReason = "API_KEY_SERVICE_BLOCKED")
        assertEquals(YtErrorKind.KeyRestricted, YouTubeParser.classifyError(403, blocked).kind)
    }

    @Test
    fun unknownErrorsKeepOnlyCodeAndSafeDetail() {
        val none = YouTubeParser.classifyError(500, "<html>boom</html>")
        assertEquals(YtErrorKind.Other, none.kind)
        assertEquals(500, none.httpCode)
        assertNull(none.detail)

        val withKey = TestData.errorBody(400, "Bad request for key=AIzaSyA1234567890abcdefghijklmnopqrstuvw today")
        val detail = YouTubeParser.classifyError(400, withKey).detail.orEmpty()
        assertFalse(detail.contains("AIza"))
        assertTrue(detail.contains("[chiave]"))
    }

    @Test
    fun redactKeysRemovesGoogleKeys() {
        val key = "AIzaSyA1234567890abcdefghijklmnopqrstuvw"
        assertEquals("url?key=[chiave]&x=1", YouTubeParser.redactKeys("url?key=$key&x=1"))
    }

    // --- serializzazione ---

    @Test
    fun videoJsonRoundTrip() {
        val original = TestData.item("rt", topic = "Filosofia", lang = LangClass.Subtitled).copy(likes = 5, hasCaptions = true)
        val back = YouTubeParser.listFromJson(YouTubeParser.listToJson(listOf(original)))
        assertEquals(listOf(original), back)
    }

    @Test
    fun corruptedStoredJsonIsIgnored() {
        assertTrue(YouTubeParser.listFromJson(null).isEmpty())
        assertTrue(YouTubeParser.listFromJson("{non json").isEmpty())
    }
}

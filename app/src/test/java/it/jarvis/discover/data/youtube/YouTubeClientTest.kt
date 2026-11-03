package it.jarvis.discover.data.youtube

import it.jarvis.discover.data.youtube.TestData.video
import it.jarvis.discover.data.youtube.TestData.videosResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class YouTubeClientTest {

    private val key = "AIzaTESTKEY0123456789012345678901234567"

    /** Rete finta: registra gli URL richiesti e risponde con quanto deciso dal test. */
    private class FakeHttp(private val responder: (String) -> HttpResponse) : HttpFetcher {
        val urls = ArrayList<String>()
        override fun get(url: String): HttpResponse {
            urls += url
            return responder(url)
        }
    }

    private fun ok(body: String) = HttpResponse(200, body)

    @Test
    fun searchBuildsTheExpectedRequest() {
        val http = FakeHttp { ok(TestData.searchResponse("aaaaaaaaaaa")) }
        val result = YouTubeClient(http).search(key, "filosofia & mente", durationHint = "long")
        assertEquals(listOf("aaaaaaaaaaa"), (result as ApiResult.Ok).value)

        val url = http.urls.single()
        assertTrue(url.startsWith("https://www.googleapis.com/youtube/v3/search?"))
        listOf(
            "part=snippet", "type=video", "q=filosofia+%26+mente", "maxResults=25", "relevanceLanguage=it",
            "regionCode=IT", "videoEmbeddable=true", "videoSyndicated=true", "videoDuration=long", "key=$key",
        ).forEach { assertTrue("manca $it in $url", url.contains(it)) }
    }

    @Test
    fun searchWithoutDurationHintOmitsTheParameter() {
        val http = FakeHttp { ok(TestData.searchResponse()) }
        YouTubeClient(http).search(key, "x")
        assertTrue(!http.urls.single().contains("videoDuration"))
    }

    @Test
    fun videosAreRequestedInChunksOf50() {
        val http = FakeHttp { ok(videosResponse()) }
        val ids = (1..120).map { "id%09d".format(it) }
        YouTubeClient(http).videos(key, ids)
        assertEquals(3, http.urls.size)
        assertTrue(http.urls.all { it.contains("/videos?") && it.contains("part=snippet%2CcontentDetails%2Cstatistics%2Cstatus") })
        assertTrue(http.urls.none { it.contains("maxResults") })
    }

    @Test
    fun fetchFeedMergesQueriesAndAssignsTopics() {
        val http = FakeHttp { url ->
            when {
                "/search?" in url && "q=uno" in url -> ok(TestData.searchResponse("v1aaaaaaaaa", "sharedaaaaa"))
                "/search?" in url -> ok(TestData.searchResponse("sharedaaaaa", "v2aaaaaaaaa"))
                else -> ok(videosResponse(video("v1aaaaaaaaa"), video("sharedaaaaa"), video("v2aaaaaaaaa")))
            }
        }
        var searches = 0
        val result = YouTubeClient(http).fetchFeed(
            key, listOf(FeedQuery("uno", "Tema 1"), FeedQuery("due", "Tema 2")), onSearchDone = { searches++ },
        )
        assertEquals(null, result.error)
        assertEquals(2, result.searchesDone)
        assertEquals(2, searches)
        // Il video presente in entrambe le ricerche conta per la prima.
        assertEquals(
            mapOf("v1aaaaaaaaa" to "Tema 1", "sharedaaaaa" to "Tema 1", "v2aaaaaaaaa" to "Tema 2"),
            result.items.associate { it.id to it.topic },
        )
        assertEquals(1, http.urls.count { "/videos?" in it })
    }

    @Test
    fun quotaErrorKeepsWhatWasAlreadyFound() {
        var searchCalls = 0
        val http = FakeHttp { url ->
            when {
                "/search?" in url -> {
                    searchCalls++
                    if (searchCalls == 1) ok(TestData.searchResponse("v1aaaaaaaaa"))
                    else HttpResponse(403, TestData.errorBody(403, "quota", reason = "quotaExceeded"))
                }
                else -> ok(videosResponse(video("v1aaaaaaaaa")))
            }
        }
        val result = YouTubeClient(http).fetchFeed(key, listOf(FeedQuery("uno", "A"), FeedQuery("due", "B"), FeedQuery("tre", "C")))
        assertEquals(listOf("v1aaaaaaaaa"), result.items.map { it.id })
        assertEquals(YtErrorKind.QuotaExceeded, result.error?.kind)
        assertEquals(1, result.searchesDone)
        assertEquals(2, searchCalls) // si ferma al primo errore, senza insistere
    }

    @Test
    fun invalidKeyOnFirstSearchReturnsNothing() {
        val body = TestData.errorBody(400, "API key not valid. Please pass a valid API key.", detailReason = "API_KEY_INVALID")
        val http = FakeHttp { HttpResponse(400, body) }
        val result = YouTubeClient(http).fetchFeed(key, listOf(FeedQuery("uno", "A")))
        assertTrue(result.items.isEmpty())
        assertEquals(YtErrorKind.InvalidKey, result.error?.kind)
        assertEquals(0, result.searchesDone)
    }

    @Test
    fun networkFailureIsReportedWithoutLeakingTheKey() {
        val http = FakeHttp { throw IOException("Unable to resolve host for $key") }
        val result = YouTubeClient(http).search(key, "x")
        val error = (result as ApiResult.Err).error
        assertEquals(YtErrorKind.Network, error.kind)
        assertTrue(!error.userMessage.contains("AIza"))
        assertTrue(error.detail == null)
    }

    @Test
    fun testKeyUsesTheCheapEndpoint() {
        val http = FakeHttp { ok("""{"items":[]}""") }
        val result = YouTubeClient(http).testKey(key)
        assertTrue(result is ApiResult.Ok)
        assertTrue(http.urls.single().contains("/i18nLanguages?"))

        val failing = YouTubeClient(FakeHttp { HttpResponse(400, TestData.errorBody(400, "API key not valid.", detailReason = "API_KEY_INVALID")) })
        assertEquals(YtErrorKind.InvalidKey, (failing.testKey(key) as ApiResult.Err).error.kind)
    }
}

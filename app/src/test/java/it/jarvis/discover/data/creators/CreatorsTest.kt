package it.jarvis.discover.data.creators

import it.jarvis.discover.data.youtube.ApiResult
import it.jarvis.discover.data.youtube.HttpFetcher
import it.jarvis.discover.data.youtube.HttpResponse
import it.jarvis.discover.data.youtube.TestData
import it.jarvis.discover.data.youtube.YouTubeClient
import it.jarvis.discover.data.youtube.YtErrorKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorsTest {

    private val key = "AIzaTESTKEY0123456789012345678901234567"

    private fun channelsJson(vararg ids: String, hidden: Boolean = false): String {
        val items = JSONArray()
        ids.forEach { id ->
            items.put(
                JSONObject().put("id", id)
                    .put("snippet", JSONObject().put("title", "Canale $id").put("customUrl", "nome$id").put("description", "Descrizione")
                        .put("thumbnails", JSONObject().put("medium", JSONObject().put("url", "https://img/$id.jpg"))))
                    .put("statistics", JSONObject().put("subscriberCount", "12500").put("videoCount", "80").put("hiddenSubscriberCount", hidden))
                    .put("contentDetails", JSONObject().put("relatedPlaylists", JSONObject().put("uploads", "UU_$id"))),
            )
        }
        return JSONObject().put("items", items).toString()
    }

    private fun mixedSearch(videos: List<String>, channels: List<String>): String {
        val items = JSONArray()
        channels.forEach { items.put(JSONObject().put("id", JSONObject().put("kind", "youtube#channel").put("channelId", it))) }
        videos.forEach { items.put(JSONObject().put("id", JSONObject().put("kind", "youtube#video").put("videoId", it))) }
        return JSONObject().put("items", items).toString()
    }

    private fun playlist(vararg pairs: Pair<String, String>): String {
        val items = JSONArray()
        pairs.forEach { (id, at) ->
            items.put(JSONObject().put("contentDetails", JSONObject().put("videoId", id).put("videoPublishedAt", at)))
        }
        return JSONObject().put("items", items).toString()
    }

    private class FakeHttp(val responder: (String) -> HttpResponse) : HttpFetcher {
        val urls = ArrayList<String>()
        override fun get(url: String): HttpResponse { urls += url; return responder(url) }
    }

    private fun ok(b: String) = HttpResponse(200, b)

    // ------------------------------------------------------------------ parser

    @Test
    fun mixedSearchSeparatesVideosAndChannels() {
        val hit = CreatorParser.parseMixedSearch(mixedSearch(listOf("v1", "v2", "v1"), listOf("UCa", "UCb")))
        assertEquals(listOf("v1", "v2"), hit.videoIds)
        assertEquals(listOf("UCa", "UCb"), hit.channelIds)
    }

    @Test
    fun channelsAreParsedWithHandleStatsAndUploads() {
        val c = CreatorParser.parseChannels(channelsJson("UCx")).single()
        assertEquals("Canale UCx", c.title)
        assertEquals("@nomeUCx", c.handle)
        assertEquals(12_500L, c.subscribers)
        assertEquals(80L, c.videoCount)
        assertEquals("UU_UCx", c.uploadsPlaylistId)
        assertEquals("https://img/UCx.jpg", c.thumbnail)
        assertEquals("https://www.youtube.com/@nomeUCx", c.channelUrl)
    }

    @Test
    fun hiddenSubscriberCountIsNotShown() {
        assertNull(CreatorParser.parseChannels(channelsJson("UCx", hidden = true)).single().subscribers)
    }

    @Test
    fun uploadsPlaylistFallsBackToChannelIdDerivation() {
        assertEquals("UUabc123", CreatorRules.uploadsIdFor("UCabc123"))
        val json = JSONObject().put("items", JSONArray().put(JSONObject().put("id", "UCabc123").put("snippet", JSONObject().put("title", "T")))).toString()
        assertEquals("UUabc123", CreatorParser.parseChannels(json).single().uploadsPlaylistId)
    }

    @Test
    fun playlistItemsGiveIdsAndDates() {
        val page = CreatorParser.parsePlaylistItems(playlist("a" to "2024-05-02T10:00:00Z", "b" to "2024-05-01T10:00:00Z"))
        assertEquals(listOf("a", "b"), page.videoIds)
        assertEquals("2024-05-02T10:00:00Z", page.publishedAt["a"])
    }

    @Test
    fun garbageInputDoesNotCrash() {
        assertTrue(CreatorParser.parseChannels("non json").isEmpty())
        assertTrue(CreatorParser.parsePlaylistItems("{}").videoIds.isEmpty())
        assertTrue(CreatorParser.parseMixedSearch("[]").channelIds.isEmpty())
        assertTrue(CreatorParser.followsFromJson("rotto").isEmpty())
    }

    @Test
    fun followsRoundTrip() {
        val c = CreatorParser.parseChannels(channelsJson("UCx")).single()
        val f = FollowedCreator(c, followedAt = 42L, level = FollowLevel.Reduced, topics = listOf("ai", "mente"), notify = true)
        val back = CreatorParser.followsFromJson(CreatorParser.followsToJson(listOf(f))).single()
        assertEquals(f, back)
    }

    // ------------------------------------------------------------------ regole

    private fun follow(id: String, level: FollowLevel = FollowLevel.Normal, topics: List<String> = emptyList()) =
        FollowedCreator(
            Creator(id, id, null, "", "", null, null, "UU$id"), 0L, level, topics,
        )

    @Test
    fun excludedCreatorsDisappear() {
        val videos = listOf(TestData.item("a", channelId = "UC1"), TestData.item("b", channelId = "UC2"))
        val out = CreatorRules.apply(videos, mapOf("UC1" to follow("UC1", FollowLevel.Excluded)))
        assertEquals(listOf("b"), out.map { it.id })
    }

    @Test
    fun reducedCreatorsKeepOneInThree() {
        val videos = (1..9).map { TestData.item("v$it", channelId = "UC1") } + TestData.item("other", channelId = "UC2")
        val out = CreatorRules.apply(videos, mapOf("UC1" to follow("UC1", FollowLevel.Reduced)))
        assertEquals(listOf("v1", "v4", "v7", "other"), out.map { it.id })
    }

    @Test
    fun topicFilterMatchesTitleOrDescriptionCaseInsensitively() {
        val ai = TestData.item("a", channelId = "UC1").copy(title = "Intelligenza Artificiale oggi")
        val other = TestData.item("b", channelId = "UC1").copy(title = "Vlog", description = "calcio")
        val out = CreatorRules.apply(listOf(ai, other), mapOf("UC1" to follow("UC1", topics = listOf("intelligenza artificiale"))))
        assertEquals(listOf("a"), out.map { it.id })
        assertTrue(CreatorRules.matchesTopics(other, emptyList()))
        assertFalse(CreatorRules.matchesTopics(other, listOf("  ")))
    }

    @Test
    fun unfollowedCreatorsAreUntouched() {
        val videos = listOf(TestData.item("a", channelId = "UC9"))
        assertEquals(videos, CreatorRules.apply(videos, mapOf("UC1" to follow("UC1", FollowLevel.Excluded))))
    }

    // ------------------------------------------------------------------ client

    @Test
    fun universalSearchUsesOneSearchAndEnrichesResults() {
        val http = FakeHttp { url ->
            when {
                "/search?" in url -> ok(mixedSearch(listOf("vid00000001"), listOf("UCa")))
                "/channels?" in url -> ok(channelsJson("UCa"))
                "/videos?" in url -> ok(TestData.videosResponse(TestData.video("vid00000001")))
                else -> HttpResponse(404, "")
            }
        }
        val r = CreatorClient(YouTubeClient(http)).universalSearch(key, "raffaele gaito") as ApiResult.Ok
        assertEquals(listOf("UCa"), r.value.creators.map { it.id })
        assertEquals(listOf("vid00000001"), r.value.videos.map { it.id })
        assertEquals(1, http.urls.count { "/search?" in it })
        val search = http.urls.first { "/search?" in it }
        assertTrue(search.contains("type=video%2Cchannel"))
        assertFalse(search.contains("videoEmbeddable")) // incompatibile con type diverso da video
    }

    @Test
    fun handleLookupDoesNotUseSearch() {
        val http = FakeHttp { ok(channelsJson("UCa")) }
        val r = CreatorClient(YouTubeClient(http)).channelByHandle(key, "@nome") as ApiResult.Ok
        assertEquals("UCa", r.value?.id)
        assertTrue(http.urls.single().contains("/channels?"))
        assertTrue(http.urls.single().contains("forHandle=%40nome"))
    }

    private val creator = Creator("UCa", "Canale", null, "", "", null, null, "UU_UCa")

    @Test
    fun creatorVideosReadsUploadsPlaylistInOrder() {
        val http = FakeHttp { url ->
            when {
                "/playlistItems?" in url -> ok(playlist("vid00000002" to "2024-02-01T00:00:00Z", "vid00000001" to "2024-01-01T00:00:00Z"))
                "/videos?" in url -> ok(TestData.videosResponse(TestData.video("vid00000001"), TestData.video("vid00000002")))
                else -> HttpResponse(404, "")
            }
        }
        val r = CreatorClient(YouTubeClient(http)).creatorVideos(key, creator) as ApiResult.Ok
        assertEquals(listOf("vid00000002", "vid00000001"), r.value.videos.map { it.id })
        assertEquals(0, r.value.searchesUsed)
        assertTrue(http.urls.first().contains("playlistId=UU_UCa"))
    }

    @Test
    fun creatorVideosFallsBackToSearchWhenPlaylistIsRefused() {
        val http = FakeHttp { url ->
            when {
                "/playlistItems?" in url -> HttpResponse(404, TestData.errorBody(404, "playlistNotFound", reason = "playlistNotFound"))
                "/search?" in url -> ok(TestData.searchResponse("vid00000001"))
                "/videos?" in url -> ok(TestData.videosResponse(TestData.video("vid00000001")))
                else -> HttpResponse(404, "")
            }
        }
        val r = CreatorClient(YouTubeClient(http)).creatorVideos(key, creator) as ApiResult.Ok
        assertEquals(1, r.value.searchesUsed)
        assertTrue(http.urls.any { "channelId=UCa" in it && "order=date" in it })
    }

    @Test
    fun creatorVideosDoesNotFallBackOnQuotaErrors() {
        val http = FakeHttp { HttpResponse(403, TestData.errorBody(403, "quota", reason = "quotaExceeded")) }
        val r = CreatorClient(YouTubeClient(http)).creatorVideos(key, creator) as ApiResult.Err
        assertEquals(YtErrorKind.QuotaExceeded, r.error.kind)
        assertEquals(1, http.urls.size)
    }

    @Test
    fun followedFeedAlternatesCreatorsAndSkipsFailures() {
        val a = creator
        val b = creator.copy(id = "UCb", title = "B", uploadsPlaylistId = "UU_UCb")
        val c = creator.copy(id = "UCc", title = "C", uploadsPlaylistId = "UU_UCc")
        val http = FakeHttp { url ->
            when {
                "playlistId=UU_UCa" in url -> ok(playlist("vid0000000a1" to "x", "vid0000000a2" to "x"))
                "playlistId=UU_UCb" in url -> ok(playlist("vid0000000b1" to "x"))
                "playlistId=UU_UCc" in url -> HttpResponse(404, TestData.errorBody(404, "non trovata"))
                "/videos?" in url -> ok(TestData.videosResponse(
                    TestData.video("vid0000000a1", channelId = "UCa"), TestData.video("vid0000000a2", channelId = "UCa"),
                    TestData.video("vid0000000b1", channelId = "UCb"),
                ))
                else -> HttpResponse(404, "")
            }
        }
        val r = CreatorClient(YouTubeClient(http)).followedFeed(key, listOf(a, b, c)) as ApiResult.Ok
        assertEquals(listOf("vid0000000a1", "vid0000000b1", "vid0000000a2"), r.value.map { it.id })
        assertEquals("Canale", r.value.first().topic)
    }

    @Test
    fun urlsNeverGoThroughLogsOrErrorsWithKey() {
        val http = FakeHttp { HttpResponse(400, TestData.errorBody(400, "API key not valid. Please pass a valid API key: $key")) }
        val r = CreatorClient(YouTubeClient(http)).channels(key, listOf("UCa")) as ApiResult.Err
        assertFalse(r.error.userMessage.contains(key))
    }
}

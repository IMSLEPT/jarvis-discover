package it.jarvis.discover.data.reco

import it.jarvis.discover.data.youtube.LangClass
import it.jarvis.discover.data.youtube.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecoTest {
    private val now = Instant.parse("2026-11-03T12:00:00Z").toEpochMilli()

    private fun video(
        id: String,
        topic: String? = "Storia",
        channel: String = "c-$id",
        title: String = "Titolo $id",
        sec: Int = 600,
        views: Long? = 50_000,
        likes: Long? = 2_000,
        published: String = "2026-10-20T10:00:00Z",
        captions: Boolean = true,
        lang: LangClass = LangClass.Italian,
    ) = VideoItem(id, title, channel, "Canale $channel", "", published, sec, "", views, likes, lang, captions, topic)

    private fun ctx(
        interests: Set<String> = emptySet(),
        profile: ProfileSnapshot = ProfileSnapshot.Empty,
        seen: Set<String> = emptySet(),
        followed: Set<String> = emptySet(),
        exploration: Exploration = Exploration.Off,
    ) = RankContext(interests, profile, seen, { it in followed }, exploration, now)

    private fun store(initial: String? = null, saved: MutableList<String> = mutableListOf()) =
        ProfileStore(initial, now = { now }, persist = { saved += it })

    // ---------------------------------------------------------------- ranking

    @Test
    fun interestMatchComesFirst() {
        val items = listOf(video("a", topic = "Cucina"), video("b", topic = "Storia"))
        val r = Recommender.rank(items, ctx(interests = setOf("Storia")))
        assertEquals("b", r.first().item.id)
        assertTrue(r.first().reasons.any { it.contains("interesse") })
    }

    @Test
    fun learnedAffinityRaisesAndRejectionLowersATopic() {
        val items = listOf(video("a", topic = "Cucina"), video("b", topic = "Storia"), video("c", topic = "Fisica"))
        val up = Recommender.rank(items, ctx(profile = ProfileSnapshot(topics = mapOf("fisica" to 8.0))))
        assertEquals("c", up.first().item.id)
        val down = Recommender.rank(items, ctx(profile = ProfileSnapshot(topics = mapOf("cucina" to -8.0))))
        assertEquals("a", down.last().item.id)
    }

    @Test
    fun alreadyWatchedVideoIsPushedDown() {
        val items = listOf(video("a"), video("b", topic = "Arte"))
        val r = Recommender.rank(items, ctx(seen = setOf("a")))
        assertEquals("b", r.first().item.id)
    }

    @Test
    fun clickbaitTitleLosesPoints() {
        val items = listOf(
            video("bait", topic = "Arte", title = "NON CREDERAI A QUELLO CHE SUCCEDE!! INCREDIBILE"),
            video("clean", topic = "Arte2", title = "Le tecniche di Caravaggio spiegate"),
        )
        val r = Recommender.rank(items, ctx())
        assertEquals("clean", r.first().item.id)
        assertTrue(Quality.clickbait("NON CREDERAI A QUELLO CHE SUCCEDE!!") > 0.5)
        assertEquals(0.0, Quality.clickbait("Introduzione alla relatività"), 0.0)
    }

    @Test
    fun followedCreatorGetsABonusWithReason() {
        val items = listOf(video("a", channel = "x"), video("b", channel = "y"))
        val r = Recommender.rank(items, ctx(followed = setOf("y")))
        assertEquals("b", r.first().item.id)
        assertTrue(r.first().reasons.any { it.contains("segui") })
    }

    @Test
    fun usualDurationIsPreferredOnceThereIsEnoughHistory() {
        val profile = ProfileSnapshot(durations = mapOf(DurationBucket.Short to 9, DurationBucket.Long to 1))
        val items = listOf(video("long", topic = "A", sec = 3600), video("short", topic = "B", sec = 240))
        val r = Recommender.rank(items, ctx(profile = profile))
        assertEquals("short", r.first().item.id)
    }

    @Test
    fun freshnessBreaksTiesInFavourOfNewerVideos() {
        val items = listOf(
            video("old", topic = "A", published = "2019-01-01T00:00:00Z"),
            video("new", topic = "B", published = "2026-11-01T00:00:00Z"),
        )
        assertEquals("new", Recommender.rank(items, ctx()).first().item.id)
    }

    @Test
    fun diversityAvoidsLongRunsOfTheSameTopic() {
        val items = (1..6).map { video("s$it", topic = "Storia") } +
            (1..3).map { video("a$it", topic = "Arte", views = 10_000, likes = 100) }
        val r = Recommender.rank(items, ctx(interests = setOf("Storia")))
        val firstFive = r.take(5).map { it.item.topic }
        assertTrue("Arte deve comparire presto: $firstFive", "Arte" in firstFive)
    }

    @Test
    fun explorationInsertsAMarkedNewTopicOnlyWhenEnabled() {
        val items = (1..8).map { video("s$it", topic = "Storia") } + video("n1", topic = "Astronomia")
        val off = Recommender.rank(items, ctx(interests = setOf("Storia"), exploration = Exploration.Off))
        assertFalse(off.any { it.exploratory })
        val on = Recommender.rank(items, ctx(interests = setOf("Storia"), exploration = Exploration.High))
        val e = on.firstOrNull { it.exploratory }
        assertNotNull(e)
        assertEquals("n1", e!!.item.id)
        assertTrue(on.indexOf(e) < 4)
        assertTrue(e.reasons.first().contains("orizzonti"))
    }

    @Test
    fun explorationSkipsClickbaitAndLowQuality() {
        val items = (1..6).map { video("s$it", topic = "Storia") } +
            video("bait", topic = "Gossip", title = "SHOCK!! NON CREDERAI A QUESTO VIDEO", views = 100, likes = 0, captions = false, lang = LangClass.ItalianGuess)
        val r = Recommender.rank(items, ctx(interests = setOf("Storia"), exploration = Exploration.High))
        assertFalse(r.any { it.exploratory })
    }

    @Test
    fun everyVideoGetsReasonsAndRankingIsDeterministic() {
        val items = (1..10).map { video("v$it", topic = if (it % 2 == 0) "Storia" else "Arte") }
        val c = ctx(interests = setOf("Storia"), exploration = Exploration.Medium)
        val a = Recommender.rank(items, c)
        val b = Recommender.rank(items, c)
        assertEquals(a.map { it.item.id }, b.map { it.item.id })
        assertEquals(items.size, a.size)
        assertTrue(a.all { it.reasons.isNotEmpty() })
        assertTrue(a.all { it.reasons.size <= 3 })
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertTrue(Recommender.rank(emptyList(), ctx()).isEmpty())
    }

    // ---------------------------------------------------------------- profilo

    @Test
    fun signalsAccumulateWithExpectedSignsAndLimits() {
        val p = store()
        val v = video("a", topic = "Storia", channel = "ch")
        p.onLiked(v, true)
        assertTrue(p.topics["storia"]!! > 1.9)
        assertTrue(p.channels["ch"]!! > 0.9)
        p.onHidden(v)
        assertTrue(p.topics["storia"]!! < 0.0)
        repeat(50) { p.onSaved(v, true) }
        assertEquals(ProfileStore.LIMIT, p.topics["storia"]!!, 1e-9)
    }

    @Test
    fun watchedGoesInHistoryOnceAndCountsDuration() {
        val p = store()
        val v = video("a", sec = 120)
        p.onWatched(v)
        p.onWatched(v)
        assertEquals(1, p.history.size)
        assertEquals(setOf("a"), p.seenIds)
        assertEquals(1, p.durations[DurationBucket.Short] ?: 0)
        assertEquals(DurationBucket.Medium, DurationBucket.of(600))
        assertEquals(DurationBucket.Long, DurationBucket.of(3600))
    }

    @Test
    fun historyIsCapped() {
        val p = store()
        repeat(ProfileStore.MAX_HISTORY + 20) { p.onWatched(video("v$it")) }
        assertEquals(ProfileStore.MAX_HISTORY, p.history.size)
        assertEquals("v${ProfileStore.MAX_HISTORY + 19}", p.history.first().video.id)
    }

    @Test
    fun olderSignalsFadeAsNewOnesArrive() {
        val p = store()
        p.onLiked(video("a", topic = "Storia"), true)
        val before = p.topics["storia"]!!
        repeat(200) { p.onLiked(video("x$it", topic = "Altro"), true) }
        assertTrue(p.topics["storia"]!! < before * 0.5)
    }

    @Test
    fun learningOffRecordsNothingNew() {
        val p = store()
        p.changeLearning(false)
        val v = video("a")
        p.onWatched(v); p.onLiked(v, true); p.onSaved(v, true); p.onHidden(v); p.onMoreLike(v)
        assertTrue(p.isEmpty)
    }

    @Test
    fun manualAdjustAndRemove() {
        val p = store()
        p.adjustTopic("Fisica", 1.0)
        assertEquals(1.0, p.topics["fisica"]!!, 1e-9)
        p.adjustTopic("fisica", -1.0)
        assertTrue(p.topics["fisica"] == null) // portato a zero, ripulito
        p.adjustTopic("Arte", 2.0)
        p.removeTopic("ARTE")
        assertTrue(p.topics.isEmpty())
    }

    @Test
    fun resetsClearWhatTheyPromise() {
        val p = store()
        val v = video("a", topic = "Storia", channel = "ch")
        p.onWatched(v); p.onLiked(v, true)
        p.changeExploration(Exploration.High)
        p.resetLearning()
        assertTrue(p.topics.isEmpty() && p.channels.isEmpty() && p.durations.isEmpty())
        assertEquals(1, p.history.size)
        assertEquals(Exploration.High, p.exploration)
        p.clearHistory()
        assertTrue(p.history.isEmpty())
        p.onLiked(v, true)
        p.changeLearning(false)
        p.resetAll()
        assertTrue(p.isEmpty)
        assertTrue(p.learning)
        assertEquals(Exploration.Medium, p.exploration)
    }

    @Test
    fun encodeDecodeRoundTripAndPersistence() {
        val saved = mutableListOf<String>()
        val p = store(saved = saved)
        val v = video("a", topic = "Storia", channel = "ch")
        p.onWatched(v); p.onSaved(v, true)
        p.changeExploration(Exploration.Low)
        assertTrue(saved.isNotEmpty())

        val q = store(initial = saved.last())
        assertEquals(p.topics, q.topics)
        assertEquals(p.channels, q.channels)
        assertEquals(p.durations, q.durations)
        assertEquals(Exploration.Low, q.exploration)
        assertEquals("a", q.history.single().video.id)
        assertEquals("Canale ch", q.channelNames["ch"])
    }

    @Test
    fun corruptedDataFallsBackToEmpty() {
        assertTrue(store(initial = "non è json").isEmpty)
        assertTrue(store(initial = null).isEmpty)
        assertTrue(store(initial = "").isEmpty)
    }

    @Test
    fun profileFeedsTheRankerEndToEnd() {
        val p = store()
        repeat(3) { p.onLiked(video("l$it", topic = "Fisica", channel = "f"), true) }
        val items = listOf(video("a", topic = "Cucina"), video("b", topic = "Fisica"))
        val r = Recommender.rank(items, ctx(profile = p.snapshot()))
        assertEquals("b", r.first().item.id)
        assertTrue(r.first().reasons.any { it.contains("apprezzato") })
    }
}

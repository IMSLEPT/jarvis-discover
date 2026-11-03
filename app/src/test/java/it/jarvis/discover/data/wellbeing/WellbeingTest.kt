package it.jarvis.discover.data.wellbeing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class WellbeingTest {

    private fun store(today: Long = 20000) = WellbeingStore(null, null, { today }) { _, _ -> }

    private class FakeSpeaker(val ok: Boolean = true, val result: SpeakResult = SpeakResult.Done) : Speaker {
        val said = ArrayList<String>()
        override suspend fun prepare() = ok
        override suspend fun speak(text: String): SpeakResult { said += text; delay(2000); return result }
        override fun stop() {}
        override fun shutdown() {}
    }

    // ---- catalogo
    @Test
    fun sessionsAreWellFormed() {
        assertEquals(Sessions.all.size, Sessions.all.map { it.id }.toSet().size)
        Sessions.all.forEach { s ->
            assertTrue(s.id, s.steps.size >= 5)
            assertTrue(s.id, s.steps.all { it.text.isNotBlank() && it.pauseSec in 3..90 })
            assertTrue(s.id + " durata " + s.durationSec, s.durationSec in 40..900)
            assertTrue(s.about.isNotBlank())
        }
        // La camminata e il rilassamento muscolare avvertono prima di iniziare.
        assertTrue(Sessions.byId("camminata")!!.caution != null)
        assertTrue(Sessions.byId("stress_sciogli")!!.caution != null)
    }

    @Test
    fun emotionsAndPathPointToExistingExercises() {
        Emotions.all.forEach { e ->
            e.sessionIds.forEach { id -> assertTrue("${e.id}:$id", Sessions.byId(id) != null) }
            e.breathId?.let { assertTrue(Breathing.byId(it) != null) }
        }
        StressPath.steps.forEach { st ->
            assertTrue(st.refId, if (st.isBreath) Breathing.byId(st.refId) != null else Sessions.byId(st.refId) != null)
        }
        assertEquals(7, StressPath.steps.size)
    }

    // ---- respirazione
    @Test
    fun breathingPhasesFollowThePattern() {
        val p = Breathing.byId("lenta_4_6")!!
        assertEquals(10, p.cycleSec)
        assertEquals("Inspira", Breathing.pointAt(p, 0).phase.label)
        assertEquals("Inspira", Breathing.pointAt(p, 3999).phase.label)
        assertEquals("Espira", Breathing.pointAt(p, 4000).phase.label)
        assertEquals("Inspira", Breathing.pointAt(p, 10_000).phase.label)
        assertEquals(1, Breathing.pointAt(p, 10_000).cycle)
        // il cerchio cresce con l'inspirazione e cala con l'espirazione
        assertEquals(0.55f, Breathing.pointAt(p, 0).scale, 0.01f)
        assertEquals(1f, Breathing.pointAt(p, 3999).scale, 0.02f)
        assertEquals(0.55f, Breathing.pointAt(p, 9999).scale, 0.02f)
        assertTrue(Breathing.pointAt(p, 2000).scale in 0.7f..0.85f)
        assertEquals(4, Breathing.pointAt(p, 0).secondsLeft)
    }

    @Test
    fun squareBreathingHasHoldsAndOthersDoNot() {
        assertTrue(Breathing.byId("quadrato_4")!!.hasHold)
        assertFalse(Breathing.byId("lenta_4_6")!!.hasHold)
        val q = Breathing.byId("quadrato_4")!!
        assertEquals(listOf("Inspira", "Trattieni", "Espira", "Pausa"), q.phases.map { it.label })
        assertEquals("Trattieni", Breathing.pointAt(q, 5000).phase.label)
        assertEquals(1f, Breathing.pointAt(q, 5000).scale, 0.001f)
    }

    @Test
    fun noLongHoldsAreOffered() {
        // Nessun esercizio propone trattenute lunghe: al massimo 4 secondi.
        Breathing.patterns.forEach { p -> assertTrue(p.phases.filter { it.from == it.to }.all { it.seconds <= 4 }) }
    }

    @Test
    fun breathingPlayerFinishesAndLogsOnlyAfterHalfMinute() = runTest {
        val st = store()
        val player = BreathingPlayer(this, st)
        player.start(Breathing.byId("lenta_4_6")!!, 1)
        advanceUntilIdle()
        assertTrue(player.finished)
        assertEquals(1, st.entries.size)
        assertEquals(ActivityKind.Breathing, st.entries[0].kind)
        assertEquals(60, st.entries[0].seconds)
        // 1 minuto non basta per segnare il passaggio del percorso (richiede 3 minuti)
        assertTrue(st.pathDone.isEmpty())

        val st2 = store()
        val p2 = BreathingPlayer(this, st2)
        p2.start(Breathing.byId("lenta_4_6")!!, 3)
        advanceUntilIdle()
        assertEquals(listOf("lenta_4_6"), st2.pathDone.toList())

        val st3 = store()
        val p3 = BreathingPlayer(this, st3)
        p3.start(Breathing.byId("lenta_4_6")!!, 5)
        advanceTimeBy(10_000); runCurrent()
        p3.stop()
        assertTrue(st3.entries.isEmpty()) // 10 secondi: non si registra
    }

    // ---- sessioni
    private fun tiny(): Session = Session(
        "respiro_ancora", "Prova", SessionCategory.Meditation, "x",
        List(6) { Step("Una frase di prova numero $it con alcune parole", 10) },
    )

    @Test
    fun sessionRunsAllStepsWithVoiceAndLogs() = runTest {
        val st = store()
        val speaker = FakeSpeaker()
        val player = SessionPlayer(this, speaker, st, now = { currentTime })
        player.start(tiny())
        advanceUntilIdle()
        assertEquals(PlayState.Finished, player.state)
        assertEquals(6, speaker.said.size)
        assertEquals(1, st.entries.size)
        assertEquals(ActivityKind.Meditation, st.entries[0].kind)
        assertEquals(72, st.entries[0].seconds) // 6 × (2 s di voce + 10 s di silenzio)
        assertTrue(st.pathDone.isEmpty()) // "respiro_ancora" non fa parte del percorso antistress
    }

    @Test
    fun withoutVoiceItReadsSilentlyAndStillFinishes() = runTest {
        val st = store()
        val speaker = FakeSpeaker(ok = false)
        val player = SessionPlayer(this, speaker, st, now = { currentTime })
        player.start(tiny())
        advanceUntilIdle()
        assertFalse(player.voiceAvailable)
        assertEquals(PlayState.Finished, player.state)
        assertTrue(speaker.said.isEmpty())
    }

    @Test
    fun pauseStopsProgressAndResumeContinues() = runTest {
        val st = store()
        val player = SessionPlayer(this, FakeSpeaker(), st, now = { currentTime })
        player.start(tiny())
        advanceTimeBy(5_000); runCurrent()
        player.pause()
        val at = player.index
        advanceTimeBy(600_000); runCurrent()
        assertEquals(PlayState.Paused, player.state)
        assertEquals(at, player.index)
        player.resume()
        advanceUntilIdle()
        assertEquals(PlayState.Finished, player.state)
        // il tempo in pausa non si conta
        assertTrue(st.entries[0].seconds < 120)
    }

    @Test
    fun skippingThroughDoesNotMarkThePath() = runTest {
        val st = store()
        val player = SessionPlayer(this, FakeSpeaker(), st, now = { currentTime })
        player.start(tiny())
        advanceTimeBy(1_000); runCurrent()
        repeat(6) { player.next(); advanceTimeBy(100); runCurrent() }
        advanceUntilIdle()
        assertEquals(PlayState.Finished, player.state)
        assertTrue(st.pathDone.isEmpty())
        assertTrue(st.entries.isEmpty()) // meno di mezzo minuto
    }

    @Test
    fun stoppingEarlyRecordsOnlyIfLongEnough() = runTest {
        val st = store()
        val player = SessionPlayer(this, FakeSpeaker(), st, now = { currentTime })
        player.start(tiny())
        advanceTimeBy(20_000); runCurrent()
        player.stop()
        assertEquals(PlayState.Idle, player.state)
        assertTrue(st.entries.isEmpty())

        player.start(tiny())
        advanceTimeBy(45_000); runCurrent()
        player.stop()
        assertEquals(1, st.entries.size)
        assertTrue(st.pathDone.isEmpty()) // fermata prima della fine: non conta per il percorso
    }

    // ---- diario
    @Test
    fun logKeepsPathAndCapsEntries() {
        val st = store()
        st.log(ActivityKind.Stress, "Sciogli la tensione", 400, refId = "stress_sciogli")
        st.log(ActivityKind.Stress, "Sciogli la tensione", 400, refId = "stress_sciogli")
        st.log(ActivityKind.Mindfulness, "Altro", 100, refId = "non_nel_percorso")
        assertEquals(listOf("stress_sciogli"), st.pathDone.toList())
        repeat(WellbeingStore.MAX_ENTRIES + 20) { st.log(ActivityKind.Sound, "x", 60) }
        assertEquals(WellbeingStore.MAX_ENTRIES, st.entries.size)
        st.resetPath()
        assertTrue(st.pathDone.isEmpty())
    }

    @Test
    fun weekTotalsAndActiveDays() {
        val entries = listOf(
            Activity(20000, ActivityKind.Breathing, "a", 180),
            Activity(20000, ActivityKind.Sound, "b", 120),
            Activity(19998, ActivityKind.Meditation, "c", 300),
            Activity(19990, ActivityKind.Meditation, "d", 600),   // fuori dalla settimana
            Activity(19980, ActivityKind.Gratitude, "e", 0),
        )
        val week = WellbeingStore.lastDays(entries, 20000, 7)
        assertEquals(7, week.size)
        assertEquals(20000L, week.last().epochDay)
        assertEquals(300, week.last().seconds)
        assertEquals(2, week.last().count)
        assertEquals(600, week.sumOf { it.seconds })
        assertEquals(2, WellbeingStore.activeDays(entries, 20000, 7))
        assertEquals(4, WellbeingStore.activeDays(entries, 20000, 30))
    }

    @Test
    fun storeEncodingRoundTripsAndToleratesGarbage() {
        val list = listOf(Activity(1, ActivityKind.Sound, "Onde", 90), Activity(2, ActivityKind.Gratitude, "Gratitudine", 0))
        assertEquals(list, WellbeingStore.decode(WellbeingStore.encode(list)))
        assertTrue(WellbeingStore.decode("rotto").isEmpty())
        assertTrue(WellbeingStore.decode(null).isEmpty())
        val saved = ArrayList<String>()
        val st = WellbeingStore(WellbeingStore.encode(list), "[\"gentilezza\"]", { 5 }) { l, _ -> saved += l }
        assertEquals(2, st.entries.size)
        assertEquals(listOf("gentilezza"), st.pathDone.toList())
        st.log(ActivityKind.Sound, "x", 40)
        assertEquals(3, WellbeingStore.decode(saved.last()).size)
    }

    // ---- rumori
    private fun samples(kind: NoiseKind, seconds: Double, seed: Long = 7): DoubleArray {
        val synth = NoiseSynth(kind, seed)
        val n = (seconds * synth.sampleRate).toInt()
        val out = ShortArray(n)
        synth.fill(out)
        return DoubleArray(n) { out[it] / 32768.0 }
    }

    private fun rms(a: DoubleArray, from: Int, to: Int): Double = Math.sqrt((from until to).sumOf { a[it] * a[it] } / (to - from))

    @Test
    fun noiseIsBoundedAudibleAndDeterministic() {
        NoiseKind.entries.forEach { k ->
            val a = samples(k, 3.0)
            assertTrue("$k fuori scala", a.all { abs(it) <= 0.96 })
            assertTrue("$k muto", rms(a, 0, a.size) > 0.02)
            assertTrue("$k non deterministico", a.contentEquals(samples(k, 3.0)))
        }
    }

    @Test
    fun brownNoiseIsDeeperThanWhiteAndWavesBreathe() {
        fun lag1(a: DoubleArray): Double {
            val m = a.average()
            var num = 0.0; var den = 0.0
            for (i in 1 until a.size) { num += (a[i] - m) * (a[i - 1] - m); den += (a[i] - m) * (a[i] - m) }
            return num / den
        }
        assertTrue(lag1(samples(NoiseKind.White, 2.0)) < 0.1)
        assertTrue(lag1(samples(NoiseKind.Brown, 2.0)) > 0.9)
        // Le onde cambiano volume nel tempo.
        val w = samples(NoiseKind.Waves, 20.0)
        val sr = NoiseSynth(NoiseKind.Waves).sampleRate
        val windows = (0 until 20).map { rms(w, it * sr, (it + 1) * sr) }
        assertTrue(windows.max() > windows.min() * 2.0)
    }
}

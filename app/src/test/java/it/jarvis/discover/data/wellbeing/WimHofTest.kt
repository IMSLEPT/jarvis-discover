package it.jarvis.discover.data.wellbeing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WimHofTest {

    private val gentle = WimHof.byId(WimHof.GENTLE_ID)!!
    private val standard = WimHof.byId("standard")!!

    private fun store() = WellbeingStore(null, null, { 20000 }) { _, _ -> }

    @Test
    fun levelsAreCautious() {
        // L'apnea ha sempre un tetto, il livello Dolce è più morbido dello Standard e il recupero dura 15 secondi.
        WimHof.levels.forEach { l ->
            assertTrue(l.id, l.maxHoldSec in 1..60)
            assertTrue(l.id, l.breaths in 10..30)
            assertTrue(l.id, l.rounds in 1..4)
            assertEquals(15, l.recoveryHoldSec)
            assertTrue(l.id, l.inhaleMs >= 1000 && l.exhaleMs >= 1000)
        }
        assertTrue(gentle.breaths < standard.breaths)
        assertTrue(gentle.maxHoldSec < standard.maxHoldSec)
    }

    @Test
    fun runFollowsTheSequence() {
        val r = WimHofRun(gentle)
        assertEquals(WhPhase.Inhale, r.phase)
        // Tutti i respiri del primo giro.
        r.advance(gentle.breaths * (gentle.inhaleMs + gentle.exhaleMs).toLong() - 1)
        assertEquals(WhPhase.Exhale, r.phase)
        assertEquals(gentle.breaths, r.breath)
        r.advance(1)
        assertEquals(WhPhase.Hold, r.phase)
        assertEquals(1, r.round)
        // L'apnea non finisce da sola prima del tetto.
        r.advance(10_000)
        assertEquals(WhPhase.Hold, r.phase)
        assertEquals(10, r.secondsShown)
        r.endHold()
        assertEquals(listOf(10), r.holdsSec)
        assertEquals(WhPhase.RecoveryInhale, r.phase)
        r.advance(WimHof.RECOVERY_INHALE_MS.toLong())
        assertEquals(WhPhase.RecoveryHold, r.phase)
        assertEquals(15, r.secondsShown)
        r.advance(15_000)
        assertEquals(WhPhase.Release, r.phase)
        r.advance(WimHof.RELEASE_MS.toLong())
        assertEquals(WhPhase.Inhale, r.phase)
        assertEquals(2, r.round)
        assertEquals(1, r.breath)
    }

    @Test
    fun holdIsCappedAndCarriesTheLeftoverTime() {
        val r = WimHofRun(gentle)
        r.advance(gentle.breaths * (gentle.inhaleMs + gentle.exhaleMs).toLong())
        assertEquals(WhPhase.Hold, r.phase)
        // Un solo grande salto di tempo: l'apnea si ferma al tetto e il resto passa al recupero.
        r.advance(gentle.maxHoldSec * 1000L + 1500)
        assertEquals(listOf(gentle.maxHoldSec), r.holdsSec)
        assertEquals(WhPhase.RecoveryInhale, r.phase)
        assertEquals(1500L, r.phaseMs)
    }

    @Test
    fun endHoldOutsideTheHoldDoesNothing() {
        val r = WimHofRun(gentle)
        r.endHold()
        assertEquals(WhPhase.Inhale, r.phase)
        assertTrue(r.holdsSec.isEmpty())
    }

    @Test
    fun fullRunFinishesAfterAllRounds() {
        val r = WimHofRun(standard)
        var guard = 0
        while (!r.finished && guard++ < 100_000) {
            if (r.phase == WhPhase.Hold && r.phaseMs >= 5000) r.endHold()
            r.advance(100)
        }
        assertTrue(r.finished)
        assertEquals(standard.rounds, r.round)
        assertEquals(standard.rounds, r.holdsSec.size)
        assertTrue(r.holdsSec.all { it == 5 })
        // Durata = parte fissa + le apnee.
        assertEquals(WimHof.fixedSeconds(standard) + 5 * standard.rounds.toLong(), r.elapsedMs / 1000)
        // Da finito il tempo non scorre più.
        val e = r.elapsedMs
        r.advance(5000)
        assertEquals(e, r.elapsedMs)
    }

    @Test
    fun circleStaysWithinBoundsAndEmptyDuringHold() {
        val r = WimHofRun(gentle)
        var guard = 0
        while (!r.finished && guard++ < 100_000) {
            assertTrue(r.scale in 0.55f..1f)
            if (r.phase == WhPhase.Hold) {
                assertEquals(0.55f, r.scale, 0.0001f)
                if (r.phaseMs >= 3000) r.endHold()
            }
            if (r.phase == WhPhase.RecoveryHold) assertEquals(1f, r.scale, 0.0001f)
            r.advance(100)
        }
        assertTrue(r.finished)
    }

    @Test
    fun standardIsLockedUntilGentleIsCompleted() {
        val none = emptyList<Activity>()
        assertTrue(WimHof.unlocked(gentle, none))
        assertFalse(WimHof.unlocked(standard, none))
        // Una sessione interrotta non sblocca.
        val interrupted = listOf(Activity(1, ActivityKind.Breathing, WimHof.logTitle(gentle, completed = false), 200))
        assertFalse(WimHof.unlocked(standard, interrupted))
        val done = listOf(Activity(1, ActivityKind.Breathing, WimHof.logTitle(gentle, completed = true), 480))
        assertTrue(WimHof.unlocked(standard, done))
    }

    @Test
    fun playerLogsCompletedSessionOnce() = runTest {
        val s = store()
        val p = WimHofPlayer(this, s)
        p.start(gentle)
        // Fa il suo corso: respiri, poi l'utente chiude ogni apnea dopo 4 secondi.
        var guard = 0
        while (p.run?.finished == false && guard++ < 100_000) {
            advanceTimeBy(WimHofPlayer.TICK)
            runCurrent()
            val r = p.run
            if (r != null && r.phase == WhPhase.Hold && r.phaseMs >= 4000) p.endHold()
        }
        runCurrent()
        assertTrue(p.run!!.finished)
        assertEquals(1, s.entries.size)
        assertEquals("Wim Hof · Dolce", s.entries[0].title)
        assertEquals(ActivityKind.Breathing, s.entries[0].kind)
        p.stop() // chiudere la schermata finale non registra di nuovo
        assertNull(p.run)
        assertEquals(1, s.entries.size)
    }

    @Test
    fun stoppingEarlyLogsInterruptedOnlyIfLongEnough() = runTest {
        val s = store()
        val p = WimHofPlayer(this, s)
        p.start(gentle)
        advanceTimeBy(10_000); runCurrent()
        p.stop()
        assertNull(p.run)
        assertTrue(s.entries.isEmpty())

        p.start(gentle)
        advanceTimeBy(60_000); runCurrent()
        p.stop()
        assertEquals(1, s.entries.size)
        assertEquals("Wim Hof · Dolce (interrotto)", s.entries[0].title)
        assertFalse(WimHof.unlocked(standard, s.entries.toList()))
    }
}

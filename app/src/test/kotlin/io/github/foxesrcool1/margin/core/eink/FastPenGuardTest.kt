package io.github.foxesrcool1.margin.core.eink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FastPenGuardTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun guard() = FastPenGuard(temp.root.resolve("eink"))

    private fun crash(path: FastPenPath) = repeat(2) {
        guard().markTrying(path)
        guard().settleAfterStart()
    }

    @Test
    fun aPathNobodyTriedMayBeTried() {
        assertEquals(FastPenGuard.State.Untried, guard().state(FastPenPath.Writing))
        assertTrue(guard().mayTry(FastPenPath.Writing))
    }

    @Test
    fun aCallThatCameBackIsFine() {
        val guard = guard()
        guard.markTrying(FastPenPath.Writing)
        guard.markFine(FastPenPath.Writing)
        assertEquals(emptyList<FastPenPath>(), guard().settleAfterStart())
        assertEquals(FastPenGuard.State.Fine, guard().state(FastPenPath.Writing))
    }

    @Test
    fun aCallThatNeverCameBackTwiceIsACrash() {
        guard().markTrying(FastPenPath.Writing)
        // The process died here. A new process starts and settles.
        assertEquals(emptyList<FastPenPath>(), guard().settleAfterStart())
        assertTrue(guard().mayTry(FastPenPath.Writing))
        // It tries again and dies again.
        guard().markTrying(FastPenPath.Writing)
        assertEquals(listOf(FastPenPath.Writing), guard().settleAfterStart())
        assertFalse(guard().mayTry(FastPenPath.Writing))
    }

    @Test
    fun oneUnrelatedDeathDoesNotBanThePath() {
        guard().markTrying(FastPenPath.Writing)
        guard().settleAfterStart()
        // The next try came back, so the doubt is gone.
        guard().markTrying(FastPenPath.Writing)
        guard().markFine(FastPenPath.Writing)
        guard().markTrying(FastPenPath.Writing)
        assertEquals(emptyList<FastPenPath>(), guard().settleAfterStart())
        assertTrue(guard().mayTry(FastPenPath.Writing))
    }

    @Test
    fun oneCrashedPathDoesNotBlockTheOther() {
        crash(FastPenPath.Writing)
        assertTrue(guard().mayTry(FastPenPath.AutoDraw))
    }

    @Test
    fun aCrashStaysACrashAcrossStarts() {
        crash(FastPenPath.AutoDraw)
        assertEquals(emptyList<FastPenPath>(), guard().settleAfterStart())
        assertFalse(guard().mayTry(FastPenPath.AutoDraw))
    }

    @Test
    fun resetAllowsAnotherTry() {
        crash(FastPenPath.Writing)
        guard().reset(FastPenPath.Writing)
        assertTrue(guard().mayTry(FastPenPath.Writing))
    }

    @Test
    fun aDamagedFileCountsAsUntried() {
        val folder = temp.root.resolve("eink").apply { mkdirs() }
        folder.resolve("fastpen-writing.state").writeText("???")
        assertEquals(FastPenGuard.State.Untried, guard().state(FastPenPath.Writing))
    }
}

package com.garfiec.librechat.core.model.steer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The second line of defence against duplicated quote chips. `SteeringDelegate` also strips the
 * excerpts off a steer's spec once they are restored, so the two are independently sufficient —
 * which is exactly why this one is pinned on its own rather than only through the composition.
 */
class RestagedQuotesTest {

    @Test
    fun anExcerptAlreadyStagedIsNotAddedAgain() {
        val staged = listOf("kept")
        assertSame(staged, mergeRestagedQuotes(staged, listOf("kept")))
        assertEquals(listOf("kept", "new"), mergeRestagedQuotes(staged, listOf("kept", "new")))
    }

    @Test
    fun theRestoredExcerptsAreAppendedInOrderAfterWhatIsStaged() {
        assertEquals(
            listOf("typed", "restored-a", "restored-b"),
            mergeRestagedQuotes(listOf("typed"), listOf("restored-a", "restored-b")),
        )
    }

    @Test
    fun repeatsWithinOneRestoreCollapse() {
        assertEquals(listOf("once"), mergeRestagedQuotes(emptyList(), listOf("once", "once")))
    }

    @Test
    fun theCapIsRespectedWithStagedChipsWinning() {
        // A restored tail that could not ride the next send is dropped rather than shown as a chip
        // the submission would silently discard.
        val full = (1..MAX_QUOTE_COUNT).map { "q$it" }
        assertSame(full, mergeRestagedQuotes(full, listOf("overflow")))

        val nearlyFull = (1 until MAX_QUOTE_COUNT).map { "q$it" }
        assertEquals(
            nearlyFull + "first-restored",
            mergeRestagedQuotes(nearlyFull, listOf("first-restored", "dropped")),
        )
    }

    @Test
    fun nothingToRestoreLeavesTheListIdentical() {
        val staged = listOf("kept")
        assertSame(staged, mergeRestagedQuotes(staged, emptyList()))
    }
}

package com.garfiec.librechat.shared.navigation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermsRequirementStateHolderTest {

    @Test
    fun aRequirementRaisedForTheActiveAccountHoldsUntilCleared() {
        val holder = TermsRequirementStateHolder { "acct-a" }
        holder.raise("acct-a")
        assertTrue(holder.required.value)
        holder.clear()
        assertFalse(holder.required.value)
    }

    @Test
    fun aCheckThatStartedBeforeASwitchDoesNotRaiseForTheIncomingAccount() {
        var active = "acct-a"
        val holder = TermsRequirementStateHolder { active }
        active = "acct-b"
        holder.raise("acct-a")
        assertFalse(holder.required.value)
    }

    @Test
    fun theTermsWaitForTheSignInFlowToEnd() {
        assertFalse(shouldShow(required = true, loggedIn = true, inAuthFlow = true, termsOpen = false))
        assertTrue(shouldShow(required = true, loggedIn = true, inAuthFlow = false, termsOpen = false))
    }

    @Test
    fun theTermsNeverOpenSignedOutTwiceOrWhenNotRequired() {
        assertFalse(shouldShow(required = true, loggedIn = false, inAuthFlow = false, termsOpen = false))
        assertFalse(shouldShow(required = true, loggedIn = true, inAuthFlow = false, termsOpen = true))
        assertFalse(shouldShow(required = false, loggedIn = true, inAuthFlow = false, termsOpen = false))
    }

    @Test
    fun aDeclineRevokesThenEndsTheSessionAndClearsTheRequirement() = runTest {
        val holder = TermsRequirementStateHolder { "acct-a" }
        holder.raise("acct-a")
        val calls = mutableListOf<String>()

        holder.decline(revoke = { calls += "revoke" }, endSession = { calls += "end" })

        assertEquals(listOf("revoke", "end"), calls)
        assertFalse(holder.required.value)
        assertFalse(holder.declining.value)
    }

    @Test
    fun aSecondDeclineWhileTheRevokeIsInFlightDoesNothing() = runTest {
        val holder = TermsRequirementStateHolder { "acct-a" }
        val revokeGate = CompletableDeferred<Unit>()
        var revokes = 0
        var ends = 0
        val decline: suspend () -> Unit = {
            holder.decline(revoke = { revokes++; revokeGate.await() }, endSession = { ends++ })
        }

        val first = launch(start = CoroutineStart.UNDISPATCHED) { decline() }
        assertTrue(holder.declining.value)
        // A double tap, and a back press, while the revoke call is still out.
        launch(start = CoroutineStart.UNDISPATCHED) { decline() }.join()
        launch(start = CoroutineStart.UNDISPATCHED) { decline() }.join()
        revokeGate.complete(Unit)
        first.join()

        assertEquals(1, revokes)
        assertEquals(1, ends)
        assertFalse(holder.declining.value)
    }

    @Test
    fun aFailedDeclineLeavesTheNextTermsScreenEnabled() = runTest {
        val holder = TermsRequirementStateHolder { "acct-a" }

        assertFailsWith<IllegalStateException> {
            holder.decline(revoke = { error("network") }, endSession = {})
        }

        assertFalse(holder.declining.value)
        var ends = 0
        holder.decline(revoke = {}, endSession = { ends++ })
        assertEquals(1, ends)
    }

    private fun shouldShow(required: Boolean, loggedIn: Boolean, inAuthFlow: Boolean, termsOpen: Boolean) =
        TermsRequirementStateHolder.shouldShow(required, loggedIn, inAuthFlow, termsOpen)
}

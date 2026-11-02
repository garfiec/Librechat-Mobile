package com.garfiec.librechat.shared.navigation

import kotlin.test.Test
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

    private fun shouldShow(required: Boolean, loggedIn: Boolean, inAuthFlow: Boolean, termsOpen: Boolean) =
        TermsRequirementStateHolder.shouldShow(required, loggedIn, inAuthFlow, termsOpen)
}

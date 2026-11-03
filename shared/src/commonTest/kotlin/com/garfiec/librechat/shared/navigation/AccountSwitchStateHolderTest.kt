package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.feature.conversations.drawer.AccountUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitchStateHolderTest {

    // B is parked at sign-in for required 2FA setup; A is still signed in.
    private val parked = account("b", isActive = true, needsSignIn = true, needsTwoFactorSetup = true)
    private val live = account("a")
    private val expired = account("c", needsSignIn = true)

    private class Fixture(accounts: List<AccountUiModel>, loggedIn: Boolean) {
        val accounts = MutableStateFlow(accounts)
        val loggedIn = MutableStateFlow(loggedIn)
        var switchSucceeds = true
        val switched = mutableListOf<String>()
        var refusals = 0
        var entered = 0
    }

    private fun TestScope.holder(
        accounts: List<AccountUiModel>,
        loggedIn: Boolean,
    ): Pair<AccountSwitchStateHolder, Fixture> {
        val fixture = Fixture(accounts, loggedIn)
        val holder = AccountSwitchStateHolder(
            scope = backgroundScope,
            accounts = fixture.accounts,
            isLoggedIn = fixture.loggedIn,
            isLoggedInNow = { fixture.loggedIn.value },
            switchAccount = { id ->
                fixture.switched += id
                fixture.switchSucceeds
            },
            onSwitchRefused = { fixture.refusals++ },
            enterSwitchedAccount = {
                fixture.entered++
                fixture.loggedIn.value = true
            },
        )
        // The sign-in screen's subscription.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.signInEscape.collect {} }
        runCurrent()
        return holder to fixture
    }

    @Test
    fun signedOutWithAnotherLiveAccountOffersIt() = runTest {
        val (holder, _) = holder(listOf(parked, live, expired), loggedIn = false)

        assertEquals(listOf(live), holder.signInEscape.value)
    }

    @Test
    fun signedOutWithNoOtherLiveAccountOffersNothing() = runTest {
        val (holder, _) = holder(listOf(parked, expired), loggedIn = false)

        assertTrue(holder.signInEscape.value.isEmpty())
    }

    @Test
    fun signedInOffersNothing() = runTest {
        // Add-account runs signed in, with the live account active and others live too.
        val (holder, _) = holder(listOf(live.copy(isActive = true), account("d")), loggedIn = true)

        assertTrue(holder.signInEscape.value.isEmpty())
    }

    @Test
    fun anotherAccountExpiringWithdrawsTheOffer() = runTest {
        val (holder, fixture) = holder(listOf(parked, live), loggedIn = false)

        fixture.accounts.value = listOf(parked, live.copy(needsSignIn = true))
        runCurrent()

        assertTrue(holder.signInEscape.value.isEmpty())
    }

    @Test
    fun choosingAnAccountFromSignInSwitchesAndEntersTheApp() = runTest {
        val (holder, fixture) = holder(listOf(parked, live), loggedIn = false)

        holder.switchTo("a")
        runCurrent()

        assertEquals(listOf("a"), fixture.switched)
        assertEquals(1, fixture.entered)
        assertTrue(holder.signInEscape.value.isEmpty(), "signed in now, so the offer is gone")
    }

    @Test
    fun aRefusedSwitchStaysAtSignInAndRereadsTheAccounts() = runTest {
        val (holder, fixture) = holder(listOf(parked, live), loggedIn = false)
        fixture.switchSucceeds = false

        holder.switchTo("a")

        assertEquals(1, fixture.refusals)
        assertEquals(0, fixture.entered)
    }

    @Test
    fun aSwitchWhileSignedInDoesNotReenter() = runTest {
        // The drawer's switch: the account flip alone carries it.
        val (holder, fixture) = holder(listOf(live.copy(isActive = true), account("d")), loggedIn = true)

        holder.switchTo("d")

        assertEquals(listOf("d"), fixture.switched)
        assertEquals(0, fixture.entered)
    }

    private fun account(
        id: String,
        isActive: Boolean = false,
        needsSignIn: Boolean = false,
        needsTwoFactorSetup: Boolean = false,
    ) = AccountUiModel(
        accountId = id,
        displayLabel = id,
        serverHost = "chat.example",
        avatarUrl = null,
        isActive = isActive,
        needsSignIn = needsSignIn,
        needsTwoFactorSetup = needsTwoFactorSetup,
    )
}

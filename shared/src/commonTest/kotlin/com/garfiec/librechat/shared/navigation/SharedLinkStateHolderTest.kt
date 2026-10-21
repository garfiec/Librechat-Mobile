package com.garfiec.librechat.shared.navigation

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.data.datastore.AccountEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SharedLinkStateHolderTest {

    private val work = AccountEntry("work", "https://chat.work.example", "Ada", lastActiveAt = 2)
    private val home = AccountEntry("home", "https://home.example", "ada@home", lastActiveAt = 1)
    private val homeLink = "https://home.example/share/abc"

    private class Fixture(initial: AccountState) {
        val accounts = InMemoryActiveAccountProvider(initial)
        var loggedIn = true
        var live = mutableSetOf("work", "home")
        var switchSucceeds = true
        val switched = mutableListOf<String>()
        var refusals = 0
        val browser = mutableListOf<String>()
    }

    private fun TestScope.holder(
        initial: AccountState = AccountState.Resolved(AccountId("work")),
    ): Pair<SharedLinkStateHolder, Fixture> {
        val fixture = Fixture(initial)
        val holder = SharedLinkStateHolder(
            scope = backgroundScope,
            activeAccountProvider = fixture.accounts,
            isLoggedIn = { fixture.loggedIn },
            rosterEntries = { listOf(work, home) },
            hasSession = { it in fixture.live },
            switchAccount = { id ->
                fixture.switched += id
                fixture.switchSucceeds
            },
            onSwitchRefused = { fixture.refusals++ },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            holder.browserLinks.collect { fixture.browser += it }
        }
        return holder to fixture
    }

    private suspend fun SharedLinkStateHolder.offerFor(url: String): SharedLinkRoute.OfferSwitch {
        val route = route(url)
        assertTrue(route is SharedLinkRoute.OfferSwitch, "expected an offer, got $route")
        offerSwitch(route)
        return route
    }

    @Test
    fun signedOutNothingIsOurs() = runTest {
        val (holder, fixture) = holder()
        fixture.loggedIn = false

        assertEquals(SharedLinkRoute.NotOurs, holder.route("https://chat.work.example/share/abc"))
    }

    @Test
    fun aLinkOnTheActiveServerOpens() = runTest {
        val (holder, _) = holder()

        assertEquals(SharedLinkRoute.Open("abc"), holder.route("https://chat.work.example/share/abc"))
    }

    @Test
    fun anAccountWithoutASessionIsNeverOffered() = runTest {
        val (holder, fixture) = holder()
        fixture.live.remove("home")

        assertEquals(SharedLinkRoute.NotOurs, holder.route(homeLink))
    }

    @Test
    fun anActiveAccountThatNeverResolvesGivesUpAfterTheTimeout() = runTest {
        val (holder, _) = holder(initial = AccountState.Warming)
        var route: SharedLinkRoute? = null
        backgroundScope.launch { route = holder.route("https://chat.work.example/share/abc") }

        advanceTimeBy(SharedLinkStateHolder.ACCOUNT_RESOLVE_TIMEOUT_MS - 1)
        runCurrent()
        assertNull(route)
        advanceTimeBy(2)
        runCurrent()

        assertEquals(SharedLinkRoute.NotOurs, route)
    }

    @Test
    fun anActiveAccountThatResolvesInTimeIsNotOfferedToItself() = runTest {
        val (holder, fixture) = holder(initial = AccountState.Warming)
        var route: SharedLinkRoute? = null
        backgroundScope.launch { route = holder.route("https://chat.work.example/share/abc") }
        runCurrent()

        fixture.accounts.set(AccountId("work"))
        runCurrent()

        assertEquals(SharedLinkRoute.Open("abc"), route)
    }

    @Test
    fun anAcceptedSwitchOpensTheLinkOnceForThatAccount() = runTest {
        val (holder, fixture) = holder()
        holder.offerFor(homeLink)

        holder.acceptSwitch()
        runCurrent()

        assertNull(holder.offer.value)
        assertEquals(listOf("home"), fixture.switched)
        assertEquals("abc", holder.takePendingSharedChat("home"))
        assertNull(holder.takePendingSharedChat("home"))
        assertTrue(fixture.browser.isEmpty())
    }

    @Test
    fun aFlipToAnotherAccountDropsThePendingLink() = runTest {
        val (holder, _) = holder()
        holder.offerFor(homeLink)
        holder.acceptSwitch()
        runCurrent()

        assertNull(holder.takePendingSharedChat("work"))
        assertNull(holder.takePendingSharedChat("home"))
    }

    @Test
    fun aFlipThatLandsSignedOutDropsThePendingLink() = runTest {
        val (holder, _) = holder()
        holder.offerFor(homeLink)
        holder.acceptSwitch()
        runCurrent()

        // The flip landed on an account whose session had just gone; the shell takes it anyway.
        assertNull(holder.takePendingSharedChat(null))
        // A later, deliberate switch to that account must not reopen the old link.
        assertNull(holder.takePendingSharedChat("home"))
    }

    @Test
    fun aRefusedSwitchSendsTheLinkToTheBrowserAndClearsIt() = runTest {
        val (holder, fixture) = holder()
        fixture.switchSucceeds = false
        holder.offerFor(homeLink)

        holder.acceptSwitch()
        runCurrent()

        assertEquals(listOf(homeLink), fixture.browser)
        assertEquals(1, fixture.refusals)
        assertNull(holder.takePendingSharedChat("home"))
    }

    @Test
    fun declinedOffersSendNothing() = runTest {
        val (holder, fixture) = holder()
        holder.offerFor(homeLink)

        holder.dismissOffer()
        holder.acceptSwitch()
        runCurrent()

        assertNull(holder.offer.value)
        assertTrue(fixture.switched.isEmpty())
        assertTrue(fixture.browser.isEmpty())
    }
}

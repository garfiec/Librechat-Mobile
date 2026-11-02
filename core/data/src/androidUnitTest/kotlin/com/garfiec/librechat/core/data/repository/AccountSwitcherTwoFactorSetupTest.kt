package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountState.Resolved
import com.garfiec.librechat.core.common.identity.deriveAccountId
import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** [AccountSwitcher.awaitingTwoFactorSetupOn]: which accounts the sign-in screen names as owing 2FA setup. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitcherTwoFactorSetupTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    private val serverA = "https://a.example.com"
    private val serverB = "https://b.example.com"
    private val enrolling = deriveAccountId(deriveServerId(serverA), "user-1")
    private val expiredOnly = deriveAccountId(deriveServerId(serverA), "user-2")
    private val otherServer = deriveAccountId(deriveServerId(serverB), "user-3")

    private fun entry(id: String, serverUrl: String, label: String) =
        AccountEntry(accountId = id, serverUrl = serverUrl, displayLabel = label, lastActiveAt = 0L)

    @Test
    fun `names only the signed-out accounts on that server that owe 2FA setup`() = runTest(testDispatcher) {
        val h = SwitcherHarness(tmpFolder.root, testDispatcher, json, Resolved(otherServer))
        h.roster.upsertAndActivate(entry(enrolling.value, serverA, "one@a"))
        h.roster.upsertAndActivate(entry(expiredOnly.value, serverA, "two@a"))
        h.roster.upsertAndActivate(entry(otherServer.value, serverB, "three@b"))
        h.tokenManager.expiredAccounts += listOf(enrolling.value, expiredOnly.value, otherServer.value)
        h.tokenManager.twoFactorSetupAccounts += listOf(enrolling.value, otherServer.value)

        // The address as typed on the server screen: a trailing slash and a pasted web route.
        val named = h.switcher.awaitingTwoFactorSetupOn("https://a.example.com/login")

        assertThat(named.map { it.displayLabel }).containsExactly("one@a")
        assertThat(h.switcher.awaitingTwoFactorSetupOn("")).isEmpty()
    }

    @Test
    fun `an account that signed back in is not named`() = runTest(testDispatcher) {
        val h = SwitcherHarness(tmpFolder.root, testDispatcher, json, Resolved(otherServer))
        h.roster.upsertAndActivate(entry(enrolling.value, serverA, "one@a"))
        // A stale marker with a live session: only an empty slot means it is waiting on setup.
        h.tokenManager.twoFactorSetupAccounts += enrolling.value

        assertThat(h.switcher.awaitingTwoFactorSetupOn(serverA)).isEmpty()
    }
}

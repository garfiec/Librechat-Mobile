package com.garfiec.librechat.core.data.repository

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.AccountState.Resolved
import com.garfiec.librechat.core.common.identity.deriveAccountId
import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.garfiec.librechat.core.network.client.SessionEndReason
import com.google.common.truth.Truth.assertThat
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Behavior tests for [AccountSwitcher.remove]: full teardown of one account (roster + tokens +
 * scoped prefs + Room purge), the flip-away-first ordering when the removed account is active
 * (switch to the most-recently-active survivor, or logout-shaped teardown + session-expired signal
 * when it was the last), and the no-op guard for unknown accounts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitcherRemoveTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    private val serverA = "https://a.example.com"
    private val serverB = "https://b.example.com"
    private val accountA = deriveAccountId(deriveServerId(serverA), "user-a")
    private val accountB = deriveAccountId(deriveServerId(serverB), "user-b")

    private fun entry(accountId: AccountId, serverUrl: String, lastActiveAt: Long) = AccountEntry(
        accountId = accountId.value,
        serverUrl = serverUrl,
        displayLabel = accountId.value,
        avatarUrl = null,
        lastActiveAt = lastActiveAt,
    )

    private fun harness(initialState: AccountState = Resolved(accountA)) =
        SwitcherHarness(tmpFolder.root, testDispatcher, json, initialState)

    private fun scopedPrefKey(accountId: AccountId) =
        stringPreferencesKey("acct:${accountId.value}:last_used_model")

    @Test
    fun `removing a non-active account purges its slots and leaves the active one untouched`() =
        runTest(testDispatcher) {
            val h = harness()
            h.roster.upsertAndActivate(entry(accountB, serverB, lastActiveAt = 1L))
            h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 2L))
            h.serverDataStore.setServerUrl(serverA)
            h.dataStore.edit { prefs ->
                prefs[scopedPrefKey(accountA)] = "model-a"
                prefs[scopedPrefKey(accountB)] = "model-b"
            }

            h.switcher.remove(accountB.value)

            val snapshot = h.roster.snapshot()
            assertThat(snapshot.entries.map { it.accountId }).containsExactly(accountA.value)
            assertThat(snapshot.activeId).isEqualTo(accountA.value)
            assertThat(h.tokenManager.removedAccounts).containsExactly(accountB.value)
            coVerify(exactly = 1) { h.dataPurger.purge(accountB) }
            val prefs = h.dataStore.data.first()
            assertThat(prefs[scopedPrefKey(accountB)]).isNull()
            assertThat(prefs[scopedPrefKey(accountA)]).isEqualTo("model-a")
            // The active account never flipped, so no switch-cache clear and no expiry signal.
            assertThat(h.provider.state.value).isEqualTo(Resolved(accountA))
            assertThat(h.switchCacheCleaner.clearCount).isEqualTo(0)
            assertThat(h.tokenManager.expiredEmissions).isEmpty()
        }

    @Test
    fun `removing the active account switches to the most recently active survivor first`() =
        runTest(testDispatcher) {
            val h = harness()
            val accountC = deriveAccountId(deriveServerId(serverB), "user-c")
            h.roster.upsertAndActivate(entry(accountC, serverB, lastActiveAt = 5L))
            h.roster.upsertAndActivate(entry(accountB, serverB, lastActiveAt = 10L))
            h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 20L))
            h.serverDataStore.setServerUrl(serverA)

            h.switcher.remove(accountA.value)

            // B (lastActiveAt=10) outranks C (5): full switch to B before A's teardown.
            assertThat(h.serverDataStore.getBaseUrl()).isEqualTo(serverB)
            assertThat(h.tokenManager.selections).containsExactly(accountB.value)
            assertThat(h.provider.state.value).isEqualTo(Resolved(accountB))
            assertThat(h.switchCacheCleaner.clearCount).isEqualTo(1)
            val snapshot = h.roster.snapshot()
            assertThat(snapshot.activeId).isEqualTo(accountB.value)
            assertThat(snapshot.entries.map { it.accountId })
                .containsExactly(accountB.value, accountC.value)
            assertThat(h.tokenManager.removedAccounts).containsExactly(accountA.value)
            coVerify(exactly = 1) { h.dataPurger.purge(accountA) }
            assertThat(h.tokenManager.expiredEmissions).isEmpty()
        }

    @Test
    fun `removing the active account skips a survivor whose session expired`() =
        runTest(testDispatcher) {
            val h = harness()
            val accountC = deriveAccountId(deriveServerId(serverB), "user-c")
            h.roster.upsertAndActivate(entry(accountC, serverB, lastActiveAt = 5L))
            h.roster.upsertAndActivate(entry(accountB, serverB, lastActiveAt = 10L))
            h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 20L))
            h.tokenManager.expiredAccounts += accountB.value

            h.switcher.remove(accountA.value)

            // B is the more recent, but its slot is empty: switching to it lands on a screen of 401s.
            assertThat(h.provider.state.value).isEqualTo(Resolved(accountC))
            assertThat(h.tokenManager.selections).containsExactly(accountC.value)
            assertThat(h.tokenManager.expiredEmissions).isEmpty()
        }

    @Test
    fun `removing the active account with only expired survivors signs out and keeps them listed`() =
        runTest(testDispatcher) {
            val h = harness()
            h.roster.upsertAndActivate(entry(accountB, serverB, lastActiveAt = 10L))
            h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 20L))
            h.tokenManager.expiredAccounts += accountB.value

            h.switcher.remove(accountA.value)

            assertThat(h.provider.state.value).isEqualTo(Resolved(null))
            assertThat(h.tokenManager.selections).isEmpty()
            assertThat(h.tokenManager.expiredReasons).containsExactly(SessionEndReason.SIGNED_OUT)
            // B stays so it can be signed back in to; it just isn't made active.
            val snapshot = h.roster.snapshot()
            assertThat(snapshot.entries.map { it.accountId }).containsExactly(accountB.value)
            assertThat(snapshot.activeId).isNull()
        }

    @Test
    fun `failing over from an expired active account moves to the most recent live one`() =
        runTest(testDispatcher) {
            val h = harness()
            val accountC = deriveAccountId(deriveServerId(serverB), "user-c")
            h.roster.upsertAndActivate(entry(accountC, serverB, lastActiveAt = 5L))
            h.roster.upsertAndActivate(entry(accountB, serverB, lastActiveAt = 10L))
            h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 20L))
            h.tokenManager.expiredAccounts += listOf(accountA.value, accountB.value)

            val result = h.switcher.failOverFromActive()

            assertThat((result as FailoverResult.Switched).entry.accountId).isEqualTo(accountC.value)
            assertThat(h.provider.state.value).isEqualTo(Resolved(accountC))
            // The expired account is kept, not removed: the user signs back in to it.
            assertThat(h.roster.snapshot().entries.map { it.accountId }).contains(accountA.value)
            assertThat(h.tokenManager.removedAccounts).isEmpty()
        }

    @Test
    fun `failing over with no live account left does nothing`() = runTest(testDispatcher) {
        val h = harness()
        h.roster.upsertAndActivate(entry(accountB, serverB, lastActiveAt = 10L))
        h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 20L))
        h.tokenManager.expiredAccounts += listOf(accountA.value, accountB.value)

        assertThat(h.switcher.failOverFromActive()).isEqualTo(FailoverResult.NoLiveAccount)
        assertThat(h.provider.state.value).isEqualTo(Resolved(accountA))
        assertThat(h.tokenManager.selections).isEmpty()
    }

    @Test
    fun `failing over while the active account still holds a session does nothing`() =
        runTest(testDispatcher) {
            val h = harness()
            h.roster.upsertAndActivate(entry(accountB, serverB, lastActiveAt = 10L))
            h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 20L))

            // An expiry reported for an account that has since been switched away from.
            assertThat(h.switcher.failOverFromActive()).isEqualTo(FailoverResult.ActiveStillLive)
            assertThat(h.provider.state.value).isEqualTo(Resolved(accountA))
            assertThat(h.tokenManager.selections).isEmpty()
        }

    @Test
    fun `switching to an account whose session expired is refused`() = runTest(testDispatcher) {
        val h = harness()
        h.roster.upsertAndActivate(entry(accountB, serverB, lastActiveAt = 10L))
        h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 20L))
        h.tokenManager.expiredAccounts += accountB.value

        assertThat(h.switcher.switch(accountB.value)).isFalse()
        assertThat(h.provider.state.value).isEqualTo(Resolved(accountA))
        assertThat(h.tokenManager.selections).isEmpty()
    }

    @Test
    fun `removing the last account tears down to logged-out and signals session expiry`() =
        runTest(testDispatcher) {
            val h = harness()
            h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 1L))
            h.serverDataStore.setServerUrl(serverA)

            h.switcher.remove(accountA.value)

            assertThat(h.provider.state.value).isEqualTo(Resolved(null))
            assertThat(h.tokenManager.clearedActive).isTrue()
            assertThat(h.switchCacheCleaner.clearCount).isEqualTo(1)
            assertThat(h.sessionCacheCleaner.fileClearCount).isEqualTo(1)
            assertThat(h.roster.snapshot().entries).isEmpty()
            assertThat(h.tokenManager.expiredEmissions).containsExactly(null)
            // Reported as a deliberate sign-out, not an expiry: the user asked for this, so the UI
            // must not tell them their session ran out.
            assertThat(h.tokenManager.expiredReasons).containsExactly(SessionEndReason.SIGNED_OUT)
            coVerify(exactly = 1) { h.dataPurger.purge(accountA) }
            // The URL is deliberately retained (logout parity) so the login screen comes back prefilled.
            assertThat(h.serverDataStore.getBaseUrl()).isEqualTo(serverA)
        }

    @Test
    fun `removing an unknown account is a no-op`() = runTest(testDispatcher) {
        val h = harness()
        h.roster.upsertAndActivate(entry(accountA, serverA, lastActiveAt = 1L))

        h.switcher.remove("srv-x:nobody")

        assertThat(h.roster.snapshot().entries).hasSize(1)
        assertThat(h.tokenManager.removedAccounts).isEmpty()
        coVerify(exactly = 0) { h.dataPurger.purge(any()) }
        assertThat(h.provider.state.value).isEqualTo(Resolved(accountA))
    }
}

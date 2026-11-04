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

/**
 * The switch clear must be handed every server whose auth cookies have to die with the transition —
 * the outgoing server, the incoming one, and on a remove-last teardown the whole roster. URL-scoped
 * clearing is what spares the identity provider's session cookie, which is what keeps an OIDC
 * re-sign-in password-free.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSwitcherCookieScopeTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }
    private val serverA = "https://a.example.com"
    private val serverB = "https://b.example.com"
    private val accountA = deriveAccountId(deriveServerId(serverA), "user-a")
    private val accountB = deriveAccountId(deriveServerId(serverB), "user-b")

    private fun entry(accountId: String, serverUrl: String, lastActiveAt: Long) = AccountEntry(
        accountId = accountId,
        serverUrl = serverUrl,
        displayLabel = accountId,
        avatarUrl = null,
        lastActiveAt = lastActiveAt,
    )

    private fun harness() = SwitcherHarness(tmpFolder.root, testDispatcher, json, Resolved(accountA))

    @Test
    fun `prepareForSignIn clears the signed-out server and the new one`() = runTest(testDispatcher) {
        val h = harness()
        h.serverDataStore.setServerUrl(serverA)
        h.roster.upsertAndActivate(entry(accountA.value, serverA, lastActiveAt = 1L))

        h.switcher.prepareForSignIn(serverB)

        assertThat(h.switchCacheCleaner.clearedServerUrls).containsExactly(setOf(serverA, serverB))
    }

    @Test
    fun `a switch clears both the outgoing and the incoming server`() = runTest(testDispatcher) {
        val h = harness()
        h.roster.upsertAndActivate(entry(accountB.value, serverB, lastActiveAt = 1L))
        h.roster.upsertAndActivate(entry(accountA.value, serverA, lastActiveAt = 2L))
        h.serverDataStore.setServerUrl(serverA)

        h.switcher.switch(accountB.value)

        assertThat(h.switchCacheCleaner.clearedServerUrls).containsExactly(setOf(serverA, serverB))
    }

    @Test
    fun `the remove-last teardown clears every roster server`() = runTest(testDispatcher) {
        val h = harness()
        h.roster.upsertAndActivate(entry(accountB.value, serverB, lastActiveAt = 1L))
        h.roster.upsertAndActivate(entry(accountA.value, serverA, lastActiveAt = 2L))
        h.serverDataStore.setServerUrl(serverA)
        // B holds no live session, so removing the active A tears down to logged-out.
        h.tokenManager.expiredAccounts += accountB.value

        h.switcher.remove(accountA.value)

        assertThat(h.switchCacheCleaner.clearedServerUrls).containsExactly(setOf(serverA, serverB))
    }
}

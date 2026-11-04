package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.network.client.AuthGate
import com.garfiec.librechat.core.network.client.RefreshResult
import com.garfiec.librechat.core.network.client.SessionEndReason
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A refresh of an account that must set up 2FA (v0.8.9-rc1) answers **200** with no token, having
 * deleted the session and cleared the `refreshToken` cookie. Read as a malformed 2xx it would be
 * retried under the flight lock and the dead slot kept (refresh limbo); read as an expiry it would fail over
 * to another account. It is neither: the slot goes and the shell is told to run setup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommonTokenDataStoreEnrollmentTest {

    private companion object {
        const val ACCOUNT = "acctA"
        const val OTHER = "acctB"
        const val ENROLLMENT_BODY =
            """{"code":"TWO_FACTOR_ENROLLMENT_REQUIRED","twoFAPending":true,"twoFASetupRequired":true,"tempToken":"setup-jwt"}"""
    }

    private var posts = 0

    private fun enrollmentEngine(): MockEngine = unconfinedMockEngine {
        posts++
        respond(
            ENROLLMENT_BODY,
            HttpStatusCode.OK,
            headersOf(
                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                HttpHeaders.SetCookie to listOf("refreshToken=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT"),
            ),
        )
    }

    private fun seeded(engine: MockEngine, active: String = ACCOUNT): FakeTokenStore = FakeTokenStore(
        refreshClientOf(engine),
        seed = mapOf(
            ACTIVE_ACCOUNT_KEY to active,
            accessKeyOf(ACCOUNT) to "A0",
            refreshKeyOf(ACCOUNT) to "R0",
            accessKeyOf(OTHER) to "B0",
            refreshKeyOf(OTHER) to "RB",
        ),
    )

    @Test
    fun `an enrollment 200 ends the ladder in one POST, drops the slot and raises the setup gate`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(enrollmentEngine())

            assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.Transient)

            assertThat(posts).isEqualTo(1)
            assertThat(store.persistedAccess(ACCOUNT)).isNull()
            assertThat(store.persistedRefresh(ACCOUNT)).isNull()
            assertThat(store.authGate.value).isEqualTo(AuthGate.TwoFactorSetupRequired(ACCOUNT, "setup-jwt"))
            // The roster pointer stays: no failover moved the user off this account.
            assertThat(store.store[ACTIVE_ACCOUNT_KEY]).isEqualTo(ACCOUNT)
            // The other account's session is untouched.
            assertThat(store.persistedRefresh(OTHER)).isEqualTo("RB")
        }

    @Test
    fun `an enrollment 200 never reports an expiry, even from the requests still in flight`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(enrollmentEngine())
            val reported = mutableListOf<SessionEndReason>()
            val collector = launch { store.sessionExpiredFlow.toList(reported) }

            store.refreshAccessToken()
            // A straggler now finds an empty slot and settles HardExpired; its report is latched.
            assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.HardExpired)
            store.emitSessionExpired(ACCOUNT)

            assertThat(reported).isEmpty()
            collector.cancel()
        }

    @Test
    fun `a switched-away account's enrollment drops its slot without raising a gate`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(enrollmentEngine(), active = OTHER)

            store.refreshAccessTokenFor(ACCOUNT, baseUrl = "https://a.example", usedAccessToken = null)

            assertThat(store.persistedRefresh(ACCOUNT)).isNull()
            assertThat(store.authGate.value).isNull()
            assertThat(store.persistedRefresh(OTHER)).isEqualTo("RB")
        }

    @Test
    fun `a switched-away account's enrollment is recorded with the account, and the active one is untouched`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(enrollmentEngine(), active = OTHER)
            val reported = mutableListOf<SessionEndReason>()
            val collector = launch { store.sessionExpiredFlow.toList(reported) }

            store.refreshAccessTokenFor(ACCOUNT, baseUrl = "https://a.example", usedAccessToken = null)

            assertThat(store.isTwoFactorSetupRequired(ACCOUNT)).isTrue()
            assertThat(store.isTwoFactorSetupRequired(OTHER)).isFalse()
            // Persisted, not in memory only: the marker is in the backing store.
            assertThat(store.store.keys).contains("acct:$ACCOUNT:two_factor_setup_required")
            // No failover, no expiry report, no move of the active pointer.
            assertThat(store.store[ACTIVE_ACCOUNT_KEY]).isEqualTo(OTHER)
            assertThat(store.getAccessTokenFor(OTHER)).isEqualTo("B0")
            assertThat(reported).isEmpty()
            collector.cancel()
        }

    @Test
    fun `signing back in to the account clears its setup marker`() = runTest(UnconfinedTestDispatcher()) {
        val store = seeded(enrollmentEngine(), active = OTHER)
        store.refreshAccessTokenFor(ACCOUNT, baseUrl = "https://a.example", usedAccessToken = null)

        store.setTokens("A1", "R1")
        store.onAccountResolved(ACCOUNT)

        assertThat(store.isTwoFactorSetupRequired(ACCOUNT)).isFalse()
    }

    /** The 403 path ends the session in the shell; the marker must survive a restart there too. */
    @Test
    fun `an enrollment 403 recorded by the shell persists the marker until the next sign-in`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(enrollmentEngine())

            store.endSessionForReauth(ACCOUNT, forTwoFactorSetup = true)

            assertThat(store.isTwoFactorSetupRequired(ACCOUNT)).isTrue()
            assertThat(store.store.keys).contains("acct:$ACCOUNT:two_factor_setup_required")
            assertThat(store.isTwoFactorSetupRequired(OTHER)).isFalse()

            store.setTokens("A1", "R1")
            store.onAccountResolved(ACCOUNT)
            assertThat(store.isTwoFactorSetupRequired(ACCOUNT)).isFalse()
        }

    /**
     * Record, restart, read: the marker is read back by a fresh store over the same storage, and the
     * teardown Connect on the server screen runs (`prepareForSignIn` → `onAccountCleared`) keeps it,
     * since the account stays in the roster still owing setup.
     */
    @Test
    fun `the setup marker survives a restart and the teardown of starting a new sign-in`() =
        runTest(UnconfinedTestDispatcher()) {
            val first = seeded(enrollmentEngine())
            first.endSessionForReauth(ACCOUNT, forTwoFactorSetup = true)

            val restarted = FakeTokenStore(refreshClientOf(enrollmentEngine()), seed = first.store)
            assertThat(restarted.isTwoFactorSetupRequired(ACCOUNT)).isTrue()
            assertThat(restarted.getAccessToken()).isNull()
            // The binding stays on the account, so cold start routes to its sign-in.
            assertThat(restarted.store[ACTIVE_ACCOUNT_KEY]).isEqualTo(ACCOUNT)

            restarted.onAccountCleared()
            assertThat(restarted.isTwoFactorSetupRequired(ACCOUNT)).isTrue()

            val restartedAgain = FakeTokenStore(refreshClientOf(enrollmentEngine()), seed = restarted.store)
            assertThat(restartedAgain.isTwoFactorSetupRequired(ACCOUNT)).isTrue()
            restartedAgain.removeAccount(ACCOUNT)
            assertThat(restartedAgain.isTwoFactorSetupRequired(ACCOUNT)).isFalse()
        }

    @Test
    fun `removing the account clears its setup marker`() = runTest(UnconfinedTestDispatcher()) {
        val store = seeded(enrollmentEngine(), active = OTHER)
        store.refreshAccessTokenFor(ACCOUNT, baseUrl = "https://a.example", usedAccessToken = null)

        store.removeAccount(ACCOUNT)

        assertThat(store.isTwoFactorSetupRequired(ACCOUNT)).isFalse()
    }

    @Test
    fun `a sign-in clears the gate and re-arms the expiry report`() = runTest(UnconfinedTestDispatcher()) {
        val store = seeded(enrollmentEngine())
        store.refreshAccessToken()

        store.setTokens("A1", "R1")
        store.onAccountResolved(ACCOUNT)
        val reported = mutableListOf<SessionEndReason>()
        val collector = launch { store.sessionExpiredFlow.toList(reported) }
        store.emitSessionExpired(ACCOUNT)

        assertThat(store.authGate.value).isNull()
        assertThat(reported).containsExactly(SessionEndReason.EXPIRED)
        collector.cancel()
    }

    @Test
    fun `a 200 with no token and no enrollment code stays a retryable malformed answer`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(unconfinedMockEngine { respond("{}", HttpStatusCode.OK, jsonResponseHeaders) })

            assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.Transient)

            assertThat(store.persistedRefresh(ACCOUNT)).isEqualTo("R0")
            assertThat(store.authGate.value).isNull()
        }

    /** A refresh token retired by enrollment or a password reset answers 401 and still settles. */
    @Test
    fun `a retired refresh token's 401 reaches re-auth`() = runTest(UnconfinedTestDispatcher()) {
        val store = seeded(
            unconfinedMockEngine {
                respond(
                    "Refresh token expired or not found for this user",
                    HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.SetCookie, "refreshToken=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT"),
                )
            },
        )

        assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.HardExpired)
        assertThat(store.persistedRefresh(ACCOUNT)).isNull()
    }

    /** The validator's 403 report: an expiry landing before the shell acts must not fail over. */
    @Test
    fun `a reported enrollment gate latches the expiry report at once`() = runTest(UnconfinedTestDispatcher()) {
        val store = seeded(enrollmentEngine())
        val reported = mutableListOf<SessionEndReason>()
        val collector = launch { store.sessionExpiredFlow.toList(reported) }

        store.reportAuthGate(AuthGate.TwoFactorSetupRequired(ACCOUNT, "t"))
        store.emitSessionExpired(ACCOUNT)

        assertThat(reported).isEmpty()
        collector.cancel()
    }

    @Test
    fun `reports for a switched-away account are ignored and terms never replaces setup`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(enrollmentEngine())

            store.reportAuthGate(AuthGate.TermsNotAccepted(OTHER))
            assertThat(store.authGate.value).isNull()

            val setup = AuthGate.TwoFactorSetupRequired(ACCOUNT, "t")
            store.reportAuthGate(setup)
            store.reportAuthGate(AuthGate.TermsNotAccepted(ACCOUNT))
            assertThat(store.authGate.value).isEqualTo(setup)

            store.consumeAuthGate(setup)
            assertThat(store.authGate.value).isNull()
        }
}

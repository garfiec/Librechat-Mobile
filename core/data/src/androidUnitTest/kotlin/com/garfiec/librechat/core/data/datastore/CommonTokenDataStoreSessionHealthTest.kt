package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.network.client.RefreshResult
import com.garfiec.librechat.core.network.client.SessionEndReason
import com.garfiec.librechat.core.network.client.SessionHealth
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * The exit from a kept-but-blocked session.
 *
 * A refresh 403 that LibreChat did not author keeps the session (#376). Kept, every request runs on
 * an expired bearer, the server answers `jwt expired`, and the stand-down re-arms for as long as the
 * block lasts. [SessionHealth] is the signal the shell shows a way out on; `endSessionForReauth` is
 * the way out.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class CommonTokenDataStoreSessionHealthTest {

    private companion object {
        const val ACCOUNT = "acctA"
        const val BLOCK_COOLDOWN_MS = 60_000L
    }

    private fun jwt(secondsFromNow: Long, id: String = "u1"): String {
        val exp = Clock.System.now().toEpochMilliseconds() / 1000 + secondsFromNow
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"HS256"}""".encodeToByteArray())
        val payload = encoder.encode("""{"id":"$id","exp":$exp}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    private fun seeded(engine: MockEngine, access: String = jwt(900)): FakeTokenStore = FakeTokenStore(
        refreshClientOf(engine),
        seed = mapOf(
            ACTIVE_ACCOUNT_KEY to ACCOUNT,
            accessKeyOf(ACCOUNT) to access,
            refreshKeyOf(ACCOUNT) to "R0",
        ),
    )

    private fun blockPage(): MockEngine = unconfinedMockEngine {
        respond("<html><body>Please use the Switchboard app</body></html>", HttpStatusCode.Forbidden)
    }

    @Test
    fun `a 403 that is not LibreChat's reports the session as blocked, keyed to its account`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(blockPage())
            assertThat(store.sessionHealth.value).isEqualTo(SessionHealth.Healthy)

            assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.Transient)

            assertThat(store.sessionHealth.value).isEqualTo(SessionHealth.RenewalBlocked(ACCOUNT))
        }

    /** LibreChat's own 403 is a dead credential and routes to re-auth itself; no banner over it. */
    @Test
    fun `LibreChat's own rejection does not report a block`() = runTest(UnconfinedTestDispatcher()) {
        val store = seeded(unconfinedMockEngine { respond("Invalid refresh token", HttpStatusCode.Forbidden) })

        assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.HardExpired)

        assertThat(store.sessionHealth.value).isEqualTo(SessionHealth.Healthy)
    }

    /**
     * The stand-down lapsing is not health: it means the client will POST again, not that the
     * server has started answering. Only an answer clears it.
     */
    @Test
    fun `the block outlives the stand-down until a refresh succeeds`() = runTest(UnconfinedTestDispatcher()) {
        var blocked = true
        val engine = unconfinedMockEngine {
            if (blocked) {
                respond("<html>denied</html>", HttpStatusCode.Forbidden)
            } else {
                respond(refreshResponseBody(jwt(900, id = "renewed")), HttpStatusCode.OK, jsonResponseHeaders)
            }
        }
        val store = seeded(engine)

        store.refreshAccessToken()
        store.clockOffsetMillis = BLOCK_COOLDOWN_MS + 1
        assertThat(store.sessionHealth.value).isEqualTo(SessionHealth.RenewalBlocked(ACCOUNT))

        // Still refused past the cooldown: still blocked.
        store.refreshAccessToken()
        assertThat(store.sessionHealth.value).isEqualTo(SessionHealth.RenewalBlocked(ACCOUNT))

        // The block lifts and the next refresh lands: healthy again, with no user action.
        blocked = false
        store.clockOffsetMillis = 2 * BLOCK_COOLDOWN_MS + 2
        assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.Refreshed)
        assertThat(store.sessionHealth.value).isEqualTo(SessionHealth.Healthy)
    }

    @Test
    fun `a sign-in clears the block`() = runTest(UnconfinedTestDispatcher()) {
        val store = seeded(blockPage())
        store.refreshAccessToken()
        assertThat(store.sessionHealth.value).isInstanceOf(SessionHealth.RenewalBlocked::class.java)

        store.setTokens(jwt(900), "R1")
        store.onAccountResolved(ACCOUNT)

        assertThat(store.sessionHealth.value).isEqualTo(SessionHealth.Healthy)
    }

    @Test
    fun `ending the session for re-auth drops the slot, clears the block and announces nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(blockPage())
            store.refreshAccessToken()
            val reported = mutableListOf<SessionEndReason>()
            val collector = launch { store.sessionExpiredFlow.toList(reported) }

            store.endSessionForReauth(ACCOUNT)

            // The slot is gone, so `isLoggedIn()` reads false and the next cold start goes to sign-in…
            assertThat(store.store[accessKeyOf(ACCOUNT)]).isNull()
            assertThat(store.store[refreshKeyOf(ACCOUNT)]).isNull()
            assertThat(store.getAccessTokenFor(ACCOUNT)).isNull()
            // …the roster pointer stays, so the account is still listed and re-loginable…
            assertThat(store.store[ACTIVE_ACCOUNT_KEY]).isEqualTo(ACCOUNT)
            // …the banner comes down…
            assertThat(store.sessionHealth.value).isEqualTo(SessionHealth.Healthy)
            // …and a straggler 401 on the dropped bearer cannot announce an expiry over this sign-out.
            store.emitSessionExpired(ACCOUNT)
            assertThat(reported).isEmpty()
            collector.cancel()
        }

    /** The latch is for the session the user ended; a later sign-in must report its own expiry. */
    @Test
    fun `a sign-in after ending the session re-arms the expiry report`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = seeded(blockPage())
            store.endSessionForReauth(ACCOUNT)
            store.setTokens(jwt(900), "R1")
            store.onAccountResolved(ACCOUNT)
            val reported = mutableListOf<SessionEndReason>()
            val collector = launch { store.sessionExpiredFlow.toList(reported) }

            store.emitSessionExpired(ACCOUNT)

            assertThat(reported).containsExactly(SessionEndReason.EXPIRED)
            collector.cancel()
        }
}

package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.logging.redact.LogRedactor
import com.garfiec.librechat.core.network.client.LibreChatHttpClient
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.network.client.SwitchGate
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * The number issue #376 is actually about: **total `POST /api/auth/refresh` per expiry, through the
 * real request pipeline.**
 *
 * `CommonTokenDataStoreRefreshBurstTest` verifies each wave in isolation by calling the store
 * directly. That is not enough on its own, because the burst is a *composition* fault — it exists
 * only because `SwitchBarrierPlugin` calls `ensureFreshAccessToken` once per request, the failed
 * proactive renewals then let every request go out on a stale bearer, and `AuthInterceptorPlugin`
 * turns each resulting 401 into its own ladder. Two things only this altitude can check:
 *
 * - the barrier really drives one proactive renewal **per request**, rather than the whole fan-out
 *   somehow sharing one (which would make the wave-1 assertions vacuous);
 * - the proactive slot and the reactive slot resolve to the **same** key end to end. If they did
 *   not, there would be two independent flight locks and two independent ladders, and every test in
 *   the direct-call suite would still pass.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class RefreshBurstThroughClientTest {

    private companion object {
        const val ACCOUNT = "acctA"
        const val FAN_OUT = 7

        /** LibreChat's own dead-credential answer: terminal, drops the slot. */
        const val LIBRECHAT_REJECTION = "Invalid refresh token"

        /** What a bouncer in front of the deployment answers. Not LibreChat's, so not terminal. */
        const val INTERMEDIARY_BLOCK = "<html><body><h1>Access denied</h1></body></html>"
    }

    private class FixedUrlProvider : ServerUrlProvider {
        override fun getBaseUrl(): String = TEST_SERVER
    }

    private fun jwt(secondsFromNow: Long, id: String = "u1"): String {
        val exp = Clock.System.now().toEpochMilliseconds() / 1000 + secondsFromNow
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"HS256"}""".encodeToByteArray())
        val payload = encoder.encode("""{"id":"$id","exp":$exp}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    /**
     * The whole production shape: a real [CommonTokenDataStore] behind a real [SwitchGate], wired
     * into a real [LibreChatHttpClient]. The API engine answers 401 (an expired bearer, exactly what
     * `requireJwtAuth` sends); the separate refresh engine answers LibreChat's own terminal 403.
     */
    /** The client under test plus the store behind it, so a test can assert on the slot as well. */
    private class Harness(val client: HttpClient, val store: FakeTokenStore)

    private fun scenario(
        refreshPosts: IntArray,
        apiCalls: IntArray,
        refreshBody: String = LIBRECHAT_REJECTION,
    ): Harness {
        val stale = jwt(-60)
        val store = FakeTokenStore(
            refreshClientOf(
                unconfinedMockEngine {
                    refreshPosts[0]++
                    respond(refreshBody, HttpStatusCode.Forbidden)
                },
            ),
            seed = mapOf(
                ACTIVE_ACCOUNT_KEY to ACCOUNT,
                accessKeyOf(ACCOUNT) to stale,
                refreshKeyOf(ACCOUNT) to "R0",
            ),
        )
        val gate = SwitchGate(
            activeAccountProvider = InMemoryActiveAccountProvider(
                AccountState.Resolved(AccountId(ACCOUNT)),
            ),
            serverUrlProvider = FixedUrlProvider(),
            tokenManager = store,
            accountReadyGate = null,
        )
        val client = LibreChatHttpClient.create(
            engineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
                override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                    MockEngine(
                        MockEngineConfig().apply(block).apply {
                            dispatcher = Dispatchers.Unconfined
                            addHandler {
                                apiCalls[0]++
                                respond("Unauthorized", HttpStatusCode.Unauthorized)
                            }
                        },
                    )
            },
            json = Json { ignoreUnknownKeys = true },
            tokenManager = store,
            serverUrlProvider = FixedUrlProvider(),
            redactor = LogRedactor(),
            switchGate = gate,
        )
        return Harness(client, store)
    }

    /**
     * One dead refresh token, a [FAN_OUT]-wide burst of requests, and the count that reaches the
     * refresh endpoint.
     *
     * Against the pre-fix source this was 10 — seven proactive POSTs (one per request, none
     * coalescing because a failed refresh leaves the access token untouched) plus a three-attempt
     * reactive ladder. Fifteen-plus in a few seconds is what got a real user's IP banned by CrowdSec.
     */
    @Test
    fun `one dead refresh token costs one refresh post for the whole fan-out`() =
        runTest(UnconfinedTestDispatcher()) {
            val refreshPosts = intArrayOf(0)
            val apiCalls = intArrayOf(0)
            val harness = scenario(refreshPosts, apiCalls)

            harness.client.use { client ->
                coroutineScope {
                    repeat(FAN_OUT) {
                        launch { runCatching { client.get("/api/convos") } }
                    }
                }
            }

            // The proactive suppression collapses the fan-out to one POST; the reactive ladder then
            // settles on its first terminal 403 and drops the slot, so the remaining 401s cost none.
            assertThat(refreshPosts[0]).isEqualTo(2)

            // Guards the premise: every request really did go out and really did 401, so the count
            // above is a suppressed burst and not a fan-out that never happened.
            assertThat(apiCalls[0]).isAtLeast(FAN_OUT)
        }

    /**
     * The same count, for a 403 the server did not author — where the session is deliberately kept.
     *
     * Keeping the slot is right (a network-layer block is not a dead credential) but it removes what
     * otherwise bounds the *reactive* burst: a terminal rejection empties the slot, so waiters 2..N
     * find nothing to send and return without POSTing. With the slot kept, nothing but the
     * endpoint-wide stand-down stops every 401 in the fan-out from spending its own POST, on every
     * fan-out, for as long as the block lasts — aimed at the auth endpoint of a deployment whose
     * bouncer is already blocking this client. Measured at 8 POSTs for a [FAN_OUT]-wide fan-out
     * without it.
     *
     * This is what a proxy rule scoped to the `/api/auth` prefix produces: the refresh POST is
     * blocked while data routes still reach LibreChat and 401 on the expired bearer. A bouncer that
     * blocks *everything* cannot exercise it — a 403 on a data route never enters the 401 ladder.
     */
    @Test
    fun `a blocked refresh route does not turn every 401 into its own post`() =
        runTest(UnconfinedTestDispatcher()) {
            val refreshPosts = intArrayOf(0)
            val apiCalls = intArrayOf(0)
            val harness = scenario(refreshPosts, apiCalls, refreshBody = INTERMEDIARY_BLOCK)

            harness.client.use { client ->
                coroutineScope {
                    repeat(FAN_OUT) {
                        launch { runCatching { client.get("/api/convos") } }
                    }
                }
            }

            // One proactive POST, and the reactive waiters stand down behind the same block rather
            // than each spending their own. Not asserted as terminal: the session must SURVIVE this.
            assertThat(refreshPosts[0]).isAtMost(2)
            assertThat(apiCalls[0]).isAtLeast(FAN_OUT)
            // The whole point of Transient — the credential is still there when the block lifts.
            assertThat(harness.store.store[refreshKeyOf(ACCOUNT)]).isEqualTo("R0")
        }
}

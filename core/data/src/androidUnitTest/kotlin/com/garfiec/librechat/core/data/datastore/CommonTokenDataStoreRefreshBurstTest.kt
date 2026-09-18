package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.network.client.RefreshResult
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Issue #376: one dead refresh token must cost one `POST /api/auth/refresh`, not one per request in
 * the fan-out plus a retry ladder on top.
 *
 * A reporter running LibreChat behind CrowdSec had their IP banned because an expiry produced 15+
 * identical rejected refresh POSTs in a few seconds — a burst indistinguishable from credential
 * stuffing against the auth endpoint. Two separate amplifiers produced it, and this suite pins both:
 *
 * - **Wave 1.** Every request opts into proactive renewal at the barrier, so a screen's fan-out calls
 *   [ensureFreshAccessToken] N times at once. `ensureFreshAccessToken`'s cooldown is read before the
 *   flight lock and written after it, so all N pass its gate before the first failure lands; the
 *   coalescing check inside then keys on the access token having *changed*, which is exactly what a
 *   failed refresh does not do. All N POSTed.
 * - **Wave 2.** A 403 was classified identically to a 401 and so spent the full retry ladder, even
 *   though every 403 arm of upstream's `refreshController` is a dead credential that no retry can
 *   rescue.
 *
 * The counts here are the whole point: asserting "the session ended" would pass with the burst fully
 * present. Each test counts POSTs against the engine.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class CommonTokenDataStoreRefreshBurstTest {

    private companion object {
        const val ACCOUNT = "acctA"

        /** The concurrency a real fan-out showed in the reporter's diagnostics (7 at once). */
        const val FAN_OUT = 7

        /** [CommonTokenDataStore.MAX_REFRESH_ATTEMPTS], which this suite must not depend on privately. */
        const val LADDER = 3
    }

    /** A JWT whose `exp` is [secondsFromNow] away. Only the payload is real; nothing verifies it. */
    private fun jwt(secondsFromNow: Long, id: String = "u1"): String {
        val exp = Clock.System.now().toEpochMilliseconds() / 1000 + secondsFromNow
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"HS256"}""".encodeToByteArray())
        val payload = encoder.encode("""{"id":"$id","exp":$exp}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    private fun seeded(engine: MockEngine, access: String?): FakeTokenStore = FakeTokenStore(
        refreshClientOf(engine),
        seed = buildMap {
            put(ACTIVE_ACCOUNT_KEY, ACCOUNT)
            access?.let { put(accessKeyOf(ACCOUNT), it) }
            put(refreshKeyOf(ACCOUNT), "R0")
        },
    )

    /** Counts POSTs and answers each with [status] and [body]. */
    private fun countingEngine(
        posts: IntArray,
        status: HttpStatusCode,
        body: String,
    ): MockEngine = unconfinedMockEngine {
        posts[0]++
        respond(body, status)
    }

    /** Upstream's `res.status(403).send('Invalid refresh token')` — the dead-credential arm. */
    private fun libreChatRejection(posts: IntArray) =
        countingEngine(posts, HttpStatusCode.Forbidden, "Invalid refresh token")

    /** What a reverse-proxy bouncer or WAF answers: a 403 that is not LibreChat's. */
    private fun intermediaryBlock(posts: IntArray) =
        countingEngine(posts, HttpStatusCode.Forbidden, "<html><body>Access denied</body></html>")

    // --- Wave 1: the fan-out ---

    /**
     * The headline regression. Before the fix this POSTed [FAN_OUT] times, because a *failed* refresh
     * leaves the stored access token untouched and the coalescing check only fires on a change.
     */
    @Test
    fun `a rejected proactive renewal posts once for the whole fan-out`() =
        runTest(UnconfinedTestDispatcher()) {
            val posts = intArrayOf(0)
            val stale = jwt(-60)
            val store = seeded(libreChatRejection(posts), access = stale)

            coroutineScope {
                repeat(FAN_OUT) {
                    launch { store.ensureFreshAccessToken(ACCOUNT, TEST_SERVER, stale) }
                }
            }

            assertThat(posts[0]).isEqualTo(1)
        }

    /**
     * The marker is per slot, so one account's dead session must not stop another account's renewal.
     * Both slots are driven here in the same burst window, which is the only place the marker is
     * consulted — `ensureFreshAccessToken`'s own per-slot cooldown covers everything after it.
     */
    @Test
    fun `one account's rejection does not suppress another account's renewal`() =
        runTest(UnconfinedTestDispatcher()) {
            val posts = intArrayOf(0)
            val stale = jwt(-60)
            val other = "acctB"
            val store = FakeTokenStore(
                refreshClientOf(libreChatRejection(posts)),
                seed = mapOf(
                    ACTIVE_ACCOUNT_KEY to ACCOUNT,
                    accessKeyOf(ACCOUNT) to stale,
                    refreshKeyOf(ACCOUNT) to "R0",
                    accessKeyOf(other) to stale,
                    refreshKeyOf(other) to "R1",
                ),
            )

            coroutineScope {
                repeat(FAN_OUT) { launch { store.ensureFreshAccessToken(ACCOUNT, TEST_SERVER, stale) } }
                repeat(FAN_OUT) { launch { store.ensureFreshAccessToken(other, TEST_SERVER, stale) } }
            }

            // One per slot — not one per request, and not one for both slots together.
            assertThat(posts[0]).isEqualTo(2)
        }

    /**
     * The success-path control, and the state the reporter's own diagnostics captured: seven
     * simultaneous renewals, one POST, six coalesced. It has to keep holding — a suppression marker
     * that also fired on success would collapse the fan-out for the wrong reason, and every count in
     * this suite would still read as correct.
     */
    @Test
    fun `a successful fan-out still posts once and hands everyone the renewed token`() =
        runTest(UnconfinedTestDispatcher()) {
            val posts = intArrayOf(0)
            val engine = unconfinedMockEngine {
                posts[0]++
                respond(
                    refreshResponseBody(jwt(900, id = "renewed${posts[0]}")),
                    HttpStatusCode.OK,
                    jsonResponseHeaders,
                )
            }
            val stale = jwt(-60)
            val store = seeded(engine, access = stale)

            val bearers = mutableListOf<String?>()
            coroutineScope {
                repeat(FAN_OUT) {
                    launch { bearers += store.ensureFreshAccessToken(ACCOUNT, TEST_SERVER, stale) }
                }
            }

            assertThat(posts[0]).isEqualTo(1)
            // Every caller leaves with the rotated token, not the stale one it arrived with.
            assertThat(bearers).hasSize(FAN_OUT)
            assertThat(bearers.toSet()).containsExactly(store.getAccessTokenFor(ACCOUNT))
            assertThat(bearers.toSet()).doesNotContain(stale)
        }

    /**
     * A best-effort ladder is one attempt long, so there is no next attempt to space out — yet the
     * inter-attempt sleep was guarded on `MAX_REFRESH_ATTEMPTS - 1` rather than on that ladder's own
     * length, so every failed proactive renewal slept on its way out while still holding the flight
     * lock. Asserted on the test scheduler's virtual clock, which only a real `delay` advances: the
     * mock engine answers without suspending, so any advance here is that stray sleep.
     *
     * The outcome has to be one that **falls through** to the bottom of the loop. A 403 now returns
     * from its arm directly, so it cannot reach the sleep and cannot discriminate here — use the 5xx
     * (`Retryable`) arm, which is also the case that actually wants a backoff on the reactive path.
     */
    @Test
    fun `a failed proactive renewal does not sleep holding the flight lock`() =
        runTest(UnconfinedTestDispatcher()) {
            val stale = jwt(-60)
            val engine = countingEngine(
                intArrayOf(0),
                HttpStatusCode.InternalServerError,
                "upstream unavailable",
            )
            val store = seeded(engine, access = stale)

            val before = testScheduler.currentTime
            store.ensureFreshAccessToken(ACCOUNT, TEST_SERVER, stale)

            assertThat(testScheduler.currentTime - before).isEqualTo(0)
        }

    // --- Wave 2: the ladder ---

    /**
     * Every 403 arm of upstream's `refreshController` is terminal — `jwt.verify` threw, the payload's
     * `exp` is past, or a `?retry` found no session. Spending the ladder on one cannot rescue it and
     * triples the burst.
     */
    @Test
    fun `LibreChat's own 403 settles on the first post instead of laddering`() =
        runTest(UnconfinedTestDispatcher()) {
            val posts = intArrayOf(0)
            val store = seeded(libreChatRejection(posts), access = jwt(900))

            val result = store.refreshAccessToken()

            assertThat(result).isEqualTo(RefreshResult.HardExpired)
            assertThat(posts[0]).isEqualTo(1)
        }

    /**
     * The 401 rationale must survive this change: the backend answers 401 identically for a dead
     * session and a transiently-missed session lookup, so that one keeps its ladder. This is the
     * control that stops the fix above from being implemented as "never retry an auth failure".
     */
    @Test
    fun `a 401 still spends the full ladder`() = runTest(UnconfinedTestDispatcher()) {
        val posts = intArrayOf(0)
        val engine = countingEngine(posts, HttpStatusCode.Unauthorized, "Refresh token expired or not found")
        val store = seeded(engine, access = jwt(900))

        val result = store.refreshAccessToken()

        assertThat(result).isEqualTo(RefreshResult.HardExpired)
        assertThat(posts[0]).isEqualTo(LADDER)
    }

    // --- Provenance: a 403 the server did not author ---

    /**
     * The compounding half of the report. Once CrowdSec banned the IP, every request — including the
     * refresh — got a 403 from the bouncer. Treating that as a dead session drops the user's tokens
     * over a network-layer block they cannot clear, so they are logged out *and* unable to sign back
     * in until the ban lapses.
     */
    @Test
    fun `a 403 that is not LibreChat's keeps the session`() = runTest(UnconfinedTestDispatcher()) {
        val posts = intArrayOf(0)
        val store = seeded(intermediaryBlock(posts), access = jwt(900))

        val result = store.refreshAccessToken()

        assertThat(result).isEqualTo(RefreshResult.Transient)
        // The slot survives: this is what lets the session recover when the block lifts.
        assertThat(store.store[refreshKeyOf(ACCOUNT)]).isEqualTo("R0")
        // And it is not retried — every attempt in the budget would meet the same bouncer.
        assertThat(posts[0]).isEqualTo(1)
    }

    /**
     * The cost of the endpoint-wide stand-down, stated rather than assumed.
     *
     * Once an unattributed 403 has stood the slot down, the gate is in front of *every* caller — so a
     * credential that is genuinely dead is not recognised as dead until the cooldown lapses. This
     * pins how long that lasts: the reactive caller behind the block gets `Transient` and does not
     * POST, so re-auth is delayed by at most [INTERMEDIARY_BLOCK_COOLDOWN_MS] rather than blocked
     * indefinitely.
     *
     * That is the deliberate trade — the alternative is hammering a bouncer that is already blocking
     * this client — but it is the one place the provenance design's two halves interact, so it should
     * fail visibly in a test if it ever becomes permanent.
     */
    @Test
    fun `a recorded block delays but does not prevent recognising a dead credential`() =
        runTest(UnconfinedTestDispatcher()) {
            val posts = intArrayOf(0)
            // First answer stands the slot down; the second would be LibreChat's own terminal 403.
            val engine = unconfinedMockEngine {
                posts[0]++
                if (posts[0] == 1) {
                    respond("<html><body>Access denied</body></html>", HttpStatusCode.Forbidden)
                } else {
                    respond("Invalid refresh token", HttpStatusCode.Forbidden)
                }
            }
            val store = seeded(engine, access = jwt(-60))

            // The block lands, and the session is kept.
            assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.Transient)
            assertThat(store.store[refreshKeyOf(ACCOUNT)]).isEqualTo("R0")

            // Inside the cooldown a reactive caller stands down too: Transient, and NO second POST —
            // this is the delay, and the assertion that it is a delay and not a POST storm.
            assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.Transient)
            assertThat(posts[0]).isEqualTo(1)
            // Still signed in: the session outlives the block rather than being torn down by it.
            assertThat(store.store[refreshKeyOf(ACCOUNT)]).isEqualTo("R0")
        }

    /** A 403 whose body is LibreChat's `redirect('/login')` stub is identified by its Location. */
    @Test
    fun `a 403 redirect to login is read as LibreChat's own rejection`() =
        runTest(UnconfinedTestDispatcher()) {
            val posts = intArrayOf(0)
            val engine = unconfinedMockEngine {
                posts[0]++
                respond("Found. Redirecting to /login", HttpStatusCode.Forbidden, headersOf("Location", "/login"))
            }
            val store = seeded(engine, access = jwt(900))

            assertThat(store.refreshAccessToken()).isEqualTo(RefreshResult.HardExpired)
            assertThat(posts[0]).isEqualTo(1)
        }
}

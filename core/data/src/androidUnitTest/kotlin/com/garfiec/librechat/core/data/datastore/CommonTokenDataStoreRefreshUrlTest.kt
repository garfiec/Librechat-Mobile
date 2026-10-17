package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.network.client.PinnedServerBaseUrlKey
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock

/**
 * Where a pinned refresh POST goes when the stored base URL is not a bare origin.
 *
 * A stored base can be LibreChat's web login page — `https://host/login?redirect_to=%2Fc%2Fnew` —
 * because onboarding accepts it: Ktor's request merge resolves `/api/config` against the origin and
 * drops the base path and query. The refresh pin is built by string concatenation, so joined onto
 * the raw string it would go to `/login?redirect_to=%2Fc%2Fnew/api/auth/refresh`; the deployment's
 * gate answers that with a 403 the client cannot attribute to LibreChat, keeps the session, and every
 * request runs on a bearer that can never renew. The refresh must resolve the way every other
 * request does.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class CommonTokenDataStoreRefreshUrlTest {

    private companion object {
        const val ACCOUNT = "acctA"
        const val WEB_LOGIN_BASE = "https://chat.example.com/login?redirect_to=%2Fc%2Fnew"
        const val EXPECTED_REFRESH = "https://chat.example.com/api/auth/refresh"
    }

    private fun jwt(secondsFromNow: Long): String {
        val exp = Clock.System.now().toEpochMilliseconds() / 1000 + secondsFromNow
        val encoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = encoder.encode("""{"alg":"HS256"}""".encodeToByteArray())
        val payload = encoder.encode("""{"id":"u1","exp":$exp}""".encodeToByteArray())
        return "$header.$payload.sig"
    }

    private class Seen(var url: String? = null, var pinnedBase: String? = null)

    /** Records the one POST's URL and its pinned-base attribute, and answers with a renewed pair. */
    private fun recordingEngine(seen: Seen): MockEngine = unconfinedMockEngine { request ->
        seen.url = request.url.toString()
        seen.pinnedBase = request.attributes.getOrNull(PinnedServerBaseUrlKey)
        respond(refreshResponseBody(jwt(900)), HttpStatusCode.OK, jsonResponseHeaders)
    }

    private fun seeded(engine: MockEngine, access: String): FakeTokenStore = FakeTokenStore(
        refreshClientOf(engine),
        seed = mapOf(
            ACTIVE_ACCOUNT_KEY to ACCOUNT,
            accessKeyOf(ACCOUNT) to access,
            refreshKeyOf(ACCOUNT) to "R0",
        ),
    )

    @Test
    fun `a proactive renewal posts to the origin's refresh route`() = runTest(UnconfinedTestDispatcher()) {
        val seen = Seen()
        val stale = jwt(-60)
        val store = seeded(recordingEngine(seen), access = stale)

        store.ensureFreshAccessToken(ACCOUNT, WEB_LOGIN_BASE, stale)

        assertThat(seen.url).isEqualTo(EXPECTED_REFRESH)
    }

    @Test
    fun `a reactive pinned refresh posts to the origin's refresh route`() = runTest(UnconfinedTestDispatcher()) {
        val seen = Seen()
        val store = seeded(recordingEngine(seen), access = jwt(900))

        store.refreshAccessTokenFor(ACCOUNT, WEB_LOGIN_BASE)

        assertThat(seen.url).isEqualTo(EXPECTED_REFRESH)
    }

    /**
     * The pin handed to `ServerHeadersPlugin` must stay the stored string: it derives the serverId
     * from it to look up the deployment's gateway headers, and the serverId hashes the URL as the
     * roster stores it. Reducing it to the origin here would file a junk-URL user's headers under a
     * server the app never recorded, and their refresh would go out without them.
     */
    @Test
    fun `the gateway-header pin keeps the stored base URL as is`() = runTest(UnconfinedTestDispatcher()) {
        val seen = Seen()
        val store = seeded(recordingEngine(seen), access = jwt(900))

        store.refreshAccessTokenFor(ACCOUNT, WEB_LOGIN_BASE)

        assertThat(seen.pinnedBase).isEqualTo(WEB_LOGIN_BASE)
    }

    /** The control: a clean base resolves to the same URL it always did. */
    @Test
    fun `a clean base posts where it always did`() = runTest(UnconfinedTestDispatcher()) {
        val seen = Seen()
        val store = seeded(recordingEngine(seen), access = jwt(900))

        store.refreshAccessTokenFor(ACCOUNT, "https://chat.example.com/")

        assertThat(seen.url).isEqualTo(EXPECTED_REFRESH)
        assertThat(seen.pinnedBase).isEqualTo("https://chat.example.com")
    }
}

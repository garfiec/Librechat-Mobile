package com.garfiec.librechat.core.data.repository

import android.webkit.CookieManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The switch clear must kill every auth cookie the LibreChat servers issued while leaving the
 * identity provider's own session cookie alive — that cookie is what lets an expired session's
 * re-sign-in complete without the password.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSwitchCacheCleanerTest {

    private val serverUrl = "https://chat.example.com"
    private val providerUrl = "https://accounts.google.com"

    // Main goes unconfined: the real main looper under Robolectric is paused mid-test, so a
    // withContext(Dispatchers.Main) inside a blocking test body would never be drained.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun seed() {
        val cookies = CookieManager.getInstance()
        cookies.setCookie(serverUrl, "refreshToken=stale-token; Path=/; Max-Age=100000; HttpOnly; Secure")
        cookies.setCookie(serverUrl, "token_provider=openid; Path=/; Max-Age=100000; HttpOnly; Secure")
        cookies.setCookie(serverUrl, "openid_access_token=old-access; Path=/; Max-Age=100000; HttpOnly; Secure")
        cookies.setCookie(serverUrl, "openid_id_token=old-id; Path=/; Max-Age=100000; HttpOnly; Secure")
        cookies.setCookie(providerUrl, "NID=provider-session; Path=/; Max-Age=100000")
    }

    /** setCookie is asynchronous on real Android (Chromium commits on a worker); settle for it. */
    private fun awaitJar(url: String, until: (String?) -> Boolean): String? {
        val deadline = System.currentTimeMillis() + 3_000
        var read: String? = null
        while (System.currentTimeMillis() < deadline) {
            read = CookieManager.getInstance().getCookie(url)
            if (until(read)) return read
            Thread.sleep(50)
        }
        return read
    }

    @Test
    fun `the switch clear expires the server's auth cookies`() = runBlocking {
        seed()
        assertThat(awaitJar(serverUrl) { it != null && "refreshToken=stale-token" in it })
            .contains("refreshToken=stale-token")

        AndroidSwitchCacheCleaner().clearOnSwitch(setOf(serverUrl))

        val jar = awaitJar(serverUrl) { it == null || "refreshToken=stale-token" !in it }.orEmpty()
        assertThat(jar).doesNotContain("refreshToken=stale-token")
        assertThat(jar).doesNotContain("token_provider=openid")
        assertThat(jar).doesNotContain("openid_access_token=old-access")
        assertThat(jar).doesNotContain("openid_id_token=old-id")
    }

    @Test
    fun `the switch clear keeps the provider's session cookie`() = runBlocking {
        seed()
        assertThat(awaitJar(providerUrl) { it != null && "NID=provider-session" in it })
            .contains("NID=provider-session")

        AndroidSwitchCacheCleaner().clearOnSwitch(setOf(serverUrl))

        assertThat(CookieManager.getInstance().getCookie(providerUrl))
            .contains("NID=provider-session")
    }
}
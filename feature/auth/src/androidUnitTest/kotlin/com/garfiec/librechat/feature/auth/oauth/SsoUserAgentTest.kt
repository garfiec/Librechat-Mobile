package com.garfiec.librechat.feature.auth.oauth

import com.garfiec.librechat.core.network.client.LibreChatHttpClient
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SsoUserAgentTest {

    @Test
    fun `the android agent is the reduced form chrome actually sends`() {
        // M110 froze the device segment. Naming a real model is a shape no shipping Chrome emits,
        // and it re-opens the mismatch against screen size and navigator.platform that claiming
        // nothing avoids.
        assertThat(SSO_ANDROID_USER_AGENT).contains("(Linux; Android 10; K)")
    }

    @Test
    fun `the android agent does not carry the webview marker`() {
        assertThat(SSO_ANDROID_USER_AGENT).contains("Chrome/")
        assertThat(SSO_ANDROID_USER_AGENT).doesNotContain("; wv")
        assertThat(SSO_ANDROID_USER_AGENT).doesNotContain("Version/")
    }

    @Test
    fun `the android agent is not the api client's`() {
        // Different audiences: the API constant answers the server's ua-parser soft-ban, this one
        // answers a provider's embedded-browser detection. Collapsing them once already replaced a
        // verified string with an unverified one.
        assertThat(SSO_ANDROID_USER_AGENT).isNotEqualTo(LibreChatHttpClient.BROWSER_USER_AGENT)
    }

    @Test
    fun `the safari suffix tracks the reported os version`() {
        assertThat(safariApplicationName("17.5")).isEqualTo("Version/17.5 Safari/604.1")
        assertThat(safariApplicationName("16.7.2")).isEqualTo("Version/16.7 Safari/604.1")
    }

    @Test
    fun `a major-only version still reads like safari`() {
        assertThat(safariApplicationName("18")).isEqualTo("Version/18.0 Safari/604.1")
    }

    @Test
    fun `an unparseable version falls back instead of emitting a malformed agent`() {
        assertThat(safariApplicationName("")).isEqualTo("Version/17.0 Safari/604.1")
        assertThat(safariApplicationName("unknown")).isEqualTo("Version/17.0 Safari/604.1")
    }

    @Test
    fun `the safari suffix claims no device, so the prefix stays the real one`() {
        val suffix = safariApplicationName("17.5")
        assertThat(suffix).doesNotContain("iPhone")
        assertThat(suffix).doesNotContain("iPad")
        assertThat(suffix).doesNotContain("Mozilla")
    }
}

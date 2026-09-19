package com.garfiec.librechat.feature.auth.oauth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SsoNavigationGateTest {

    private fun gate(
        serverUrl: String = "https://chat.example.com",
        provider: String = "google",
    ) = SsoNavigationGate(serverUrl, provider)

    @Test
    fun `allows the initial provider navigation`() {
        val gate = gate()
        assertThat(gate.decide("https://chat.example.com/oauth/google"))
            .isEqualTo(SsoNavigationDecision.ALLOW)
        assertThat(gate.stopped).isFalse()
    }

    @Test
    fun `allows the provider's own pages`() {
        val gate = gate()
        gate.decide("https://chat.example.com/oauth/google")
        assertThat(gate.decide("https://accounts.google.com/o/oauth2/auth?client_id=x"))
            .isEqualTo(SsoNavigationDecision.ALLOW)
    }

    @Test
    fun `allows the server callback itself`() {
        val gate = gate()
        assertThat(gate.decide("https://chat.example.com/oauth/google/callback?code=abc&state=xyz"))
            .isEqualTo(SsoNavigationDecision.ALLOW)
    }

    @Test
    fun `stops the redirect that follows the callback`() {
        val gate = gate()
        gate.decide("https://chat.example.com/oauth/google/callback?code=abc")
        assertThat(gate.decide("https://chat.example.com/"))
            .isEqualTo(SsoNavigationDecision.STOP)
        assertThat(gate.stopped).isTrue()
    }

    @Test
    fun `stops the error landing that follows the callback`() {
        val gate = gate()
        gate.decide("https://chat.example.com/oauth/google/callback?code=abc")
        assertThat(gate.decide("https://chat.example.com/oauth/error"))
            .isEqualTo(SsoNavigationDecision.STOP)
    }

    @Test
    fun `stays stopped once stopped`() {
        val gate = gate()
        gate.decide("https://chat.example.com/oauth/google/callback")
        gate.decide("https://chat.example.com/")
        assertThat(gate.decide("https://chat.example.com/c/new"))
            .isEqualTo(SsoNavigationDecision.STOP)
    }

    @Test
    fun `does not arm on another provider's callback`() {
        val gate = gate()
        gate.decide("https://chat.example.com/oauth/github/callback?code=abc")
        assertThat(gate.decide("https://chat.example.com/"))
            .isEqualTo(SsoNavigationDecision.ALLOW)
    }

    @Test
    fun `does not arm on a lookalike host`() {
        val gate = gate()
        gate.decide("https://evil-chat.example.com/oauth/google/callback?code=abc")
        assertThat(gate.decide("https://chat.example.com/"))
            .isEqualTo(SsoNavigationDecision.ALLOW)
    }

    @Test
    fun `tolerates a trailing slash on the server url`() {
        val gate = gate(serverUrl = "https://chat.example.com/")
        gate.decide("https://chat.example.com/oauth/google/callback?code=abc")
        assertThat(gate.decide("https://chat.example.com/"))
            .isEqualTo(SsoNavigationDecision.STOP)
    }

    @Test
    fun `matches a server mounted under a sub-path`() {
        val gate = gate(serverUrl = "https://example.com/librechat")
        gate.decide("https://example.com/librechat/oauth/google/callback?code=abc")
        assertThat(gate.decide("https://example.com/librechat/"))
            .isEqualTo(SsoNavigationDecision.STOP)
    }

    @Test
    fun `arms when the WebView lower-cases a host the user typed in mixed case`() {
        val gate = gate(serverUrl = "https://Chat.Example.com")
        gate.decide("https://chat.example.com/oauth/google/callback?code=abc")
        assertThat(gate.decide("https://chat.example.com/"))
            .isEqualTo(SsoNavigationDecision.STOP)
    }

    @Test
    fun `a callback-prefixed path on a longer segment does not arm`() {
        val gate = gate()
        gate.decide("https://chat.example.com/oauth/google/callbackextra")
        assertThat(gate.decide("https://chat.example.com/"))
            .isEqualTo(SsoNavigationDecision.ALLOW)
    }
}

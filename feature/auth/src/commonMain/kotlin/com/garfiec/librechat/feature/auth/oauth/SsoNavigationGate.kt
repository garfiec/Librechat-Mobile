package com.garfiec.librechat.feature.auth.oauth

enum class SsoNavigationDecision { ALLOW, STOP }

/**
 * Decides when the OAuth round-trip is over, so the WebView can be stopped before it loads
 * anything else.
 *
 * Without this the server's post-callback redirect to `DOMAIN_CLIENT` loads the whole LibreChat
 * web app, whose `AuthContext` fires `silentRefresh()` on mount. That hits the same
 * `/api/auth/refresh` the app is about to call, and the backend rotates `session.refreshTokenHash`
 * on every refresh — so whichever request lands second gets a 401. The app wins in practice, but
 * nothing enforces it, and a lost race looks like a server bug.
 *
 * Cancelling the navigation (rather than stopping the load afterwards) means that page is never
 * fetched at all, which also decouples the stop decision from cookie-read timing.
 */
class SsoNavigationGate(serverUrl: String, provider: String) {

    private val callbackPrefix = "${serverUrl.trimEnd('/')}/oauth/$provider/callback"

    private var callbackSeen = false

    var stopped = false
        private set

    fun decide(url: String): SsoNavigationDecision {
        if (stopped) return SsoNavigationDecision.STOP

        if (callbackSeen) {
            stopped = true
            return SsoNavigationDecision.STOP
        }

        // Prefix rather than host compare: the app built the start URL from this same string, so
        // this is the one comparison guaranteed to agree with what it actually loaded — including
        // a server mounted under a sub-path.
        //
        // Case-insensitively, because the two sides come from different places: the stored server
        // URL keeps whatever case the user typed (it is only trimmed and given a scheme), while
        // the WebView reports a canonicalised URL with scheme and host lower-cased. A server
        // entered as `https://Chat.Example.com` would otherwise never match its own callback, the
        // gate would never arm, and the post-callback page would load after all.
        val path = url.substringBefore('?').substringBefore('#')
        if (path.equals(callbackPrefix, ignoreCase = true) ||
            path.startsWith("$callbackPrefix/", ignoreCase = true)
        ) {
            callbackSeen = true
        }
        return SsoNavigationDecision.ALLOW
    }
}

package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.auth.AuthCookies

/** Embedded WebView that runs the full OAuth round-trip for [provider] against [serverUrl].
 *
 *  Loads `{serverUrl}/oauth/{provider}`. The server mounts the OAuth router at `/oauth`, not
 *  `/api/oauth` — the latter is a 404, which is why the previous Custom Tabs flow never reached a
 *  provider at all. The User-Agent is a browser string because the server's ua-parser middleware
 *  rejects WebView agents, and because providers block embedded-browser sign-ins.
 *
 *  This view owns the jar's lifecycle around the load, because the clear has to be ordered against
 *  the load and only the side that owns both can guarantee that. When [clearAllCookies] is true —
 *  the add-account flow — the jar is emptied: a retained provider session would let this round-trip
 *  silently complete as whoever the provider last authenticated, with no chooser and no error. A
 *  plain sign-in keeps the provider's session cookie, so an expired session's re-sign-in can
 *  complete without the password; only the server's own auth cookies are expired first. iOS gets
 *  the empty-jar start by construction from its non-persistent store; Android has to ask for it.
 *
 *  Exactly one of [onTokenCapture], [onOAuthError] or [onCaptureFail] fires per round-trip.
 *  [onCaptureFail] is the primary failure signal — since upstream dropped `failureMessage`, a
 *  rejected callback is a bare redirect carrying no `error` parameter at all. */
@Composable
expect fun SsoWebView(
    serverUrl: String,
    provider: String,
    clearAllCookies: Boolean,
    onTokenCapture: (AuthCookies) -> Unit,
    onOAuthError: (String?) -> Unit,
    onCaptureFail: () -> Unit,
    modifier: Modifier = Modifier,
)

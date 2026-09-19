package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Embedded WebView that runs the full OAuth round-trip for [provider] against [serverUrl].
 *
 *  Loads `{serverUrl}/oauth/{provider}`. The server mounts the OAuth router at `/oauth`, not
 *  `/api/oauth` — the latter is a 404, which is why the previous Custom Tabs flow never reached a
 *  provider at all. The User-Agent is a browser string because the server's ua-parser middleware
 *  rejects WebView agents, and because providers block embedded-browser sign-ins.
 *
 *  The whole round-trip runs in one cookie jar, which is what lets the callback carry the state
 *  binding the server set at authorize time (upstream v0.8.8-rc3+). The callback then sets
 *  `refreshToken` on the server's own origin; this view reads it out of that jar and reports it
 *  via [onTokenCapture].
 *
 *  This view owns the jar's whole lifecycle, because the clear has to be ordered against the load
 *  and only the side that owns both can guarantee that. Every sign-in starts from an empty jar, not
 *  merely one without the server's `refreshToken`: the provider's own session cookie is what makes
 *  the round-trip re-select an identity the user never chose, and clearing it is the only way to
 *  guarantee the account chooser is reached. iOS gets this by construction from its non-persistent
 *  store; Android has to ask for it.
 *
 *  Exactly one of [onTokenCapture], [onOAuthError] or [onCaptureFail] fires per round-trip.
 *  [onCaptureFail] is the primary failure signal — since upstream dropped `failureMessage`, a
 *  rejected callback is a bare redirect carrying no `error` parameter at all. */
@Composable
expect fun SsoWebView(
    serverUrl: String,
    provider: String,
    onTokenCapture: (String) -> Unit,
    onOAuthError: (String?) -> Unit,
    onCaptureFail: () -> Unit,
    modifier: Modifier = Modifier,
)

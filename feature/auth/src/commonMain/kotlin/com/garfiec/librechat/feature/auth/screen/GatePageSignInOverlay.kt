package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The gate's own sign-in page, full screen over the server screen, for a gate the browser sign-in
 * can't use. Ends when the gate sends the page back to the server with its session cookie set.
 */
@Composable
fun GatePageSignInOverlay(
    portalUrl: String,
    serverUrl: String,
    onSignInComplete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GatePageSignInFrame(onClose = onClose, modifier = modifier) { webViewModifier ->
        GatePageWebView(
            portalUrl = portalUrl,
            serverUrl = serverUrl,
            onSignInComplete = onSignInComplete,
            onFail = onClose,
            modifier = webViewModifier,
        )
    }
}

/**
 * Loads [portalUrl] in an in-app web view, with a cleared cookie jar, and reports the cookies set
 * for [serverUrl] (the app's own excluded) once a navigation reaches [serverUrl]'s authority.
 * [onFail] when the page can't load or the gate set no cookie.
 */
@Composable
internal expect fun GatePageWebView(
    portalUrl: String,
    serverUrl: String,
    onSignInComplete: (String) -> Unit,
    onFail: () -> Unit,
    modifier: Modifier = Modifier,
)

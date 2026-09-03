package com.garfiec.librechat.feature.auth.oauth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * TCHAT: whole file. Runs the OAuth flow in an app-owned `WebView`/`WKWebView` instead of an
 * external browser (Chrome Custom Tab / `ASWebAuthenticationSession`).
 *
 * The external-browser design in upstream Switchboard could never work: LibreChat sets the
 * `refreshToken` cookie on the *browser's* origin, but a Custom Tab uses Chrome's own profile and
 * `ASWebAuthenticationSession` uses Safari's — neither is the cookie jar [OAuthLauncher] reads
 * from (`android.webkit.CookieManager` / `NSHTTPCookieStorage`). Loading the flow in a `WebView`
 * the app itself embeds makes that store the *same* store [OAuthLauncher] reads afterwards.
 *
 * This composable only detects *when* the flow has finished — it deliberately does not read
 * cookies or extract a token itself. The caller reacts to [onComplete] by calling the existing
 * [OAuthLauncher]-based extraction, exactly as it did for the old, external-browser design's
 * `ON_RESUME` check — only the trigger changed, not what runs next.
 *
 * @param startUrl the OAuth entry point, e.g. `"$serverUrl/oauth/$provider"`.
 * @param serverUrl this server's origin — completion is [isOAuthReturnUrl] against it.
 * @param onComplete invoked once, the moment the WebView navigates back to an ordinary page on
 *   [serverUrl] (i.e. anywhere outside `/oauth`). Never invoked if the user dismisses first.
 */
@Composable
expect fun OAuthWebView(
    startUrl: String,
    serverUrl: String,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
)

/**
 * The OAuth entry point for [provider] on [serverUrl]. LibreChat mounts these at `/oauth`, not
 * `/api/oauth` — see the `TCHAT:` comments in [OAuthLauncher]'s implementations for the bug this
 * corrected. Centralized here (rather than inlined at each `launchOAuth` call site) so there is
 * exactly one place this shape is written.
 */
fun oauthStartUrl(serverUrl: String, provider: String): String = "$serverUrl/oauth/$provider"

/**
 * True once the WebView has navigated from the OAuth flow back to an ordinary page on [serverUrl]
 * — i.e. [currentUrl] shares [serverUrl]'s origin (scheme + host + port) and its path is no longer
 * under `/oauth`. That is exactly the shape of LibreChat's own redirect chain: every provider
 * (including TensorGrid's OIDC bridge) ends at `oauthHandler`, which sets `refreshToken` then
 * redirects to `DOMAIN_CLIENT` — which for this app *is* [serverUrl].
 *
 * Deliberately a plain string check, not a full URL-parsing library: `:feature:auth` has no
 * dependency that exposes one to commonMain, and comparing origin + path prefix is all this needs.
 */
internal fun isOAuthReturnUrl(serverUrl: String, currentUrl: String): Boolean {
    val serverOrigin = originOf(serverUrl) ?: return false
    val (currentOrigin, currentPath) = originAndPathOf(currentUrl) ?: return false
    return currentOrigin.equals(serverOrigin, ignoreCase = true) &&
        !currentPath.startsWith("/oauth", ignoreCase = true)
}

private fun originOf(url: String): String? = originAndPathOf(url)?.first

/** Splits a URL into ("scheme://host[:port]", "/path?query#fragment"); null if it has no scheme. */
private fun originAndPathOf(url: String): Pair<String, String>? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val afterScheme = url.substring(schemeEnd + 3)
    val pathStart = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
    return if (pathStart < 0) {
        url to ""
    } else {
        url.substring(0, schemeEnd + 3 + pathStart) to afterScheme.substring(pathStart)
    }
}

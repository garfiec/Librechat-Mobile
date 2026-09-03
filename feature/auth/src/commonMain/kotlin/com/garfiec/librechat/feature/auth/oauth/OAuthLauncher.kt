package com.garfiec.librechat.feature.auth.oauth

/**
 * Platform-abstracted OAuth cookie access. The flow itself is launched in-app via [OAuthWebView]
 * (an embedded `WebView`/`WKWebView`, not an external browser) — see that file's kdoc for why.
 * This interface only covers what happens after: reading the `refreshToken` cookie the server set
 * during the flow, and clearing it once consumed.
 *
 * TCHAT: was also responsible for *launching* the flow (`launchOAuth`, via a Chrome Custom Tab on
 * Android / `ASWebAuthenticationSession` on iOS). That design never actually worked: both surfaces
 * present the OAuth pages in the *browser's* cookie jar (Chrome's own profile / Safari's), not the
 * store this interface reads from (`android.webkit.CookieManager` / `NSHTTPCookieStorage`) — so
 * `extractTokenFromCookies` could never find anything. `OAuthWebView` fixes this by running the
 * flow in a `WebView`/`WKWebView` the app embeds itself, whose cookie jar *is* the store below.
 */
interface OAuthLauncher {
    /** After OAuth redirect, extract refresh token from cookies/callback. */
    fun extractTokenFromCookies(serverUrl: String): String?

    /** Clear OAuth cookie/state after successful extraction. */
    fun clearOAuthCookie(serverUrl: String)
}

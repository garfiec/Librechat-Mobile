package com.garfiec.librechat.feature.auth.oauth

/**
 * Chrome's Android User-Agent, for the SSO WebView only.
 *
 * Deliberately not `LibreChatHttpClient.BROWSER_USER_AGENT`. That constant exists to satisfy the
 * stock server's `ua-parser-js` middleware, which soft-bans the first non-browser agent it sees,
 * and it is load-bearing on three surfaces. The audience here is an identity provider running its
 * own embedded-browser detection — a different problem with a different correct answer — so the two
 * are separate constants that are allowed to drift apart.
 *
 * The device segment is frozen at `Android 10; K` on purpose. Chrome reduced its Android
 * User-Agent in M110: every device, every OEM and every screen size reports exactly that, so a
 * string naming a real model is a shape no shipping Chrome emits and takes no fingerprinting to
 * spot. It also claims no device at all, which leaves nothing for `screen.width`,
 * `devicePixelRatio` or `navigator.platform` to contradict — the reason deriving it from
 * `Build.MODEL` would be worse rather than better.
 *
 * This exact string is the one the flow was verified against. Treat a change to it as a change to
 * the only empirical result this approach rests on.
 *
 * Not covered here: Chrome also sends `Sec-CH-UA` client hints, generated independently of a
 * `userAgentString` override, which would contradict this far more precisely than any viewport
 * measurement. Keeping them consistent needs `androidx.webkit`'s `setUserAgentMetadata`, which is
 * not a dependency; what WebView actually advertises is unverified.
 */
const val SSO_ANDROID_USER_AGENT: String =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/138.0.0.0 Mobile Safari/537.36"

/** Used only when the reported system version is unparseable, which a real device does not do. */
private const val FALLBACK_SAFARI_VERSION = "17.0"

/**
 * The `applicationNameForUserAgent` that makes a `WKWebView` present as Safari rather than as an
 * embedded browser.
 *
 * WebKit builds the agent as its own prefix plus this suffix, and that prefix already carries the
 * real device, idiom and OS version. Appending rather than replacing is the point: nothing here
 * fabricates a device or an OS, so there is no claim for the runtime to contradict. What the
 * default omits is the `Version/…` and `Safari/…` pair, and that omission is the in-app-browser
 * tell.
 *
 * [systemVersion] is `UIDevice.systemVersion`, so `Version/` tracks the OS the way Safari's does.
 * One known cosmetic divergence: WebKit appends this after `Mobile/15E148`, whereas Safari orders
 * it `Version/… Mobile/… Safari/…`. Token-based parsers do not care, and the alternative — writing
 * the whole agent by hand — means inventing the device and idiom this avoids.
 */
fun safariApplicationName(systemVersion: String): String {
    val parts = systemVersion.split('.').filter { part -> part.isNotBlank() }
    val major = parts.getOrNull(0)?.takeIf { part -> part.all(Char::isDigit) }
        ?: return "Version/$FALLBACK_SAFARI_VERSION Safari/604.1"
    val minor = parts.getOrNull(1)?.takeIf { part -> part.all(Char::isDigit) } ?: "0"
    return "Version/$major.$minor Safari/604.1"
}

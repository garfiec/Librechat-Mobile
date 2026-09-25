package com.garfiec.librechat.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.AttributeKey

/** RFC 6265 cookie names are case-sensitive, and the server reads exactly this one. */
private const val REFRESH_COOKIE_NAME = "refreshToken"

/** The account an attached cookie belongs to. Survives a strip, so the re-attach knows what to re-read. */
private val ImageCookieAccountKey = AttributeKey<String>("ImageCookieAccount")

/** The token currently on the wire for this request; absent means no cookie is attached right now. */
private val ImageCookieAttachedKey = AttributeKey<String>("ImageCookieAttached")

/**
 * The cookie value a rotation-race retry was already spent on.
 *
 * Keyed by the token rather than a boolean because Ktor copies request attributes onto every
 * redirect hop and every retry replay, and `HttpRedirect` sits outside this plugin — so a plain
 * flag burns the budget for the whole call chain instead of for one send, and a cookie that has
 * rotated a second time mid-chain never gets its retry. The storm guard is the unchanged-token
 * check below, not this: a server refusing on the merits re-reads the same value and stops.
 */
private val ImageCookieRetriedKey = AttributeKey<String>("ImageCookieRetriedFor")

/**
 * Authenticates requests to the server's local image mount (`${basePath}/images/…`) with the account's
 * refresh-token cookie, because that is the only credential its middleware reads.
 *
 * Upstream made the mount credentialed **by default** in 0.8.8-rc2 (`secureImageLinks !== false`, and
 * the setting is unset on every deployment that never opted out). Its middleware authenticates on
 * `req.headers.cookie` alone — no `Authorization` branch, no query token, no signed URL — so a
 * bearer-token client loses every generated image, tool-call image output and stored `/images/…`
 * avatar. A browser is unaffected, which is why upstream would not have noticed: `res.cookie(
 * 'refreshToken', …)` sets no `path`, so browsers already attach this cookie to every same-origin
 * request including `<img>` fetches. This is a strictly narrower use of a credential the app already
 * puts on the wire for `/api/auth/refresh`.
 *
 * ### Install position is load-bearing
 *
 * **Between [AuthInterceptorPlugin] and [ServerHeadersPlugin], on the main client only.** Ktor runs
 * same-phase `HttpRequestPipeline.State` interceptors in install order, so the app's
 * `Cookie: refreshToken=…` is already on the request when [applyCustomHeaders] merges the user's
 * gateway cookie into it — which is what keeps this to **one** `Cookie` line, and what lets that
 * function's case-sensitive collision-drop shadow a stale `refreshToken=` pasted out of browser
 * devtools rather than the other way round. Coil resolves the main client and nothing else; the
 * streaming and refresh clients never fetch an image.
 *
 * ### Gates, each closing a specific hole
 *
 * - **[isSameServerAuthority]** (scheme + host + port, fail-closed) — not `isSameHostAsServer`, which
 *   fails *open* on an unresolved base URL and would permit a same-host `http://` downgrade. It is
 *   also what keeps the cookie off the presigned-CDN URLs `ImageUrlResolver`'s `startsWith("http")`
 *   arm passes through untouched.
 * - **[isSecuredImagePath]**, base-path aware — anything looser is a blanket cookie.
 * - **A resolved, non-pending account.** A null account or a [RequestIdentity.isPending] add-account
 *   probe attaches nothing, mirroring the explicit bearer branch in [AuthInterceptorPlugin]: the bare
 *   token slot holds a *staged* sign-in's credential during that flow, and sending it to the server
 *   being added is the exact leak the pending identity exists to prevent.
 * - **Stripped on a cross-authority redirect** (and re-applied on return). Ktor's `HttpRedirect`
 *   copies every header to the new target and strips only `Authorization`; [stripCustomHeaders]
 *   deliberately unpicks only the *user's* cookie segments. Without this, a `/images/` 302 off-domain
 *   hands the refresh token to a foreign host.
 *
 * ### The rotation race
 *
 * From rc2 the middleware does not merely verify the JWT (as rc1 did): it looks the token up via
 * `findSession({ userId, refreshToken })` against `session.refreshTokenHash`, which every refresh
 * overwrites. So a token read even slightly before a concurrent refresh is already invalid, and it
 * comes back **403**, not 401. Hence the token is read at attach time and never cached, plus a
 * one-shot retry that re-reads and re-sends **only if the value actually changed**. `configureRetryPolicy`
 * is 5xx/IO-only, so nothing else re-sends underneath this and there is no retry storm.
 */
class ImageCookiePlugin private constructor(
    private val credentials: ImageCookieCredentials,
    private val serverUrlProvider: ServerUrlProvider?,
) {
    class Config {
        lateinit var credentials: ImageCookieCredentials

        /**
         * Fallback source of the server base URL for requests carrying no [RequestIdentity] snapshot.
         * Null leaves those requests cookie-less — fail-closed, like [isSameServerAuthority] itself.
         */
        var serverUrlProvider: ServerUrlProvider? = null
    }

    companion object : HttpClientPlugin<Config, ImageCookiePlugin> {
        override val key = AttributeKey<ImageCookiePlugin>("ImageCookie")

        override fun prepare(block: Config.() -> Unit): ImageCookiePlugin {
            val config = Config().apply(block)
            return ImageCookiePlugin(config.credentials, config.serverUrlProvider)
        }

        override fun install(plugin: ImageCookiePlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                val snapshot = context.attributes.getOrNull(RequestIdentityKey)
                if (snapshot?.isPending == true) return@intercept
                val baseUrl = plugin.baseUrlFor(snapshot)
                if (!isSameServerAuthority(context.url, baseUrl)) return@intercept
                if (!isSecuredImagePath(context.url, baseUrl)) return@intercept
                val accountId = snapshot?.accountId ?: return@intercept
                plugin.attach(context, accountId)
            }

            scope.plugin(HttpSend).intercept { request ->
                // Before the send, because a redirect re-enters this interceptor per hop with the
                // rewritten URL — the State phase runs once per *call* and cannot see hop two at all.
                plugin.reconcileAcrossAuthority(request)

                val call = execute(request)
                if (call.response.status != HttpStatusCode.Forbidden) return@intercept call

                // Only a hop that actually carried the cookie can be the rotation race. A 403 from a
                // CDN we stripped for, or from an image that simply isn't this user's, is terminal.
                val sent = request.attributes.getOrNull(ImageCookieAttachedKey) ?: return@intercept call
                val accountId = request.attributes.getOrNull(ImageCookieAccountKey) ?: return@intercept call
                if (request.attributes.getOrNull(ImageCookieRetriedKey) == sent) return@intercept call

                val rotated = plugin.credentials.refreshTokenFor(accountId)
                // An unchanged token means the server rejected this exact value on its merits; resending
                // it would only turn one broken image into two round trips.
                if (rotated == null || rotated == sent) return@intercept call

                request.attributes.put(ImageCookieRetriedKey, sent)
                request.headers.putRefreshCookie(rotated)
                request.attributes.put(ImageCookieAttachedKey, rotated)
                execute(request)
            }
        }
    }

    private fun baseUrlFor(snapshot: RequestIdentity?): String? =
        snapshot?.baseUrl?.takeIf { it.isNotEmpty() } ?: serverUrlProvider?.getBaseUrl()

    private suspend fun attach(request: HttpRequestBuilder, accountId: String) {
        val token = credentials.refreshTokenFor(accountId) ?: return
        request.headers.putRefreshCookie(token)
        // Recorded only once a cookie is really on the request, so the redirect pass below can use its
        // presence as "this call is an image call that has a credential to manage".
        request.attributes.put(ImageCookieAccountKey, accountId)
        request.attributes.put(ImageCookieAttachedKey, token)
    }

    /**
     * Keep the cookie on exactly the hops that qualify. Both directions, because a redirect chain can
     * leave the server's authority and come back (origin → object store → origin is an ordinary
     * signed-URL shape, and an access edge bounces through its own domain by design), and a strip that
     * is never undone leaves the final hop unauthenticated — which is a 401 page, not an error.
     */
    private suspend fun reconcileAcrossAuthority(request: HttpRequestBuilder) {
        val accountId = request.attributes.getOrNull(ImageCookieAccountKey) ?: return
        val baseUrl = baseUrlFor(request.attributes.getOrNull(RequestIdentityKey))
        val attached = request.attributes.getOrNull(ImageCookieAttachedKey)
        val eligible = isSameServerAuthority(request.url, baseUrl) && isSecuredImagePath(request.url, baseUrl)
        when {
            attached != null && !eligible -> {
                request.headers.removeRefreshCookie()
                request.attributes.remove(ImageCookieAttachedKey)
            }
            // Re-read rather than replay [attached]: by the time a redirect chain comes back the token
            // may have rotated, and a replayed copy is already invalid server-side.
            attached == null && eligible -> attach(request, accountId)
        }
    }
}

/**
 * Write `refreshToken=[token]` into the request's single `Cookie` line, replacing any earlier value.
 *
 * RFC 6265 allows exactly one `Cookie` header and servers and proxies disagree about two, so this
 * folds into the existing line rather than appending a second — the same rule [applyCustomHeaders]
 * follows. Ours goes **last**, so a gateway in front of the origin reads its own segments without
 * parsing past the app's.
 */
@Suppress("NoGetOutsideModuleDefinition") // Ktor's StringValuesBuilder.getAll, not Koin's.
private fun HeadersBuilder.putRefreshCookie(token: String) {
    val kept = otherCookieSegments()
    remove(HttpHeaders.Cookie)
    append(HttpHeaders.Cookie, (kept + "$REFRESH_COOKIE_NAME=$token").joinToString("; "))
}

/** Unpick only the app's own segment, so a user's gateway cookie survives the strip. */
@Suppress("NoGetOutsideModuleDefinition") // Ktor's StringValuesBuilder.getAll, not Koin's.
private fun HeadersBuilder.removeRefreshCookie() {
    val kept = otherCookieSegments()
    remove(HttpHeaders.Cookie)
    if (kept.isNotEmpty()) append(HttpHeaders.Cookie, kept.joinToString("; "))
}

@Suppress("NoGetOutsideModuleDefinition") // Ktor's StringValuesBuilder.getAll, not Koin's.
private fun HeadersBuilder.otherCookieSegments(): List<String> =
    getAll(HttpHeaders.Cookie).orEmpty()
        .flatMap(::cookieSegments)
        .filterNot { it.cookieName() == REFRESH_COOKIE_NAME }

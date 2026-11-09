package com.garfiec.librechat.core.network.client

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.network.RequestActivityTracker
import com.garfiec.librechat.core.common.result.AccessGatewayException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.failureKind
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.logging.LogOrigin
import com.garfiec.librechat.core.logging.redact.LogRedactor
import com.garfiec.librechat.core.model.error.ServerErrorCode
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.DefaultRequest.DefaultRequestBuilder
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import io.ktor.client.plugins.logging.Logger as KtorLogger

object LibreChatHttpClient {

    fun create(
        engineFactory: HttpClientEngineFactory<*>,
        json: Json,
        tokenManager: TokenManager,
        serverUrlProvider: ServerUrlProvider,
        redactor: LogRedactor,
        refreshCookieCredentials: RefreshCookieCredentials,
        accountReadyGate: AccountReadyGate? = null,
        switchGate: SwitchGate? = null,
        serverHeadersProvider: ServerHeadersProvider = EmptyServerHeadersProvider,
        requestActivityTracker: RequestActivityTracker? = null,
        debug: Boolean = false,
    ): HttpClient = HttpClient(engineFactory) {
        install(ContentNegotiation) {
            json(json)
        }

        install(Logging) {
            logger = object : KtorLogger {
                override fun log(message: String) {
                    // Route through the shared redactor so Logcat/NSLog get the same scrubbing as the
                    // persistent sink (tokens, JWTs, emails, hosts, IDs) — one redaction policy, not two.
                    Logger.d("HTTP") { redactor.redact(message) }
                }
            }
            level = if (debug) LogLevel.HEADERS else LogLevel.NONE
            // A user-configured gateway header is a secret under a name only the user knows, so
            // LogRedactor's pattern matching can't recognise it the way it recognises `Bearer`. Gate
            // it here, where the name is known, instead. Pre-emptive: `level` is NONE in release
            // builds, so nothing reaches the logger today — this keeps a future `debug = true` from
            // silently starting to print the credential.
            //
            // Matched against the CONFIGURED names only. "Any name that would be a legal custom
            // header" is every RFC-7230 token outside RESERVED_NAMES — Location, Set-Cookie,
            // Retry-After, X-Request-Id — i.e. exactly the fields someone turns HEADERS logging on to
            // read, and redacting them makes the log useless for the gateway problems it exists for.
            sanitizeHeader { name ->
                serverHeadersProvider.headersFor(serverUrlProvider.getBaseUrl())
                    .keys.any { it.equals(name, ignoreCase = true) }
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 120_000
        }

        // First HttpSend interceptor installed, so it is the outermost one: a call that retries,
        // redirects or replays after a token refresh counts once, not once per wire send.
        if (requestActivityTracker != null) {
            install(RequestActivityPlugin) {
                this.tracker = requestActivityTracker
            }
        }

        install(HttpRequestRetry) { configureRetryPolicy() }

        install(AuthInterceptorPlugin) {
            this.tokenManager = tokenManager
            // Host-scope the bearer token so a presigned absolute-URL fetch to a
            // third-party CDN (S3/CloudFront file download-url) never leaks the
            // LibreChat session token to that host.
            this.serverUrlProvider = serverUrlProvider
        }

        // BETWEEN the auth plugin and the header plugin, and nowhere else. Same-phase `State`
        // interceptors run in install order, so the app's `Cookie: refreshToken=` is on the request
        // before applyCustomHeaders folds the user's gateway cookie into the same line — which is what
        // keeps it to one Cookie header and lets the app's segment win a name collision.
        install(OpenIdCookiePlugin) {
            this.serverUrlProvider = serverUrlProvider
        }
        install(ImageCookiePlugin) {
            this.credentials = refreshCookieCredentials
            this.serverUrlProvider = serverUrlProvider
        }

        // Installed AFTER AuthInterceptorPlugin so its HttpSend interceptor is the inner one: it then
        // runs on every actual send, including the auth plugin's post-refresh 401 retry.
        install(ServerHeadersPlugin) {
            this.serverHeadersProvider = serverHeadersProvider
            this.serverUrlProvider = serverUrlProvider
        }

        // Outside GatewayDetectionPlugin, so a gateway's challenge is reported before a redirect is.
        install(ServerProbePlugin)

        // Must see the gateway's own 302, so it has to be inside the redirect loop — following that
        // redirect yields a 200 sign-in page, which no status-based check can catch.
        install(GatewayDetectionPlugin)

        // The SwitchBarrierPlugin (when a SwitchGate is wired) captures a consistent
        // (url, bearer, account) snapshot per request and resolves the URL against it, subsuming
        // ServerUrlReadyPlugin's cold-start await. Without a gate (tests) fall back to the plain
        // readiness plugin so a cold-start request can't be built against an empty base URL.
        if (switchGate != null) {
            install(SwitchBarrierPlugin) {
                this.switchGate = switchGate
            }
        } else {
            install(ServerUrlReadyPlugin) {
                this.serverUrlProvider = serverUrlProvider
                this.accountReadyGate = accountReadyGate
            }
        }

        HttpResponseValidator {
            validateResponse { response ->
                if (!response.status.isSuccess()) {
                    val statusCode = response.status.value
                    val bodyText = try {
                        response.bodyAsText()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        ""
                    }
                    val extracted = extractDisplayError(json, response, bodyText, statusCode)
                    val isBanned = isLibreChatBan(statusCode, extracted)

                    Diag.w(
                        "HTTP",
                        origin = LogOrigin.SERVER,
                        attrs = mapOf(
                            "status" to statusCode.toString(),
                            "path" to response.call.request.url.encodedPath,
                            "method" to response.call.request.method.value,
                        ),
                    ) { "HTTP $statusCode" }

                    if (isBanned) {
                        // Scoped to the request's snapshot account: a ban landing on a switched-away
                        // account's straggler request must not be reported against the live one. A pending
                        // add-flow request passes through entirely (its accountId is null, which
                        // emitSessionExpired treats as "active session, always emit") — a ban from
                        // the server being ADDED belongs to the add flow, mirroring the 401 path.
                        val identity = response.call.request.attributes.getOrNull(RequestIdentityKey)
                        if (identity?.isPending != true) {
                            tokenManager.emitSessionExpired(identity?.accountId, SessionEndReason.BANNED)
                        }
                    }

                    if (statusCode == HttpStatusCode.Forbidden.value) reportAuthGate(tokenManager, response, bodyText)
                    throw ApiException(
                        statusCode = statusCode,
                        message = extracted.message,
                        isBanned = isBanned,
                        body = bodyText.ifBlank { null },
                        serverAuthored = extracted.serverAuthored,
                        retryAfterSeconds = response.headers[HttpHeaders.RetryAfter]
                            ?.trim()
                            ?.toLongOrNull(),
                    )
                }
            }
        }

        defaultRequest {
            applyBrowserDefaults()
            contentType(ContentType.Application.Json)
        }

        // Keep last: the innermost send interceptor, between the engine and the other plugins.
        installTransportRecovery()
    }

    /**
     * What a request's error says when its 401 could not be cleared by a refresh. English, like the
     * status fallbacks in [extractErrorMessage] — this module has no resources — and app-authored,
     * so `looksLikeUserMessage` is not consulted.
     */
    const val SESSION_RENEWAL_FAILED_MESSAGE = "Your session couldn't be renewed. Sign in again to continue."

    // Standard Chrome on Android UA string — ua-parser-js will detect "Chrome" as browser name
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /**
     * The message for an error response, plus whether it came from the body.
     *
     * The provenance flag matters downstream: server-authored text is screened before it is
     * rendered (a gateway can put an entire HTML login page in `message`), while this app's own
     * wording below is shown as-is.
     */
    private data class ExtractedError(val message: String, val serverAuthored: Boolean)

    /**
     * Turns a 403 that is one of the server's required steps (v0.8.9-rc1) into an [AuthGate]:
     * the 2FA enrollment gate every authenticated route answers, and the terms gate on writes.
     *
     * Keyed on the body's code, never on the status: the user-agent soft-ban, a ban, an access
     * gateway and a WAF all answer 403 too, and none of them is a step the user can take in the app.
     * Scoped to the request's account like the ban report; a pending add-flow request is the add
     * flow's own business (its sign-in answers the enrollment 200 itself).
     */
    private fun reportAuthGate(tokenManager: TokenManager, response: HttpResponse, bodyText: String) {
        val identity = response.call.request.attributes.getOrNull(RequestIdentityKey)
        if (identity?.isPending == true) return
        val accountId = identity?.accountId
        val setupToken = ServerErrorCode.twoFactorEnrollmentToken(bodyText)
        val gate = when {
            setupToken != null -> AuthGate.TwoFactorSetupRequired(accountId, setupToken)
            ServerErrorCode.isTermsNotAccepted(bodyText) -> AuthGate.TermsNotAccepted(accountId)
            else -> return
        }
        tokenManager.reportAuthGate(gate)
    }

    /**
     * True only for LibreChat's **own** ban response: `checkBan`'s `banResponse` answers
     * `403` with `{"message": "<the ban sentence>"}` — JSON, with the wording in a `message` field.
     *
     * Decided off [extractErrorMessage]'s result rather than re-parsing the body: that parse has
     * already run for this response, and its [ExtractedError.serverAuthored] flag *is* the "this
     * came out of a LibreChat JSON envelope" test — a proxy interstitial or an HTML error page
     * cannot set it, and neither can an empty body. (Extraction also reads the `error` /
     * `error_message` fallback keys, so the ban wording would be honoured there too; upstream's ban
     * answer uses `message`, which makes that inert breadth rather than a second shape to match.)
     *
     * This used to be `bodyText.contains("ban")` against the raw body, which matches any 403 whose
     * body happens to contain those three letters — including a reverse proxy's or WAF's block page.
     * Emitting [SessionEndReason.BANNED] for one of those tells the user their account is restricted
     * over a network-layer block (and once turned a temporary IP ban into a hard logout, #376).
     * Requiring LibreChat's own JSON shape means an unrecognised 403 fails the request without
     * reporting a ban.
     */
    private fun isLibreChatBan(statusCode: Int, extracted: ExtractedError): Boolean {
        if (statusCode != 403 || !extracted.serverAuthored) return false
        return BAN_MESSAGE_MARKERS.all { extracted.message.contains(it, ignoreCase = true) }
    }

    /**
     * Both halves of `checkBan`'s message ("Your account has been temporarily banned due to
     * violations of our service.") must be present, so an unrelated server message that merely says
     * "banned" does not qualify. Registered in `scripts/mirrors.json`: upstream rewording this is
     * silent here — nothing fails to decode, the app just stops recognising a ban.
     */
    private val BAN_MESSAGE_MARKERS = listOf("banned", "account")

    /**
     * [extractErrorMessage], except for a 401 the auth plugin could not clear with a refresh: that one
     * is about the session, and the server's wording (`jwt expired`) describes the token rather than
     * anything the user can act on. The plugin sets [SessionRenewalFailedKey] on the call it hands
     * back (this validator is Ktor's default-installed one, so the outermost sender and the last to
     * see it); a 401 on an auth route never carries it.
     */
    private fun extractDisplayError(
        json: Json,
        response: HttpResponse,
        body: String,
        statusCode: Int,
    ): ExtractedError {
        val renewalFailed = response.call.request.attributes.getOrNull(SessionRenewalFailedKey) == true
        val movedTo = redirectedServer(response)
        return if (statusCode == HttpStatusCode.Unauthorized.value && renewalFailed) {
            ExtractedError(SESSION_RENEWAL_FAILED_MESSAGE, serverAuthored = false)
        } else if (movedTo != null) {
            ExtractedError(
                "This server has moved to $movedTo. Sign in again at that address.",
                serverAuthored = false,
            )
        } else {
            extractErrorMessage(json, body, statusCode)
        }
    }

    /**
     * `scheme://host[:port]` of another server a non-GET was redirected to: Ktor follows only GET and
     * HEAD, so a stored address the server now redirects fails every POST. Never the path or query (a
     * gateway URL carries a `meta=` JWT). Only when the Location keeps the request's `/api/…` path: a
     * forward-auth portal (`https://auth.example.com/login?rd=…`) is a sign-in wall, not the server's
     * new address. A root-relative Location (upstream's `redirect('/login')`) is the token store's to
     * classify, so it gets no message here.
     */
    private fun redirectedServer(response: HttpResponse): String? {
        val request = response.call.request
        if (response.status.value !in 300..399 || request.method == HttpMethod.Get || request.method == HttpMethod.Head) {
            return null
        }
        val location = response.headers[HttpHeaders.Location]?.takeIf { "://" in it } ?: return null
        val target = runCatching { Url(location) }.getOrNull()?.takeIf { it.host.isNotEmpty() } ?: return null
        val from = request.url
        val apiPath = from.encodedPath.let { path ->
            path.indexOf("/api/").let { if (it < 0) path else path.substring(it) }
        }
        if (!target.encodedPath.trimEnd('/').endsWith(apiPath.trimEnd('/'))) return null
        if (target.protocol == from.protocol && target.host.equals(from.host, ignoreCase = true) &&
            target.port == from.port
        ) {
            return null
        }
        val port = if (target.specifiedPort == 0 || target.specifiedPort == target.protocol.defaultPort) {
            ""
        } else {
            ":${target.specifiedPort}"
        }
        return "${target.protocol.name}://${target.host}$port"
    }

    private fun extractErrorMessage(json: Json, body: String, statusCode: Int): ExtractedError {
        if (body.isNotBlank()) {
            try {
                val jsonObj = json.parseToJsonElement(body).jsonObject
                val msg = (jsonObj["message"] as? JsonPrimitive)?.content
                    ?: (jsonObj["error"] as? JsonPrimitive)?.content
                    ?: (jsonObj["error_message"] as? JsonPrimitive)?.content
                if (!msg.isNullOrBlank()) return ExtractedError(msg, serverAuthored = true)
            } catch (_: Exception) {
                if (body.contains("banned", ignoreCase = true) || body.contains("forbidden", ignoreCase = true)) {
                    return ExtractedError(
                        "Access denied. Your account may have been restricted.",
                        serverAuthored = false,
                    )
                }
            }
        }

        val fallback = when (statusCode) {
            400 -> "Bad request"
            403 -> "Access denied"
            404 -> "Not found"
            429 -> "Too many requests. Please try again later."
            in 500..599 -> "Server error. Please try again later."
            else -> "Request failed (HTTP $statusCode)"
        }
        return ExtractedError(fallback, serverAuthored = false)
    }
}

private fun HttpClientConfig<*>.installTransportRecovery() {
    val platformVerdict = platformTransportVerdict ?: return
    install(TransportRecoveryPlugin) { verdict = platformVerdict }
}

/**
 * Retry policy shared by production and the guard test so they can't drift. Replays only
 * side-effect-free methods: a retried POST/PATCH would mint a duplicate chat job + orphaned
 * generation or a duplicate upload, and a retried DELETE that already succeeded server-side would
 * surface a spurious 404. The allow-list means an unknown or newly added method defaults to *not*
 * retried. Below this, a request whose connection fails before a response can still be resent: on
 * Android by OkHttp, for any replayable body, POST included (multipart counts, `ReplayableUploadBodies`);
 * on iOS only a multipart upload, by [TransportRecoveryPlugin].
 */
internal fun HttpRequestRetryConfig.configureRetryPolicy() {
    // A 5xx can arrive as a response (retryIf) or, once HttpResponseValidator throws, as an
    // ApiException (retryOnExceptionIf) — so both predicates carry the same method and status gate,
    // whichever path a non-2xx takes.
    retryIf(maxRetries = 2) { request, response ->
        request.method.isRetrySafe() && NoServerErrorRetry !in request.attributes &&
            response.status.value.isTransientServerError()
    }
    retryOnExceptionIf(maxRetries = 2) { request, cause ->
        request.method.isRetrySafe() &&
            (NoServerErrorRetry !in request.attributes || cause !is ApiException) &&
            cause !is kotlinx.coroutines.CancellationException &&
            (cause !is ApiException || cause.statusCode.isTransientServerError()) &&
            // Deterministic until the user edits the credential, so retrying only delays the report.
            // Excluded here rather than by install order: this plugin is installed first and so runs
            // outermost, which it must stay for the transient failures it exists to absorb.
            cause !is AccessGatewayException &&
            cause !is ServerRedirectException &&
            // Same for a certificate the device doesn't trust, and for cleartext the platform won't send.
            cause.failureKind().let { it != FailureKind.Tls && it != FailureKind.CleartextBlocked }
    }
    exponentialDelay()
}

/**
 * Marks a request whose caller owns its 5xx backoff, so [configureRetryPolicy] sends it once on a
 * server error (a transport failure still retries). Set it with [noServerErrorRetry].
 */
internal val NoServerErrorRetry = AttributeKey<Unit>("NoServerErrorRetry")

/** See [NoServerErrorRetry]. */
internal fun HttpRequestBuilder.noServerErrorRetry() {
    attributes.put(NoServerErrorRetry, Unit)
}

private val RETRY_SAFE_METHODS = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

private fun HttpMethod.isRetrySafe(): Boolean = this in RETRY_SAFE_METHODS

/**
 * A 5xx that a second attempt could plausibly fix. 501 (route has no strategy for this server, e.g.
 * `download-url` on local storage) and 505 are verdicts about the server, not momentary failures.
 */
private fun Int.isTransientServerError(): Boolean =
    this in 500..599 && this != HttpStatusCode.NotImplemented.value &&
        this != HttpStatusCode.VersionNotSupported.value

/**
 * Applies the browser [User-Agent][LibreChatHttpClient.BROWSER_USER_AGENT] that the stock LibreChat
 * server's `ua-parser-js` middleware requires — a single audited definition point shared by the main
 * and streaming clients so the browser-UA invariant lives in one place. The refresh client
 * deliberately omits this (its `/auth/refresh` route is UA-ungated).
 *
 * No URL here: [SwitchBarrierPlugin] or [ServerUrlReadyPlugin] resolves every relative request
 * against the deployment root before this runs, and `DefaultRequest`'s own merge would apply
 * different rules to a subpath base.
 */
internal fun DefaultRequestBuilder.applyBrowserDefaults() {
    headers.append(HttpHeaders.UserAgent, LibreChatHttpClient.BROWSER_USER_AGENT)
}

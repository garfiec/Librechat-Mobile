# core:network

Ktor clients, API services (`api/`, thin HTTP wrappers, no business logic), SSE client (`sse/`), auth
and gateway plugins (`client/`). Clients: **main** (`LibreChatHttpClient.kt`), **streaming** and
**refresh** (`di/NetworkModule.kt`). `TokenManager` and `ServerUrlProvider` are interfaces here,
implemented in `:core:data`.

## Rules

- **`arg`-wrapped bodies** (`{ "arg": { ... } }`): build with a `@Serializable` wrapper (see
  `ConvoUpdateBody`) or `buildJsonObject`, **never a `Map<String, Any>`** — Ktor picks a map's
  serializer from its values, so mixed value types throw before sending (and `safeApiCall` turns it
  into a generic error). A map is safe only when every value has one type.
- Shared `Json`: `ignoreUnknownKeys`, `isLenient`, `encodeDefaults = false`, `explicitNulls = false`,
  `coerceInputValues`. `socketTimeoutMillis = 120_000`; the streaming client uses `Long.MAX_VALUE`.
- Browser-like `User-Agent` required (root CLAUDE.md, Backend Quirks).
- Non-2xx becomes an exception in `HttpResponseValidator`; repositories catch via `safeApiCall`
  (`core/common/CLAUDE.md`). Respect `429` + `Retry-After` on auth endpoints.
- **Retry** (`configureRetryPolicy`): GET/HEAD/OPTIONS are re-sent twice on a transient 5xx (not
  501/505) or transport failure. A caller with its own 5xx backoff opts out with
  `noServerErrorRetry()`, a request attribute both predicates read (never match by path: subpath
  deployments); today only `CodeEnvironmentsApi.status`. Transport failures still retry.
- **TLS failure (`FailureKind.Tls`) is terminal** in `configureRetryPolicy` and `SseClient`, Android
  only, and only from the certificate exception *beneath* the TLS failure: a bare
  `SSLHandshakeException` can be a mid-handshake reset or `https://` to a plain-HTTP port and must stay
  retryable. iOS never classifies it (Darwin's exception is untyped; the iOS SSE transport maps
  `nw_error_domain_tls` to a plain `Exception`, retried normally).
- **User-installed CAs are trusted on Android** (`app/src/main/res/xml/network_security_config.xml`).
  `CertificateTrustListener` (OkHttp `EventListener` on every client, via `CertificateTrustOkHttp`)
  records user-added trust anchors in `CertificateTrustRegistry` per host:port; sign-in and About show
  a notice. Informational only: nothing is blocked, anchor changes aren't flagged (#36). iOS records
  nothing.

## URLs: the deployment root

**The cleaned base path is the deployment root; every request path is appended to it.**
`https://host/librechat` + `api/config` → `https://host/librechat/api/config`.

- Ktor requests resolve through `resolveAgainstDeploymentRoot` (`client/DeploymentRoot.kt`), called by
  `SwitchBarrierPlugin` (main + streaming) or `ServerUrlReadyPlugin` (refresh; main when no `SwitchGate`
  is wired). **Never install both on one client.** **No `defaultRequest` may carry a URL**: Ktor's RFC
  3986 merge drops the base's last segment and lets an absolute path replace it, breaking subpath
  deployments.
- Every hand-built URL (refresh, `ImageUrlResolver`, image-gen output, file, share, SSO, iOS SSE)
  joins onto `cleanServerUrl(baseUrl)` (`:core:common`), the same root the resolver uses;
  `isSecuredImagePath` matches `deploymentRootSegments`. Never join onto the raw stored string: it can
  be a pasted web-UI page (`https://host/login?redirect_to=…`), which 4xxs the refresh for ever.
  `cleanServerUrl` strips query, fragment and one recognised web route and keeps any other path.
- **Identity uses the raw string, unchanged**: `deriveServerId`, `RequestIdentity.baseUrl`,
  `PinnedServerBaseUrlKey`, `ServerHeadersPlugin`'s header key. Pass it through (`pinnedBaseUrl`).
- A stored base with an unrecognised junk segment (`https://host/chat` on a host-root deployment)
  404s; re-entering it on the server screen repairs it (`feature/auth/CLAUDE.md`).

## SSE Streaming Architecture

**Two layers of buffering are worked around. Don't undo either.**

1. **Ktor `client.get()` materializes the whole body (both platforms).** Stream with
   `prepareGet { } + execute { it.bodyAsChannel() }`.
2. **NSURLSession withholds `didReceiveData` for `text/*` (iOS only)** until ~512 bytes or close
   (`application/json` / `octet-stream` are exempt; KTOR-6378, unresolved). LibreChat always sends
   `text/event-stream`, so streaming would look frozen. Not fixable in Ktor or `commonMain`; layer 1's
   fix doesn't reach it.

`expect class SseHttpTransport`, bound in `networkPlatformModule` (with the engine factory; the rest
of `networkModule` is common):
- `SseHttpTransport.android.kt` — Ktor `prepareGet + execute` shim.
- `SseHttpTransport.ios.kt` — HTTP/1.1 over `Network.framework` `NWConnection`, for the SSE GET only;
  all other iOS traffic uses Darwin. Parsing in common `HttpResponseParser.kt`.
- `SseClient` — retry, connectivity, mapper reset, SKIE-safe errors.

**Don't use Ktor's SSE plugin**: it rides the same `NSURLSessionDataTask` path, so layer 2 applies.

**Do NOT remove the cinterop bridge** `src/iosMain/cinterop/nwparams_defaults.def`. The Kotlin/Native
binding of `NW_PARAMETERS_DEFAULT_CONFIGURATION` wraps the block in a Kotlin lambda, which crashes in
`block_destroy_helper` on a non-NSObject Swift `ProtocolOptions<TLSProtocol>`; the `.def` calls the
macro in C so the block stays in Network.framework's memory management.

### Protocol and resume

- Two-phase: `POST /api/agents/chat` → `{ streamId }` (= conversationId), then
  `GET /api/agents/chat/stream/:streamId`. Legacy single-phase (Assistants): the POST body is the
  stream. Both go through `SseClient`. Reconnect appends `?resume=true` (answered with a `sync` frame);
  abort is `POST /api/agents/chat/abort`.
- **A body that ends without a final frame is a drop, not the run's end** (servers before v0.8.9-rc1 are
  silent on any quiet stream, so idle proxies close live ones; the server's `res.destroy()` on a
  publication failure also means reconnect). `SseClient` resumes it on
  the retry ladder (1/2/4/8/16 s, max 5). Only a 404, an in-band error or a Final ends the loop, so a
  flow completing with no event means "job gone" (or account switched), never "socket closed".
- **Progress that resets the attempt counter is a parsed event, never a byte**, so a 200 with no events
  (captive portal) climbs the ladder instead of looping forever. At the ceiling emit
  `Error(code = RETRY_EXHAUSTED)`, not a network error: the consumer checks `/chat/status`.
- **v0.8.9-rc1 keepalive** (`:\n\n` every `streamKeepaliveIntervalMs`, default 25 s, `0` = off):
  `SseLineParser` skips it but it restarts the 120 s stall timeout. Don't lower that timeout (older
  servers and disabled keepalive still go quiet). A keepalive is not a parsed event and doesn't reset
  the attempt counter. iOS: the receive loop asks for a minimum of one byte and `HttpResponseParser`
  passes a lone 3-byte chunk through.
- Match stream errors on the **cause chain**, never by exception type: `accessGatewayCause()`,
  `httpStatusCause()`, `sseStreamCause()`. Ktor re-throws a cancelled channel's cause wrapped in
  `ClosedByteChannelException` depending on which side loses a race, so a type-exact `catch` works
  only most of the time.

## Custom per-server (gateway) headers

`ServerHeadersPlugin` attaches the user's static gateway headers (e.g. Cloudflare Access service
tokens) on **all three** clients. Store: `ServerRepository` (`core/data/CLAUDE.md`). iOS SSE gets them
from the `RequestIdentity` snapshot (rendered by `sseCustomHeaderLines`).

- **Must NOT reuse `AuthInterceptorPlugin`'s `skipPaths`**: that would keep the gateway credential off
  `auth/login`/`auth/refresh` and make sign-in impossible.
- **The cross-authority strip must stay in the `HttpSend` interceptor.** `HttpRedirect` copies every
  header to the redirect target (stripping only `Authorization`) and re-executes below the request
  pipeline, so a `State`-phase host check can't see the new host. Reachable via
  `FilesApi.downloadFromUrl`.
- Scoping is `isSameServerAuthority` (scheme + host + port, fail-closed), stricter than the bearer's
  `isSameHostAsServer` (`HostScoping.kt`): the token is long-lived, so no `http://` downgrade or unknown
  base may carry it.
- Editor: `CustomHeadersEditor` in `:core:ui` (strings in its `composeResources`), shared by
  `feature/auth` and `feature/settings`. `:core:ui` must not depend on `:core:network`; callers map
  `HeaderRejection` → `CustomHeaderRowError`. Values are masked with a per-row reveal keyed by row
  index, and adding/removing a row re-masks all (no stale index aliasing onto another credential). It
  branches on its own width (`BoxWithConstraints`, stacks below 420dp), not device or caller flag.

### Detecting the gateway

`GatewayDetectionPlugin` turns a rejection into `AccessGatewayException`, on **all three** clients;
`AccessGatewaySignal` is the shared predicate.

- **Must stay an `HttpSend` interceptor.** The validator is outermost and sees only the final 200; the
  302 with `WWW-Authenticate: Cloudflare-Access` is visible only inside the redirect loop.
- **Never read the body** (runs for streaming responses too).
- **Read every `WWW-Authenticate` line.** Ktor scans `getAll(...)`; `HttpResponseParser` folds repeats
  into one comma-separated value (RFC 7230 §3.2.2). Ktor's `headers[name]` returns the first line, a
  naive parser keeps the last; either alone misses a second-line challenge.
- **Install on every new client.**
- **Cloudflare Access only, deliberately.** Cookie-based gateways (Authelia, oauth2-proxy) send no
  comparable header and get the generic message. Inferring a gateway from off-authority `text/html`
  would misfire on legitimate redirects (presigned downloads). Reconsider only on a real report.
- **Terminal, never retried — three separate opt-outs**: `SseClient` and the refresh loop each exit on
  it, and `configureRetryPolicy` excludes it in `retryOnExceptionIf` (`HttpRequestRetry` is installed
  first, so it's outermost and sees the throw). Exclude by predicate, not by install order.
- **iOS SSE transport** carries its own `AccessGatewaySignal` check **before** its status check, or the
  rejection becomes a bare `SseHttpStatusException(302)` retried five times.
- **A gateway block is not an expired session.** Refresh maps it to `RefreshAttempt.GatewayBlocked` →
  `Transient`, never through `settle()`; nothing is surfaced there (the main client raises it on the
  user's screen).

### 403s, bans and renewal failure

- **A 403 not attributable to LibreChat never ends the session.** The validator's ban check requires
  LibreChat's `banResponse` shape (403 + JSON `message` with the ban wording; `ban-response-message`
  in `scripts/mirrors.json`), never a substring match on the body — a WAF/CrowdSec page would log the
  user out with no way back in. An unrecognised 403 fails only the request. The refresh half (and
  `SessionHealth.RenewalBlocked`) is in `core/data/CLAUDE.md`.
- **A genuine ban doesn't end the session client-side either.** Upstream's `checkBan` guards only some
  routes, so `BANNED` shows a once-per-account "Account restricted" dialog; the server's own session
  deletion later surfaces as a normal `EXPIRED`. So **`BANNED` must not set the store's one-shot
  `sessionExpiryReported` latch**, or that later expiry is swallowed.
- A 401 `AuthInterceptorPlugin` couldn't clear is tagged `SessionRenewalFailedKey` so the validator
  shows `SESSION_RENEWAL_FAILED_MESSAGE`. Put the tag on the **call's** request attributes, not the
  builder's (they differ).

## The image cookie (`/images/*`, upstream 0.8.8-rc2+)

The server's image mount authenticates by **cookie only**. `ImageCookiePlugin` attaches the account's
refresh token to exactly that mount; `isSecuredImagePath` decides what that is. Server side and the
iOS cookie-jar guardrail: `DISCOVERY.md`.

- **`/images/` must NOT go in `AUTH_SKIP_PATHS`.** That set also suppresses `SwitchBarrierPlugin`'s
  proactive renewal and is a whole-path-suffix matcher. The image mount needs only "its 401 is its own
  verdict", handled separately in the 401 leg; the bearer stays attached.
- **Install between `AuthInterceptorPlugin` and `ServerHeadersPlugin`, main client only** (Coil uses
  it). Same-phase `State` interceptors run in install order, so the app cookie is set before
  `applyCustomHeaders` merges the user's gateway cookie into one `Cookie` header, and the app's segment
  wins a name collision. Streaming and refresh clients must not carry it.
- **The cross-authority strip stays in `HttpSend`** (same reason as gateway headers;
  `stripCustomHeaders` removes only the user's segments), or an off-domain 302 leaks the refresh token.
- **Never cache the token.** The server matches `session.refreshTokenHash`, which each refresh
  overwrites, and rejects a stale one with **403**. Read at attach time keyed to the snapshot's
  `accountId`; the one-shot 403 retry resends only if the token changed. Keep it off `RequestIdentity`
  and `TokenManager` (`RefreshCookieCredentials` is its own seam).
- Gate: `isSameServerAuthority` plus a **base-path-aware** match (upstream mounts
  `${basePath}/images`). A null or pending account attaches nothing.

## OpenID token reuse

`OpenIdCookiePlugin` (main, streaming, refresh; before `ServerHeadersPlugin`) attaches
`token_provider=openid` and the opaque `openid_user_id` from the request's own identity, **never** the
active account in place of a pending or captured one.

- Only explicit refresh, logout and image requests send a refresh token, always as the complete
  bundle via `attachAuthCookies`.
- Redirect sends strip app cookies outside the captured authority or deployment root. Gateway cookies
  merge into one header; app-owned values win.
- `CookieHelper.update` preserves omitted cookies and rejects an OpenID refresh rotation that lacks
  its replacement user marker. The server reissues the marker whenever it rotates the refresh token;
  its session-reuse 200 sets no cookies, so keep the previous marker. **Treat the marker as opaque**:
  don't reconstruct, decode or independently persist it.
- Parse response cookies with Ktor's `HttpMessage.setCookie()`: Foundation combines repeated fields
  and `Expires` contains commas.
- `refreshBearerFor` updates bearer and marker on retry. The raw iOS SSE path bypasses the plugins and
  renders through `sseCustomHeaderLines`, including after renewal.
- **Never add a process-wide cookie jar.**

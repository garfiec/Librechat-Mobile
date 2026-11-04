package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import kotlinx.coroutines.flow.first

/**
 * The one place that decides how an MCP OAuth sign-in is opened, for Settings' reconnect and the
 * in-chat prompt alike.
 *
 * v0.8.9-rc1 (upstream d349120ec) removed the callback's fallback to a pending flow: it now
 * redirects to `/oauth/error?error=csrf_validation_failed` unless the browser carries the
 * `oauth_csrf` or `oauth_session` cookie. Those are set only on the response to an authenticated
 * request, and this app's HTTP client keeps no jar, so on those servers the cookies from
 * `POST /oauth/bind` are seeded into an in-app WebView ([McpOAuthLaunch.InApp]). A known older
 * server keeps the system browser ([McpOAuthLaunch.External]), where the pending-flow fallback lets
 * the callback through. See VERSION_GATES.md.
 *
 * An unknown version tries the bind too, but the route only exists from upstream v0.8.3-rc1. On an
 * older server the unmatched POST falls through to the SPA catch-all, which answers 200 `text/html`
 * with no `Set-Cookie`, so a bind that succeeds without an `oauth_csrf`/`oauth_session` cookie means
 * the route is missing and the server predates the cookie check. A 404 (a proxy or a deployment
 * without the catch-all; the handler itself never answers 404) means the same. Both fall back to
 * the system browser rather than failing the sign-in: an unseeded WebView has nothing the browser
 * lacks.
 *
 * Bound at the moment of the tap, never earlier: the CSRF cookie lives ten minutes, and a later
 * prompt for the same callback path replaces it.
 */
class McpOAuthLauncher(
    private val mcpRepository: McpRepository,
    private val configRepository: ConfigRepository,
) {
    suspend fun prepare(serverName: String, authorizationUrl: String): Result<McpOAuthLaunch> {
        if (!usesBoundWebView(configRepository.detectedBackendVersion.value)) {
            return Result.Success(McpOAuthLaunch.External(serverName, authorizationUrl))
        }
        return when (val bound = mcpRepository.bindOAuth(serverName)) {
            is Result.Success ->
                if (bound.data.isEmpty()) {
                    Result.Success(McpOAuthLaunch.External(serverName, authorizationUrl))
                } else {
                    Result.Success(
                        McpOAuthLaunch.InApp(
                            serverName = serverName,
                            authorizationUrl = authorizationUrl,
                            serverUrl = configRepository.serverUrl.first(),
                            seedCookies = bound.data,
                        ),
                    )
                }
            is Result.Error ->
                if (bound.isRouteMissing()) {
                    Result.Success(McpOAuthLaunch.External(serverName, authorizationUrl))
                } else {
                    bound
                }
            Result.Loading -> Result.Error(message = null)
        }
    }

    private fun Result.Error.isRouteMissing(): Boolean =
        (exception as? ApiException)?.statusCode == HTTP_NOT_FOUND

    companion object {
        const val BROWSER_BINDING_MIN_VERSION = "0.8.9-rc1"
        private const val HTTP_NOT_FOUND = 404

        fun usesBoundWebView(version: String?): Boolean =
            version == null || BackendVersion.isCompatibleOrNewer(version, BROWSER_BINDING_MIN_VERSION)
    }
}

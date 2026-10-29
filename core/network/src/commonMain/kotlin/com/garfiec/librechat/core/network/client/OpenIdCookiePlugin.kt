package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.util.AttributeKey

private val ExplicitAuthCookiesKey = AttributeKey<AuthCookies>("ExplicitAuthCookies")
private val AuthCookieBaseUrlKey = AttributeKey<String>("AuthCookieBaseUrl")

/** Explicit refresh/logout/image credentials, also tracked so redirects can remove them safely. */
fun HttpRequestBuilder.attachAuthCookies(cookies: AuthCookies, baseUrl: String? = null) {
    attributes.put(ExplicitAuthCookiesKey, cookies)
    baseUrl?.let { attributes.put(AuthCookieBaseUrlKey, it) }
    CookieHelper.merge(headers, cookies.values())
}

private fun markerValues(markers: OpenIdMarkers?): Map<String, String?> = mapOf(
    "token_provider" to markers?.let { "openid" },
    "openid_user_id" to markers?.userIdCookie,
)

private fun AuthCookies.values(): Map<String, String?> = mapOf("refreshToken" to refreshToken) + markerValues(openId)

/** Installs before gateway headers, on every LibreChat Ktor transport. Never uses a global cookie jar. */
class OpenIdCookiePlugin private constructor(private val serverUrlProvider: ServerUrlProvider?) {
    class Config { var serverUrlProvider: ServerUrlProvider? = null }

    companion object : HttpClientPlugin<Config, OpenIdCookiePlugin> {
        override val key = AttributeKey<OpenIdCookiePlugin>("OpenIdCookies")
        override fun prepare(block: Config.() -> Unit): OpenIdCookiePlugin =
            OpenIdCookiePlugin(Config().apply(block).serverUrlProvider)

        override fun install(plugin: OpenIdCookiePlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                plugin.apply(context)
            }
            scope.plugin(HttpSend).intercept { request ->
                // Runs per send, including refresh retries and each hop of a redirect chain.
                plugin.apply(request)
                execute(request)
            }
        }
    }

    private fun apply(request: HttpRequestBuilder) {
        val snapshot = request.attributes.getOrNull(RequestIdentityKey)
        val base = snapshot?.baseUrl?.takeIf { it.isNotEmpty() }
            ?: request.attributes.getOrNull(AuthCookieBaseUrlKey)
            ?: request.attributes.getOrNull(PinnedServerBaseUrlKey)
            ?: serverUrlProvider?.getBaseUrl()
        val explicit = request.attributes.getOrNull(ExplicitAuthCookiesKey)
        val values = explicit?.values() ?: markerValues(snapshot?.openId)
        val root = deploymentRootSegments(base)
        val path = request.url.encodedPathSegments.filter { it.isNotEmpty() }
        val eligible = isSameServerAuthority(request.url, base) && path.take(root.size) == root
        CookieHelper.merge(request.headers, if (eligible) values else values.mapValues { null })
    }
}

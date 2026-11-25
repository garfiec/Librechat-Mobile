package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.result.AccessGatewayException
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes

/** The gate credential currently on a request, so a redirect hop strips exactly that. */
internal val AppliedGateCredentialKey = AttributeKey<GateCredential>("AppliedGateCredential")

/** The credential this call carries to its own server, kept while an off-site hop has it stripped. */
internal val CallGateCredentialKey = AttributeKey<GateCredential>("CallGateCredential")

/**
 * Attaches the server's gate credential ([GateCredentialProvider]) to requests addressed to the
 * server's own scheme, host and port, and renews it once when the gate refuses it.
 *
 * Install on **every** client that talks to the server (main, streaming, refresh), **before**
 * [GatewayDetectionPlugin]: its `HttpSend` interceptor must be the outer one so it catches the
 * [AccessGatewayException] the detection throws for a refused credential.
 *
 * Mirrors [ServerHeadersPlugin]'s two phases: attach in `State` (the base URL is applied by then), and
 * strip or re-apply per redirect hop in `HttpSend`, because Ktor's redirect copies every header but
 * `Authorization` to the next host. The credential is read at attach time, never cached: a refresh
 * revokes the previous access token at once.
 */
class GateCredentialPlugin private constructor(
    private val provider: GateCredentialProvider,
    private val serverUrlProvider: ServerUrlProvider?,
) {
    class Config {
        var provider: GateCredentialProvider = NoGateCredentials

        /** Fallback base URL for requests with no snapshot and no pinned URL; null attaches nothing. */
        var serverUrlProvider: ServerUrlProvider? = null
    }

    companion object : HttpClientPlugin<Config, GateCredentialPlugin> {
        override val key = AttributeKey<GateCredentialPlugin>("GateCredential")

        override fun prepare(block: Config.() -> Unit): GateCredentialPlugin {
            val config = Config().apply(block)
            return GateCredentialPlugin(config.provider, config.serverUrlProvider)
        }

        override fun install(plugin: GateCredentialPlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                val baseUrl = plugin.baseUrlOf(context.attributes) ?: return@intercept
                if (!isSameServerAuthority(context.url, baseUrl)) return@intercept
                val credential = plugin.provider.credentialFor(baseUrl) ?: return@intercept
                context.headers.applyGateCredential(credential)
                context.attributes.put(AppliedGateCredentialKey, credential)
                context.attributes.put(CallGateCredentialKey, credential)
            }

            scope.plugin(HttpSend).intercept { request ->
                val baseUrl = plugin.baseUrlOf(request.attributes)
                val applied = request.attributes.getOrNull(AppliedGateCredentialKey)
                val sameAuthority = baseUrl != null && isSameServerAuthority(request.url, baseUrl)
                val forCall = request.attributes.getOrNull(CallGateCredentialKey)
                // Both directions: a chain can leave the server and come back (origin, object store,
                // origin), and the attach phase runs once per call, not per hop.
                if (applied != null && !sameAuthority) {
                    request.headers.stripGateCredential(applied)
                    request.attributes.remove(AppliedGateCredentialKey)
                } else if (applied == null && forCall != null && sameAuthority) {
                    request.headers.applyGateCredential(forCall)
                    request.attributes.put(AppliedGateCredentialKey, forCall)
                }
                val sent = request.attributes.getOrNull(AppliedGateCredentialKey)
                try {
                    execute(request)
                } catch (e: AccessGatewayException) {
                    // Only a refusal of a credential we sent can be fixed by renewing it; a server
                    // with none needs a sign-in, which the caller surfaces from the exception.
                    if (sent == null || baseUrl == null) throw e
                    val renewed = plugin.provider.onRejected(baseUrl, sent) ?: throw e
                    request.headers.stripGateCredential(sent)
                    request.headers.applyGateCredential(renewed)
                    request.attributes.put(AppliedGateCredentialKey, renewed)
                    request.attributes.put(CallGateCredentialKey, renewed)
                    execute(request)
                }
            }
        }
    }

    /** Same precedence as [ServerHeadersPlugin]: snapshot, then pinned URL, then the live provider. */
    private fun baseUrlOf(attributes: Attributes): String? =
        attributes.getOrNull(RequestIdentityKey)?.baseUrl?.takeIf { it.isNotEmpty() }
            ?: attributes.getOrNull(PinnedServerBaseUrlKey)?.takeIf { it.isNotEmpty() }
            ?: serverUrlProvider?.getBaseUrl()?.takeIf { it.isNotEmpty() }
}

/** Installs [GateCredentialPlugin]; call it before installing [GatewayDetectionPlugin]. */
fun HttpClientConfig<*>.installGateCredentials(provider: GateCredentialProvider, serverUrlProvider: ServerUrlProvider) {
    install(GateCredentialPlugin) {
        this.provider = provider
        this.serverUrlProvider = serverUrlProvider
    }
}

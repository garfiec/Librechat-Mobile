package com.garfiec.librechat.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.URLBuilder
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase

/**
 * Installs the [SwitchGate] as a per-request barrier: in a phase inserted *before*
 * [HttpRequestPipeline.Before], it captures the `(baseUrl, accountId, bearer)` snapshot, stores it in
 * the request attributes ([RequestIdentityKey]) for the auth phase to read, and resolves the request
 * URL against the snapshot's base URL right here ([resolveAgainstDeploymentRoot]).
 *
 * Resolving against the *snapshot* rather than the live provider closes the window where a switch
 * between URL resolution and the auth phase would send a request to the new server carrying the old
 * snapshot's bearer. No `defaultRequest` on these clients carries a URL, so nothing later in the
 * pipeline can overwrite it. Absolute (cross-host) request URLs already carry a host, so the base is
 * not applied to them.
 *
 * This barrier subsumes [ServerUrlReadyPlugin]'s role on the clients it is installed on:
 * [SwitchGate.captureSnapshot] awaits the account/URL readiness itself, and the two resolve through
 * the same function. Never install both on one client: whichever runs first sets the host and the
 * other becomes a no-op. The refresh client stays on
 * [ServerUrlReadyPlugin] (ungated) so a seed/switch-time token operation can't deadlock behind it.
 */
class SwitchBarrierPlugin private constructor(
    private val switchGate: SwitchGate,
) {
    class Config {
        lateinit var switchGate: SwitchGate
    }

    companion object : HttpClientPlugin<Config, SwitchBarrierPlugin> {
        override val key = AttributeKey<SwitchBarrierPlugin>("SwitchBarrier")
        private val SwitchBarrierPhase = PipelinePhase("SwitchBarrier")

        override fun prepare(block: Config.() -> Unit): SwitchBarrierPlugin =
            SwitchBarrierPlugin(Config().apply(block).switchGate)

        override fun install(plugin: SwitchBarrierPlugin, scope: HttpClient) {
            scope.requestPipeline.insertPhaseBefore(HttpRequestPipeline.Before, SwitchBarrierPhase)
            scope.requestPipeline.intercept(SwitchBarrierPhase) {
                val snapshot = plugin.switchGate.captureSnapshot(renewIfStale = shouldRenewFor(context.url))
                context.attributes.put(RequestIdentityKey, snapshot)
                if (snapshot.baseUrl.isNotEmpty()) {
                    resolveAgainstDeploymentRoot(context.url, snapshot.baseUrl)
                }
            }
        }

        /**
         * Whether this request should proactively renew a near-expired access token before being
         * sent. Both exclusions cover requests that will never carry the session bearer, so a refresh
         * on their behalf is at best wasted and at worst harmful:
         *
         * - **Auth endpoints** ([isAuthSkipPath]) — a sign-in or password-reset POST issued while an
         *   expired token is still stored would otherwise put a full refresh in front of the Sign in
         *   button. The bearer is deliberately never attached to these; renewing it is not this
         *   request's business.
         * - **Absolute URLs** — a request that already carries a host at this phase is a cross-host
         *   fetch (a presigned CDN download), which [AuthInterceptorPlugin] host-scopes the bearer
         *   away from anyway. A relative URL has no host yet and will be resolved against the
         *   snapshot's base below, so it is same-server by construction.
         *
         * An absolute URL that happens to *be* the server is excluded too — a missed head start on a
         * rare path, and it falls back to the reactive 401. Fail-safe is the right side to err on.
         */
        private fun shouldRenewFor(requestUrl: URLBuilder): Boolean =
            requestUrl.host.isEmpty() && !isAuthSkipPath(requestUrl)
    }
}

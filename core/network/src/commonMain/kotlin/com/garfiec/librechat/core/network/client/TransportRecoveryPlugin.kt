package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.result.CleartextBlockedException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.utils.unwrapCancellationException
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.io.IOException

internal enum class TransportVerdict {
    /** The connection died before the request was processed: a pooled one the server had closed. */
    StaleConnection,
    Network,
    Tls,
    CleartextBlocked,
}

/**
 * The platform's reading of its engine's failures, or null where the engine's own exceptions
 * already classify by name and recover by themselves (OkHttp).
 */
internal expect val platformTransportVerdict: ((Throwable) -> TransportVerdict?)?

/** A transport failure that `classifyFailure` reads as Network (its name contains "IOException"). */
internal class TransportIOException(cause: Throwable) : IOException("Transport failure", cause)

/** A transport failure that `classifyFailure` reads as Tls (its name contains "Certificate"). */
internal class TransportCertificateException(cause: Throwable) : Exception("Certificate failure", cause)

/**
 * Sends a multipart upload once more when its connection turned out to be one the server had
 * already closed, and gives the engine's untyped failures a type `classifyFailure` can read.
 * Install it last, so it is the innermost send interceptor and every outer plugin sees one outcome.
 * Only multipart is resent, as OkHttp does on Android: a duplicate (on a connection that died after
 * the server stored the request) is an extra file or import, where a chat start's is a second
 * generation.
 */
internal class TransportRecoveryPlugin private constructor(private val verdict: (Throwable) -> TransportVerdict?) {

    class Config {
        var verdict: ((Throwable) -> TransportVerdict?)? = null
    }

    companion object : HttpClientPlugin<Config, TransportRecoveryPlugin> {
        override val key = AttributeKey<TransportRecoveryPlugin>("TransportRecovery")

        override fun prepare(block: Config.() -> Unit): TransportRecoveryPlugin =
            TransportRecoveryPlugin(checkNotNull(Config().apply(block).verdict) { "verdict is required" })

        // Any engine failure is read; the first attempt's is dropped only when the upload is resent.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        override fun install(plugin: TransportRecoveryPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                var resent = false
                while (true) {
                    try {
                        return@intercept execute(request)
                    } catch (e: Exception) {
                        // A cancelled or timed-out request is not a transport failure.
                        currentCoroutineContext().ensureActive()
                        if (!request.executionContext.isActive) throw e
                        val cause = e.unwrapCancellationException()
                        when (plugin.verdict(cause)) {
                            TransportVerdict.StaleConnection -> {
                                if (resent || !request.isMultipart()) throw TransportIOException(cause)
                                resent = true
                            }
                            TransportVerdict.Network -> throw TransportIOException(cause)
                            TransportVerdict.Tls -> throw TransportCertificateException(cause)
                            TransportVerdict.CleartextBlocked -> throw CleartextBlockedException(cause)
                            null -> throw e
                        }
                    }
                }
                error("the loop only exits by returning or throwing")
            }
        }

        private fun HttpRequestBuilder.isMultipart(): Boolean =
            (body as? OutgoingContent)?.contentType?.match(ContentType.MultiPart.Any) == true
    }
}

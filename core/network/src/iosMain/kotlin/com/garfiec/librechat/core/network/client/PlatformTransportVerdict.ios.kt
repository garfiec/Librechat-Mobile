package com.garfiec.librechat.core.network.client

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorAppTransportSecurityRequiresSecureConnection
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNetworkConnectionLost
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorRequestBodyStreamExhausted
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateNotYetValid

internal actual val platformTransportVerdict: ((Throwable) -> TransportVerdict?)? = ::darwinTransportVerdict

/**
 * A POST on a connection the server already closed fails with connection-lost, or with
 * body-stream-exhausted when NSURLSession would resend it but can't rewind Ktor's body stream;
 * either way the request wasn't processed. Tls is the server's certificate only: -1200 (secure
 * connection failed) also reports a reset mid-handshake, worth retrying, and the client-certificate
 * codes would get a message blaming the server's certificate.
 */
internal fun darwinTransportVerdict(cause: Throwable): TransportVerdict? {
    val error = (cause as? DarwinHttpRequestException)?.origin ?: return null
    if (error.domain != NSURLErrorDomain) return null
    return when (error.code) {
        NSURLErrorNetworkConnectionLost, NSURLErrorRequestBodyStreamExhausted -> TransportVerdict.StaleConnection
        NSURLErrorCannotFindHost,
        NSURLErrorCannotConnectToHost,
        NSURLErrorDNSLookupFailed,
        NSURLErrorNotConnectedToInternet,
        NSURLErrorInternationalRoamingOff,
        NSURLErrorDataNotAllowed,
        -> TransportVerdict.Network
        in NSURLErrorServerCertificateNotYetValid..NSURLErrorServerCertificateHasBadDate -> TransportVerdict.Tls
        NSURLErrorAppTransportSecurityRequiresSecureConnection -> TransportVerdict.CleartextBlocked
        else -> null
    }
}

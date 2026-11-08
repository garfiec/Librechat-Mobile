package com.garfiec.librechat.core.network.client

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorDomain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DarwinTransportVerdictTest {

    private fun darwin(code: Long, domain: String = checkNotNull(NSURLErrorDomain)) =
        DarwinHttpRequestException(NSError.errorWithDomain(domain, code, null))

    @Test
    fun connectionLostAndExhaustedBodyStreamReadAsAStaleConnection() {
        assertEquals(TransportVerdict.StaleConnection, darwinTransportVerdict(darwin(-1005)))
        assertEquals(TransportVerdict.StaleConnection, darwinTransportVerdict(darwin(-1021)))
    }

    @Test
    fun unreachableHostsAndNoConnectivityReadAsNetwork() {
        for (code in listOf(-1003L, -1004L, -1006L, -1009L, -1018L, -1020L)) {
            assertEquals(TransportVerdict.Network, darwinTransportVerdict(darwin(code)), "code $code")
        }
    }

    @Test
    fun serverCertificateErrorsReadAsTlsButClientCertificateAndFailedSecureConnectionDoNot() {
        for (code in -1204L..-1201L) {
            assertEquals(TransportVerdict.Tls, darwinTransportVerdict(darwin(code)), "code $code")
        }
        for (code in listOf(-1206L, -1205L, -1200L)) {
            assertNull(darwinTransportVerdict(darwin(code)), "code $code")
        }
    }

    @Test
    fun cleartextRefusedByAppTransportSecurityReadsAsCleartextBlocked() {
        assertEquals(TransportVerdict.CleartextBlocked, darwinTransportVerdict(darwin(-1022)))
    }

    @Test
    fun otherCodesDomainsAndExceptionsAreLeftAlone() {
        assertNull(darwinTransportVerdict(darwin(-1011)))
        assertNull(darwinTransportVerdict(darwin(-1005, domain = "NSPOSIXErrorDomain")))
        assertNull(darwinTransportVerdict(IllegalStateException("not a transport failure")))
    }
}

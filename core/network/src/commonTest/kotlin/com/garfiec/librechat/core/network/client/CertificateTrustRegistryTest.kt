package com.garfiec.librechat.core.network.client

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CertificateTrustRegistryTest {

    @Test
    fun a_recorded_host_is_found_from_the_server_URL() = runTest {
        val registry = CertificateTrustRegistry()
        registry.record("Chat.Example.com", 443, CertificateAuthority.UserInstalled)

        // Default port, a deployment path and a different host case all name the same server.
        assertEquals(
            CertificateAuthority.UserInstalled,
            registry.observe("https://chat.example.com/librechat").first(),
        )
    }

    @Test
    fun another_port_on_the_same_host_is_a_different_server() = runTest {
        val registry = CertificateTrustRegistry()
        registry.record("chat.example.com", 8443, CertificateAuthority.UserInstalled)

        assertNull(registry.observe("https://chat.example.com").first())
        assertEquals(CertificateAuthority.UserInstalled, registry.observe("https://chat.example.com:8443").first())
    }

    @Test
    fun plain_http_and_unparseable_URLs_never_report_trust() = runTest {
        val registry = CertificateTrustRegistry()
        registry.record("chat.example.com", 80, CertificateAuthority.UserInstalled)

        assertNull(registry.observe("http://chat.example.com").first())
        assertNull(registry.observe("").first())
    }

    @Test
    fun record_reports_only_a_change() {
        val registry = CertificateTrustRegistry()

        assertTrue(registry.record("chat.example.com", 443, CertificateAuthority.UserInstalled))
        assertFalse(registry.record("chat.example.com", 443, CertificateAuthority.UserInstalled))
        assertTrue(registry.record("chat.example.com", 443, CertificateAuthority.System))
    }
}

package com.garfiec.librechat.core.network.client

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Which kind of certificate authority vouched for a server's TLS certificate. */
enum class CertificateAuthority {
    /** One the OS ships with. */
    System,

    /** One installed on the device by the user or by device management. */
    UserInstalled,
}

/**
 * The certificate authority behind each server's TLS certificate, as seen on the most recent
 * connection to it in this process.
 *
 * Only Android records anything (an OkHttp event listener, see `networkPlatformModule`): iOS gives
 * no equivalent of `isUserAddedCertificate`, so lookups there stay null. Informational only: nothing
 * is blocked on it.
 */
class CertificateTrustRegistry {
    private val byAuthority = MutableStateFlow<Map<String, CertificateAuthority>>(emptyMap())

    /** Returns whether this changed what was recorded for the host. */
    fun record(host: String, port: Int, authority: CertificateAuthority): Boolean {
        val key = authorityKey(host, port)
        var changed = false
        byAuthority.update {
            changed = it[key] != authority
            if (changed) it + (key to authority) else it
        }
        return changed
    }

    /**
     * Null until a TLS connection to [serverUrl] has completed in this process, and always for an
     * `http://` URL, an unparseable one, or on iOS.
     */
    fun observe(serverUrl: String): Flow<CertificateAuthority?> {
        val key = httpsAuthorityKey(serverUrl) ?: return flowOf(null)
        return byAuthority.map { it[key] }.distinctUntilChanged()
    }

    private fun httpsAuthorityKey(serverUrl: String): String? {
        val url = runCatching { Url(serverUrl.trim()) }.getOrNull() ?: return null
        if (url.protocol != URLProtocol.HTTPS || url.host.isBlank()) return null
        return authorityKey(url.host, url.port)
    }

    private fun authorityKey(host: String, port: Int) = "${host.lowercase()}:$port"
}

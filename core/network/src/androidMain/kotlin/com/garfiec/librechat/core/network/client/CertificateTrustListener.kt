package com.garfiec.librechat.core.network.client

import android.net.http.X509TrustManagerExtensions
import com.garfiec.librechat.core.logging.Diag
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Records, for each new TLS connection, whether the certificate authority that vouched for the
 * server is one the user (or device management) installed. `network_security_config.xml` trusts
 * those alongside the system's, and this is how the app can say so.
 *
 * The handshake OkHttp hands [secureConnectEnd] is not the chain the server sent: its
 * [Handshake.peerCertificates] go through `AndroidCertificateChainCleaner`, which returns
 * `X509TrustManagerExtensions.checkServerTrusted`'s validated path, ending at the trust anchor. So
 * the last certificate is the authority to ask about even when the server leaves its root out.
 */
internal class CertificateTrustListener(
    private val registry: CertificateTrustRegistry,
) : EventListener() {

    private val extensions: X509TrustManagerExtensions? by lazy {
        runCatching {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?.let(::X509TrustManagerExtensions)
        }.getOrNull()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        val anchor = handshake?.let { runCatching { it.peerCertificates.lastOrNull() }.getOrNull() }
            as? X509Certificate ?: return
        val ext = extensions ?: return
        val userInstalled = runCatching { ext.isUserAddedCertificate(anchor) }.getOrElse {
            Diag.w("TLS", throwable = it) { "Couldn't tell whether the trust anchor is user-installed" }
            return
        }
        val authority = if (userInstalled) CertificateAuthority.UserInstalled else CertificateAuthority.System
        val url = call.request().url
        val changed = registry.record(host = url.host, port = url.port, authority = authority)
        if (changed && userInstalled) {
            Diag.i("TLS") { "Server certificate trusted via a user-installed CA" }
        }
    }
}

internal class CertificateTrustOkHttp(
    private val listener: EventListener,
) : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine = OkHttp.create {
        block()
        config { eventListener(listener) }
    }
}

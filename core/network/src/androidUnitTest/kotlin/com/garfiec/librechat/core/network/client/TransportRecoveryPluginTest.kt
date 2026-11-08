package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.failureKind
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The plugin with a stub verdict standing in for a platform's (the iOS one reads Darwin codes). */
class TransportRecoveryPluginTest {

    private class Stale : Exception("stale")
    private class Offline : Exception("offline")
    private class BadCertificate : Exception("bad certificate")
    private class Cleartext : Exception("cleartext")
    private class Other : Exception("other")

    private val verdict: (Throwable) -> TransportVerdict? = {
        when (it) {
            is Stale -> TransportVerdict.StaleConnection
            is Offline -> TransportVerdict.Network
            is BadCertificate -> TransportVerdict.Tls
            is Cleartext -> TransportVerdict.CleartextBlocked
            else -> null
        }
    }

    private val bodies = mutableListOf<ByteArray>()

    private fun client(failures: List<Throwable>, delayMs: Long = 0): HttpClient {
        var attempt = 0
        return HttpClient(
            MockEngine { request ->
                bodies += request.body.toByteArray()
                delay(delayMs)
                failures.getOrNull(attempt++)?.let { throw it }
                respond("ok")
            },
        ) {
            install(HttpTimeout)
            install(TransportRecoveryPlugin) { verdict = this@TransportRecoveryPluginTest.verdict }
        }
    }

    private fun upload() = MultiPartFormDataContent(
        formData {
            append("file", ByteArray(4096) { it.toByte() }, Headers.build { append(HttpHeaders.ContentType, "image/png") })
            append("file_id", "f1")
        },
    )

    @Test
    fun `an upload on a closed connection is sent once more, whole`() = runTest {
        val response = client(listOf(Stale())).post("https://example.com/api/files") {
            setBody(upload())
            onUpload { _, _ -> }
        }

        assertThat(response.bodyAsText()).isEqualTo("ok")
        assertThat(bodies).hasSize(2)
        assertThat(bodies[1]).isEqualTo(bodies[0])
        assertThat(bodies[0].size).isGreaterThan(4096)
    }

    @Test
    fun `an upload is sent at most twice, and the second failure reads as Network`() = runTest {
        val failure = runCatching {
            client(listOf(Stale(), Stale())).post("https://example.com/api/files") { setBody(upload()) }
        }.exceptionOrNull()

        assertThat(bodies).hasSize(2)
        assertThat(failure).isInstanceOf(TransportIOException::class.java)
        assertThat(failure!!.failureKind()).isEqualTo(FailureKind.Network)
    }

    @Test
    fun `a JSON post on a closed connection is not resent`() = runTest {
        val failure = runCatching {
            client(listOf(Stale())).post("https://example.com/api/agents/chat/agents") {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
        }.exceptionOrNull()

        assertThat(bodies).hasSize(1)
        assertThat(failure!!.failureKind()).isEqualTo(FailureKind.Network)
    }

    @Test
    fun `network, certificate and cleartext failures get types the classifier reads`() = runTest {
        suspend fun failureFor(cause: Throwable) = runCatching {
            client(listOf(cause)).post("https://example.com/api/files") { setBody(upload()) }
        }.exceptionOrNull()!!

        assertThat(failureFor(Offline()).failureKind()).isEqualTo(FailureKind.Network)
        assertThat(failureFor(BadCertificate()).failureKind()).isEqualTo(FailureKind.Tls)
        assertThat(failureFor(Cleartext()).failureKind()).isEqualTo(FailureKind.CleartextBlocked)
        assertThat(failureFor(Other())).isInstanceOf(Other::class.java)
        assertThat(bodies).hasSize(4)
    }

    @Test
    fun `a request that timed out is not resent`() = runTest {
        val failure = runCatching {
            client(emptyList(), delayMs = 1_000).post("https://example.com/api/files") {
                setBody(upload())
                timeout { requestTimeoutMillis = 100 }
            }
        }.exceptionOrNull()

        assertThat(bodies).hasSize(1)
        assertThat(failure!!.failureKind()).isEqualTo(FailureKind.Timeout)
    }
}

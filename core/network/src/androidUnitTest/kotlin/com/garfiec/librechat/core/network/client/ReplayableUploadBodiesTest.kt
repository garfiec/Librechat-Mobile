package com.garfiec.librechat.core.network.client

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import okhttp3.EventListener
import org.junit.After
import org.junit.Test
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** The production engine against a server that closes idle keep-alive connections, as Node does. */
class ReplayableUploadBodiesTest {

    private val server = IdleClosingServer(idleMs = 500)
    private val client = HttpClient(CertificateTrustOkHttp(EventListener.NONE)) {
        install(HttpRequestRetry) { configureRetryPolicy() }
    }
    private val url = "http://127.0.0.1:${server.port}/api/files"

    // Larger than a socket buffer, so writing into the closed connection fails mid-body.
    private val bytes = ByteArray(256 * 1024) { it.toByte() }

    private fun multipart() = MultiPartFormDataContent(
        formData {
            append("file", bytes, Headers.build { append(HttpHeaders.ContentType, "image/png") })
        },
    )

    @After
    fun tearDown() {
        client.close()
        server.close()
    }

    @Test
    fun `a multipart upload is resent whole on a fresh connection when the pooled one was closed`() = runBlocking {
        client.post(url) { setBody(multipart()) }
        // The server has closed the pooled connection; OkHttp reuses one idle under 10 s unchecked.
        Thread.sleep(1_500)
        val second = client.post(url) {
            setBody(multipart())
            onUpload { _, _ -> }
        }

        assertThat(second.bodyAsText()).isEqualTo("ok")
        assertThat(server.connections.get()).isEqualTo(2)
        assertThat(server.bodyLengths.distinct()).hasSize(1)
    }

    @Test
    fun `a streamed body that is not multipart stays one-shot`() = runBlocking {
        fun stream() = object : OutgoingContent.ReadChannelContent() {
            override val contentType = ContentType.Application.OctetStream
            override val contentLength = bytes.size.toLong()
            override fun readFrom() = ByteReadChannel(bytes)
        }
        client.post(url) { setBody(stream()) }
        Thread.sleep(1_500)

        val second = runCatching { client.post(url) { setBody(stream()) } }

        assertThat(second.isFailure).isTrue()
    }

    private class IdleClosingServer(private val idleMs: Int) : AutoCloseable {
        private val socket = ServerSocket(0)
        val port: Int get() = socket.localPort
        val connections = AtomicInteger()
        val bodyLengths = CopyOnWriteArrayList<Long>()

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val connection = runCatching { socket.accept() }.getOrNull() ?: break
                    connections.incrementAndGet()
                    thread(isDaemon = true) { serve(connection) }
                }
            }
        }

        private fun serve(connection: Socket) = connection.use {
            val input = BufferedInputStream(it.getInputStream())
            while (true) {
                it.soTimeout = idleMs
                // Null once the connection idles past idleMs or the client hangs up: close it.
                val head = runCatching { readHead(input) }.getOrNull() ?: return
                it.soTimeout = 0
                val length = Regex("(?i)content-length: (\\d+)").find(head)?.groupValues?.get(1)?.toLong() ?: 0L
                input.skipNBytes(length)
                bodyLengths += length
                it.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: keep-alive\r\n\r\nok".toByteArray())
                    flush()
                }
            }
        }

        private fun readHead(input: BufferedInputStream): String? {
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) return null
                head.append(b.toChar())
            }
            return head.toString()
        }

        override fun close() = socket.close()
    }
}

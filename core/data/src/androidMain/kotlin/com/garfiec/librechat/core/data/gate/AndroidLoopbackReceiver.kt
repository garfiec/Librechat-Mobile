package com.garfiec.librechat.core.data.gate

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

/**
 * [LoopbackReceiver] on a [ServerSocket] bound to 127.0.0.1.
 *
 * **Every connection is served at once, and only a callback ends the wait.** The browser opens
 * speculative connections to the address before it posts, and may post over one of them much later;
 * reading connections one after another would let an idle one block or time out the sign-in.
 * Blocking reads end by closing their sockets, which [awaitCallback] does on its way out.
 */
internal class AndroidLoopbackReceiver(private val io: CoroutineDispatcher) : LoopbackReceiver {
    @Volatile private var server: ServerSocket? = null

    override fun start(): Int {
        val socket = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))
        server = socket
        return socket.localPort
    }

    override suspend fun awaitCallback(page: String): Map<String, String> = withContext(io) {
        val listening = checkNotNull(server) { "start() first" }
        val result = CompletableDeferred<Map<String, String>>()
        val connections = Collections.synchronizedSet(mutableSetOf<Socket>())
        val acceptor = launch {
            while (isActive) {
                val connection = try {
                    listening.accept()
                } catch (_: IOException) {
                    break
                }
                connections += connection
                launch {
                    try {
                        handle(connection, page)?.let(result::complete)
                    } catch (_: IOException) {
                        // An idle or abandoned connection: not the callback.
                    } finally {
                        connections -= connection
                        runCatching { connection.close() }
                    }
                }
            }
        }
        try {
            result.await()
        } finally {
            runCatching { listening.close() }
            synchronized(connections) { connections.toList() }.forEach { runCatching { it.close() } }
            acceptor.cancel()
        }
    }

    private fun handle(connection: Socket, page: String): Map<String, String>? {
        val input = connection.getInputStream()
        val head = LoopbackHttp.parseHead(readHead(input) ?: return null) ?: return null
        val body = readBody(input, head.contentLength)
        val params = LoopbackHttp.callbackParams(head, body)
        connection.getOutputStream().apply {
            write(if (params != null) LoopbackHttp.ok(page) else LoopbackHttp.notFound)
            flush()
        }
        return params
    }

    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        while (buffer.size() < LoopbackHttp.MAX_HEAD_BYTES) {
            val b = input.read()
            if (b < 0) return null
            buffer.write(b)
            matched = if (b.toChar() == HEAD_END[matched]) matched + 1 else if (b.toChar() == '\r') 1 else 0
            if (matched == HEAD_END.length) return buffer.toString(Charsets.ISO_8859_1.name()).removeSuffix(HEAD_END)
        }
        return null
    }

    private fun readBody(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n < 0) break
            read += n
        }
        return bytes.copyOf(read).toString(Charsets.UTF_8)
    }

    override fun close() {
        runCatching { server?.close() }
        server = null
    }

    private companion object {
        const val BACKLOG = 8
        const val HEAD_END = "\r\n\r\n"
    }
}

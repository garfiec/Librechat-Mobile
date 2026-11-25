package com.garfiec.librechat.core.data.gate

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.POLLIN
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.close as closeSocket

/**
 * [LoopbackReceiver] on a POSIX socket bound to 127.0.0.1. Accepts and reads through `poll` with a
 * short timeout, so a cancelled wait notices within [POLL_MILLIS] instead of blocking in `accept`.
 * Every connection is served at once and only a callback ends the wait: the browser opens
 * speculative connections first and may post over one of them later.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosLoopbackReceiver(private val io: CoroutineDispatcher) : LoopbackReceiver {
    private var fd: Int = -1

    override fun start(): Int = memScoped {
        val s = socket(AF_INET, SOCK_STREAM, 0)
        check(s >= 0) { "socket() failed" }
        val one = alloc<IntVar>().apply { value = 1 }
        setsockopt(s, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
        val addr = alloc<sockaddr_in>()
        addr.sin_len = sizeOf<sockaddr_in>().convert()
        addr.sin_family = AF_INET.convert()
        addr.sin_port = 0u
        addr.sin_addr.s_addr = LOOPBACK_NETWORK_ORDER
        if (bind(s, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0 || listen(s, BACKLOG) != 0) {
            closeSocket(s)
            error("bind/listen on 127.0.0.1 failed")
        }
        val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
        getsockname(s, addr.ptr.reinterpret(), length.ptr)
        fd = s
        val networkPort = addr.sin_port.toInt()
        ((networkPort and BYTE) shl BITS) or (networkPort shr BITS and BYTE)
    }

    override suspend fun awaitCallback(page: String): Map<String, String> = withContext(io) {
        val result = CompletableDeferred<Map<String, String>>()
        val acceptor = launch {
            while (isActive) {
                if (!readable(fd)) continue
                val connection = accept(fd, null, null)
                if (connection < 0) continue
                launch {
                    try {
                        handle(connection, page)?.let(result::complete)
                    } finally {
                        closeSocket(connection)
                    }
                }
            }
        }
        try {
            result.await()
        } finally {
            // Handlers notice within POLL_MILLIS and close their sockets.
            acceptor.cancel()
        }
    }

    private suspend fun handle(connection: Int, page: String): Map<String, String>? {
        memScoped {
            val one = alloc<IntVar>().apply { value = 1 }
            setsockopt(connection, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<IntVar>().convert())
        }
        val received = ArrayList<Byte>()
        var headEnd = -1
        while (headEnd < 0 && received.size < LoopbackHttp.MAX_HEAD_BYTES) {
            val chunk = receive(connection) ?: return null
            received.addAll(chunk.toList())
            headEnd = indexOfHeadEnd(received)
        }
        if (headEnd < 0) return null
        val head = LoopbackHttp.parseHead(received.subList(0, headEnd).toByteArray().decodeToString()) ?: return null
        val body = ArrayList(received.subList(headEnd + HEAD_END_LENGTH, received.size))
        while (body.size < head.contentLength) {
            val chunk = receive(connection) ?: break
            body.addAll(chunk.toList())
        }
        val params = LoopbackHttp.callbackParams(head, body.take(head.contentLength).toByteArray().decodeToString())
        val reply = if (params != null) LoopbackHttp.ok(page) else LoopbackHttp.notFound
        reply.usePinned { pinned -> send(connection, pinned.addressOf(0), reply.size.convert(), 0) }
        return params
    }

    /**
     * One read, or null on close or error. Waits as long as the sign-in does: the browser may hold a
     * speculative connection open and send the callback over it much later.
     */
    private suspend fun receive(connection: Int): ByteArray? {
        while (currentCoroutineContext().isActive) {
            if (!readable(connection)) continue
            return memScoped {
                val buffer = allocArray<ByteVar>(CHUNK)
                val n = recv(connection, buffer, CHUNK.convert(), 0)
                if (n <= 0) null else buffer.readBytes(n.toInt())
            }
        }
        return null
    }

    private fun readable(socket: Int, timeoutMillis: Int = POLL_MILLIS): Boolean = memScoped {
        val p = alloc<pollfd>()
        p.fd = socket
        p.events = POLLIN.convert()
        poll(p.ptr, 1u, timeoutMillis) > 0
    }

    private fun indexOfHeadEnd(bytes: List<Byte>): Int {
        for (i in 0..bytes.size - HEAD_END_LENGTH) {
            if (bytes[i] == CR && bytes[i + 1] == LF && bytes[i + 2] == CR && bytes[i + 3] == LF) return i
        }
        return -1
    }

    override fun close() {
        if (fd >= 0) closeSocket(fd)
        fd = -1
    }

    private companion object {
        const val BACKLOG = 4
        const val POLL_MILLIS = 500
        const val CHUNK = 4096
        const val HEAD_END_LENGTH = 4
        const val BYTE = 0xFF
        const val BITS = 8
        const val CR: Byte = 13
        const val LF: Byte = 10

        /** 127.0.0.1 in network byte order, as read by a little-endian `UInt` (every iOS device). */
        const val LOOPBACK_NETWORK_ORDER: UInt = 0x0100007Fu
    }
}

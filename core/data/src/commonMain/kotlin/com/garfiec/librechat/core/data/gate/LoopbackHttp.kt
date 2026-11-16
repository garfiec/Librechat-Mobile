package com.garfiec.librechat.core.data.gate

import io.ktor.http.parseQueryString

/**
 * The HTTP/1.1 the loopback listener needs to understand: one request line, a `Content-Length`, a form
 * body. Shared by both platforms' sockets so the parsing is tested once.
 */
internal object LoopbackHttp {
    const val MAX_HEAD_BYTES = 16 * 1024
    const val MAX_BODY_BYTES = 64 * 1024
    const val CALLBACK_PATH = "/callback"

    data class Head(val method: String, val target: String, val contentLength: Int)

    /** Parses the request head (everything before the blank line); null when it isn't HTTP. */
    fun parseHead(head: String): Head? {
        val lines = head.split("\r\n")
        val parts = lines.firstOrNull()?.split(' ') ?: return null
        if (parts.size < 3 || !parts[2].startsWith("HTTP/")) return null
        val length = lines.drop(1)
            .firstOrNull { it.substringBefore(':').trim().equals("Content-Length", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        return Head(parts[0].uppercase(), parts[1], length.coerceIn(0, MAX_BODY_BYTES))
    }

    /**
     * The authorization response's parameters when [head] is a request to [CALLBACK_PATH], else null
     * (a favicon fetch, a probe). A form POST carries them in [body]; a GET in the query.
     */
    fun callbackParams(head: Head, body: String): Map<String, String>? {
        val path = head.target.substringBefore('?')
        if (path != CALLBACK_PATH) return null
        val encoded = when (head.method) {
            "POST" -> body
            "GET" -> head.target.substringAfter('?', "")
            else -> return null
        }
        val parsed = parseQueryString(encoded)
        return parsed.names().associateWith { parsed[it].orEmpty() }
    }

    fun ok(page: String): ByteArray = response("200 OK", page)

    val notFound: ByteArray get() = response("404 Not Found", "")

    private fun response(status: String, page: String): ByteArray {
        val body = page.encodeToByteArray()
        return (
            "HTTP/1.1 $status\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Connection: close\r\n\r\n"
            ).encodeToByteArray() + body
    }
}

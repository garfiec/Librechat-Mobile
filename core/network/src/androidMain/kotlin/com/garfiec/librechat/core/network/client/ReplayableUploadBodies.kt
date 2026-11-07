package com.garfiec.librechat.core.network.client

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink

/**
 * Lets OkHttp resend a multipart upload on a fresh connection when the server already closed the
 * pooled keep-alive one; Ktor marks every streamed body one-shot, which blocks that resend. Safe
 * only while multipart parts are byte arrays, which Ktor rewrites on each write: a part streamed
 * from a one-time source would be resent truncated, so keep it out.
 */
internal object ReplayableUploadBodies : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body
        val multipart = request.header("Content-Type")?.startsWith("multipart/", ignoreCase = true) == true
        if (body == null || !multipart || !body.isOneShot() || body.isDuplex()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().method(request.method, Repeatable(body)).build())
    }

    private class Repeatable(private val delegate: RequestBody) : RequestBody() {
        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = delegate.contentLength()

        override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
    }
}

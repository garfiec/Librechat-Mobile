package com.garfiec.librechat.core.network.client

import okhttp3.Interceptor
import okhttp3.Response
import java.net.ProtocolException
import java.net.Proxy

/**
 * Surfaces an origin's 407 (Authelia's answer to an expired or revoked bearer) as a
 * [GateChallengeSignal] instead of OkHttp's generic "Received HTTP_PROXY_AUTH (407) code while not
 * using proxy", which no caller could tell apart from a broken connection.
 *
 * A network interceptor, because OkHttp's follow-up logic throws on the 407 before an application
 * interceptor or Ktor sees the response. A [ProtocolException] subclass, because OkHttp never
 * retries one on another route. A 407 from a configured HTTP proxy is left to OkHttp's proxy
 * authenticator.
 */
internal object GateChallengeInterceptor : Interceptor {
    private const val PROXY_AUTH_REQUIRED = 407

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != PROXY_AUTH_REQUIRED) return response
        if (chain.connection()?.route()?.proxy?.type() == Proxy.Type.HTTP) return response
        response.close()
        throw GateChallengeException()
    }
}

internal class GateChallengeException :
    ProtocolException("The server's access gate refused the credential (407)"),
    GateChallengeSignal

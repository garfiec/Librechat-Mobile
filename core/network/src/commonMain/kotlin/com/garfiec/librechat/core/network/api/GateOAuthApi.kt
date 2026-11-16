package com.garfiec.librechat.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The OAuth endpoints of an access gate's identity provider (Authelia), for the native sign-in that
 * gets a bearer token through a forward-auth gate.
 *
 * [client] must be the `KoinQualifiers.External` client: the gate's portal is another host, and the
 * LibreChat clients' tokens and gateway headers must never reach it.
 */
class GateOAuthApi(private val client: HttpClient, private val json: Json) {

    /** The provider's discovery document at [portalUrl]'s origin, or null when it serves none. */
    suspend fun discover(portalUrl: String): GateDiscovery? {
        val origin = URLBuilder(Url(portalUrl)).apply {
            encodedPath = "/.well-known/openid-configuration"
            parameters.clear()
            fragment = ""
        }.buildString()
        val response = client.get(origin) {
            expectSuccess = false
            header(HttpHeaders.Accept, "application/json")
        }
        if (!response.status.isSuccess()) return null
        return runCatching { json.decodeFromString<GateDiscovery>(response.bodyAsText()) }.getOrNull()
            ?.takeIf { it.issuer.isNotBlank() && it.authorizationEndpoint.isNotBlank() && it.tokenEndpoint.isNotBlank() }
    }

    /** Pushed authorization request (RFC 9126). */
    suspend fun pushAuthorizationRequest(endpoint: String, form: Map<String, String>): GateOAuthResult<String> =
        post(endpoint, form) { json.decodeFromString<ParResponse>(it).requestUri }

    /** The token endpoint, for both the authorization-code and refresh-token grants. */
    suspend fun token(endpoint: String, form: Map<String, String>): GateOAuthResult<GateTokens> =
        post(endpoint, form) { json.decodeFromString<GateTokens>(it) }

    private suspend fun <T> post(
        endpoint: String,
        form: Map<String, String>,
        decode: (String) -> T,
    ): GateOAuthResult<T> {
        val response: HttpResponse = client.submitForm(
            url = endpoint,
            formParameters = Parameters.build { form.forEach { (k, v) -> append(k, v) } },
        ) {
            expectSuccess = false
            header(HttpHeaders.Accept, "application/json")
        }
        val body = response.bodyAsText()
        if (response.status.isSuccess()) {
            return runCatching { GateOAuthResult.Ok(decode(body)) }
                .getOrElse { GateOAuthResult.Refused(response.status.value, "invalid_response", null) }
        }
        val error = runCatching { json.decodeFromString<OAuthError>(body) }.getOrNull()
        return GateOAuthResult.Refused(response.status.value, error?.error ?: "http_${response.status.value}", error?.description)
    }

    @Serializable
    private data class ParResponse(@SerialName("request_uri") val requestUri: String)

    @Serializable
    private data class OAuthError(
        val error: String,
        @SerialName("error_description") val description: String? = null,
    )
}

@Serializable
data class GateDiscovery(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("pushed_authorization_request_endpoint") val parEndpoint: String? = null,
)

@Serializable
data class GateTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long? = null,
) {
    override fun toString(): String = "GateTokens(<redacted>, expiresIn=$expiresInSeconds)"
}

sealed interface GateOAuthResult<out T> {
    data class Ok<T>(val value: T) : GateOAuthResult<T>

    /** The provider answered with an OAuth error ([error] is its `error` code, e.g. `invalid_client`). */
    data class Refused(val status: Int, val error: String, val description: String?) : GateOAuthResult<Nothing>
}

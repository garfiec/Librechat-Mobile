package com.garfiec.librechat.core.network.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import org.junit.Test

/** Pins the profile-picture upload to upstream's `POST /api/files/images/avatar` contract. */
class UserApiAvatarUploadTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun client(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    private suspend fun renderBody(content: OutgoingContent): String = coroutineScope {
        val writable = content as OutgoingContent.WriteChannelContent
        val channel = writer(Dispatchers.Default) { writable.writeTo(channel) }.channel
        channel.readRemaining().readByteArray().decodeToString()
    }

    @Test
    fun `uploadAvatar posts a jpeg file part and manual flag, and decodes the url`() = runTest {
        var method: HttpMethod? = null
        var path: String? = null
        var body: String? = null
        val engine = MockEngine { request ->
            method = request.method
            path = request.url.encodedPath
            body = renderBody(request.body)
            respond(
                content = """{"url":"/images/u1/avatar-1.png?manual=true"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = UserApi(client(engine)).uploadAvatar("jpg".encodeToByteArray(), "image/jpeg", "avatar.jpg")

        assertThat(method).isEqualTo(HttpMethod.Post)
        assertThat(path).isEqualTo("/api/files/images/avatar")
        assertThat(body).containsMatch("name=\"?file\"?")
        assertThat(body).contains("filename=\"avatar.jpg\"")
        assertThat(body).contains("image/jpeg")
        assertThat(body).containsMatch("name=\"?manual\"?")
        assertThat(body).contains("true")
        assertThat(result.url).isEqualTo("/images/u1/avatar-1.png?manual=true")
    }
}

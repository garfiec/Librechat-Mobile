package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/**
 * `PUT /api/permissions/:type/:id` through a real client with the app's JSON configuration, which
 * drops nulls and defaults. The server revokes public access only for `public === false` and runs
 * its SHARE_PUBLIC check whenever `public` is truthy (PermissionsController.js:166,
 * middleware/share.ts:214), so whether `public` reaches the wire is the behaviour.
 */
class PermissionsApiUpdateTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(response: String = UPSTREAM_RESPONSE): PermissionsApi {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = response,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return PermissionsApi(client)
    }

    private fun sentBody(): JsonObject =
        Json.parseToJsonElement((requests.last().body as TextContent).text).jsonObject

    @Test
    fun `turning public off sends public false`() = runTest {
        api().updateResourcePermissions("skill", "sk-1", UpdateResourcePermissionsRequest(public = false))
        assertThat(sentBody()["public"].toString()).isEqualTo("false")
        assertThat(sentBody().containsKey("publicAccessRoleId")).isFalse()
    }

    @Test
    fun `turning public on sends the flag and its role`() = runTest {
        api().updateResourcePermissions(
            "skill",
            "sk-1",
            UpdateResourcePermissionsRequest(public = true, publicAccessRoleId = "skill_viewer"),
        )
        assertThat(sentBody()["public"].toString()).isEqualTo("true")
        assertThat(sentBody()["publicAccessRoleId"].toString()).isEqualTo("\"skill_viewer\"")
    }

    @Test
    fun `a grant that leaves public alone sends neither public field`() = runTest {
        api().updateResourcePermissions(
            "skill",
            "sk-1",
            UpdateResourcePermissionsRequest(
                updated = listOf(Principal(type = "user", id = "u1", accessRoleId = "skill_viewer")),
            ),
        )
        assertThat(sentBody().containsKey("public")).isFalse()
        assertThat(sentBody().containsKey("publicAccessRoleId")).isFalse()
        assertThat(sentBody().containsKey("updated")).isTrue()
    }

    /** Why callers refetch: the response wraps a partial list, and none of it reaches the model. */
    @Test
    fun `the upstream response decodes to no grants`() = runTest {
        val result = api().updateResourcePermissions("skill", "sk-1", UpdateResourcePermissionsRequest(public = true))
        assertThat(result).isEqualTo(ResourcePermissions())
    }

    private companion object {
        // PermissionsController.js:226-233, for a call that granted one person on a public skill.
        const val UPSTREAM_RESPONSE = """{"message":"Permissions updated successfully","results":{"principals":[
            {"type":"user","id":"65f0c0ffee0000000000b002","name":"Bartholomew","accessRoleId":"skill_viewer"}],
            "public":true,"publicAccessRoleId":"skill_viewer"}}"""
    }
}

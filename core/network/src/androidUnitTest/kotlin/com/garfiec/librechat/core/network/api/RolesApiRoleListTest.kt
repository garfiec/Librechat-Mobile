package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.request.UpdateRoleMemoriesRequest
import com.garfiec.librechat.core.model.request.UpdateRoleSkillsRequest
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
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The admin role picker lists every role on the server (custom ones included), and a custom role's
 * name, which may hold a space or a slash, travels as one encoded path segment.
 */
class RolesApiRoleListTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(body: String): RolesApi {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = body,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return RolesApi(client)
    }

    @Test
    fun `listRoles asks the admin route for one page of 200 and decodes the names`() = runTest {
        val response = api(
            """{"roles":[{"_id":"1","name":"ADMIN","description":""},{"name":"USER"},""" +
                """{"name":"Night shift"}],"total":3,"limit":200,"offset":0}""",
        ).listRoles()

        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Get)
        assertThat(request.url.encodedPath).endsWith("/api/admin/roles")
        assertThat(request.url.parameters["limit"]).isEqualTo("200")
        assertThat(response.roles.map { it.name }).containsExactly("ADMIN", "USER", "Night shift").inOrder()
    }

    @Test
    fun `a custom role's name is one encoded path segment on every role route`() = runTest {
        val roleBody = """{"name":"a/b c","permissions":{}}"""
        val api = api(roleBody)

        api.getRole("a/b c")
        api.updateRoleMemories("a/b c", UpdateRoleMemoriesRequest(use = true))
        api.updateRoleSkills("a/b c", UpdateRoleSkillsRequest(use = true))

        assertThat(requests.map { it.url.encodedPath }).containsExactly(
            "/api/roles/a%2Fb%20c",
            "/api/roles/a%2Fb%20c/memories",
            "/api/roles/a%2Fb%20c/skills",
        ).inOrder()
    }
}

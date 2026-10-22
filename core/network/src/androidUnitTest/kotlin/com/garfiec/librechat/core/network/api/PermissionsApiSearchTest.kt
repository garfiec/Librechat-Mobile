package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `GET /api/permissions/search-principals` through a real client with the app's JSON
 * configuration. The server wraps the principals in an object (`createPrincipalSearch` in
 * packages/api/src/acl/search.ts, data-provider `TPrincipalSearchResponse`), so a bare-list decode fails every
 * search.
 */
class PermissionsApiSearchTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(response: String): PermissionsApi {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = response,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
        }
        return PermissionsApi(client)
    }

    @Test
    fun `the upstream search response decodes to its principals`() = runTest {
        val principals = api(UPSTREAM_RESPONSE).searchPrincipals("sam")

        assertThat(principals.map { it.type to it.name }).containsExactly(
            "user" to "Samantha Reyes",
            "group" to "Sales",
            "user" to "Sam Entra",
        ).inOrder()
        assertThat(principals[0].id).isEqualTo("65f0c0ffee0000000000b002")
        assertThat(principals[0].email).isEqualTo("sam@example.com")
        assertThat(principals[1].memberCount).isEqualTo(12)
        // An Entra principal that doesn't exist locally yet has no id.
        assertThat(principals[2].id).isNull()
        assertThat(principals[2].idOnTheSource).isEqualTo("entra-oid-1")
        assertThat(requests.last().url.parameters["q"]).isEqualTo("sam")
    }

    @Test
    fun `an empty search decodes to no principals`() = runTest {
        val principals = api(
            """{"query":"zz","limit":10,"types":null,"results":[],"count":0,"sources":{"local":0,"entra":0}}""",
        ).searchPrincipals("zz")

        assertThat(principals).isEmpty()
    }

    private companion object {
        // createPrincipalSearch's 200 body (packages/api/src/acl/search.ts), shaped as TPrincipalSearchResponse.
        const val UPSTREAM_RESPONSE = """{"query":"sam","limit":10,"types":["user","group"],"results":[
            {"id":"65f0c0ffee0000000000b002","type":"user","name":"Samantha Reyes","email":"sam@example.com",
             "username":"sreyes","avatar":"","provider":"local","source":"local"},
            {"id":"65f0c0ffee0000000000c003","type":"group","name":"Sales","email":"sales@example.com",
             "source":"local","memberCount":12,"description":"Sales team"},
            {"id":null,"type":"user","name":"Sam Entra","email":"sam.e@example.com","source":"entra",
             "idOnTheSource":"entra-oid-1"}],
            "count":3,"sources":{"local":2,"entra":1}}"""
    }
}

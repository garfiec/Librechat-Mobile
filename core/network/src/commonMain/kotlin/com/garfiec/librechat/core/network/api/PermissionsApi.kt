package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.model.response.EffectivePermissionsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path
import kotlinx.serialization.Serializable

/**
 * Granular ACL permission endpoints, mirroring upstream
 * `api/server/routes/accessPermissions.js`. The base path is `/api/permissions`
 * and the resource type drives routing (`agent`, `promptGroup`, `mcpServer`,
 * `remoteAgent`, `sharedLink`).
 */
class PermissionsApi(
    private val client: HttpClient,
) {
    /**
     * GET /api/permissions/{resourceType}/{resourceId} — list grants.
     * Requires SHARE permission on the resource (enforced server-side).
     */
    suspend fun getResourcePermissions(
        resourceType: String,
        resourceId: String,
    ): ResourcePermissions =
        client.get {
            url { path("api/permissions/$resourceType/$resourceId") }
        }.body()

    /**
     * PUT /api/permissions/{resourceType}/{resourceId} — bulk update. Server
     * diffs `updated[]` against existing entries and revokes `removed[]`.
     * Requires SHARE (and SHARE_PUBLIC for `public=true`).
     *
     * The server answers `{message, results: {principals, public?, publicAccessRoleId}}`, where
     * `principals` holds only what this call granted, so the decoded value is NOT the resource's
     * grant list: read that again with [getResourcePermissions] after a change.
     */
    suspend fun updateResourcePermissions(
        resourceType: String,
        resourceId: String,
        body: UpdateResourcePermissionsRequest,
    ): ResourcePermissions =
        client.put {
            url { path("api/permissions/$resourceType/$resourceId") }
            setBody(body)
        }.body()

    /**
     * GET /api/permissions/{resourceType}/roles — roles available for a type.
     */
    suspend fun getResourceRoles(resourceType: String): List<AccessRole> =
        client.get {
            url { path("api/permissions/$resourceType/roles") }
        }.body()

    /**
     * GET /api/permissions/{resourceType}/{resourceId}/effective — the caller's `PermissionBits`
     * on one resource (e.g. a shared link's lookup `_id`). Zero grants nothing.
     */
    suspend fun getEffectivePermissions(
        resourceType: String,
        resourceId: String,
    ): EffectivePermissionsResponse =
        client.get {
            url { path("api/permissions/$resourceType/$resourceId/effective") }
        }.body()

    /**
     * GET /api/permissions/{resourceType}/effective/all — the caller's `PermissionBits` on every
     * resource of the type they can reach, keyed by resource id (the record's `_id`, e.g. an MCP
     * server's `dbId`). A resource missing from the map grants nothing beyond what the caller
     * could already see.
     */
    suspend fun getAllEffectivePermissions(resourceType: String): Map<String, Int> =
        client.get {
            url { path("api/permissions/$resourceType/effective/all") }
        }.body()

    /**
     * GET /api/permissions/search-principals?q=... — autocomplete users/groups via the people
     * picker. The server answers `{query, limit, types, results, count, sources}` and searches only
     * the principal types the caller's PEOPLE_PICKER permissions allow (403 when none); this
     * returns the `results`.
     */
    suspend fun searchPrincipals(query: String, limit: Int? = null): List<Principal> =
        client.get {
            url { path("api/permissions/search-principals") }
            parameter("q", query)
            limit?.let { parameter("limit", it) }
        }.body<PrincipalSearchResponse>().results
}

/**
 * The `search-principals` envelope (upstream `TPrincipalSearchResponse`); only the hits are read.
 * [results] is required so a renamed field fails the search instead of reading as "no matches".
 */
@Serializable
internal data class PrincipalSearchResponse(
    val results: List<Principal>,
)

package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path

/**
 * Granular ACL permission endpoints, mirroring upstream
 * `api/server/routes/accessPermissions.js`. The base path is `/api/permissions`
 * and the resource type drives routing (`agent`, `promptGroup`, `mcpServer`,
 * `remoteAgent`).
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
     * GET /api/permissions/search-principals?q=... — autocomplete users/groups
     * via the people-picker. Returns a list of principals (USER + GROUP).
     */
    suspend fun searchPrincipals(query: String, limit: Int? = null): List<Principal> =
        client.get {
            url { path("api/permissions/search-principals") }
            parameter("q", query)
            limit?.let { parameter("limit", it) }
        }.body()
}

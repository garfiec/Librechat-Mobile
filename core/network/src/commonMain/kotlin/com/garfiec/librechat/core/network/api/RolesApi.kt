package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.UpdateRoleMemoriesRequest
import com.garfiec.librechat.core.model.request.UpdateRoleSkillsRequest
import com.garfiec.librechat.core.model.response.RoleListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.path

class RolesApi(
    private val client: HttpClient,
) {
    suspend fun getRole(roleName: String): UserRolePermissions =
        client.get {
            url { rolePath(roleName) }
        }.body()

    /**
     * Every role on the server, custom ones included (`GET /api/admin/roles`, v0.8.8). Needs the
     * READ_ROLES capability; web asks for `limit=200` in one page (`data-service.ts` `listRoles`)
     * and falls back to USER/ADMIN when this fails or lists nothing.
     */
    suspend fun listRoles(): RoleListResponse =
        client.get {
            url { path("api/admin/roles") }
            parameter("limit", ROLE_LIST_LIMIT)
        }.body()

    /**
     * Sets a role's SKILLS permission booleans
     * (`PUT /api/roles/:roleName/skills`). Admin-only server-side (manageRoles);
     * returns the updated role. No `arg`-wrap.
     */
    suspend fun updateRoleSkills(roleName: String, request: UpdateRoleSkillsRequest): UserRolePermissions =
        client.put {
            url { rolePath(roleName, "skills") }
            setBody(request)
        }.body()

    /**
     * Sets a role's MEMORIES permission booleans
     * (`PUT /api/roles/:roleName/memories`). Admin-only server-side (manageRoles);
     * returns the updated role. No `arg`-wrap; a partial body merges.
     */
    suspend fun updateRoleMemories(roleName: String, request: UpdateRoleMemoriesRequest): UserRolePermissions =
        client.put {
            url { rolePath(roleName, "memories") }
            setBody(request)
        }.body()

    /**
     * `api/roles/:roleName[/:permissionType]`. A custom role (listed by [listRoles]) may hold a space
     * or a slash, so the name is appended as ONE segment with `encodeSlash = true`, which escapes it
     * exactly once: `path()` would escape an already-encoded name a second time (`%20` -> `%2520`),
     * and Express decodes the param only once.
     */
    private fun URLBuilder.rolePath(roleName: String, permissionType: String? = null) {
        path("api/roles")
        appendPathSegments(roleName, encodeSlash = true)
        if (permissionType != null) appendPathSegments(permissionType)
    }
}

/** Web's page size for the role picker (`data-service.ts` `listRoles`). Mirror `admin-role-list-page-size`. */
private const val ROLE_LIST_LIMIT = 200

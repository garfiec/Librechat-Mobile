package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors upstream `principalSchema` (accessPermissions.ts:89). Used both for
 * grants returned by `GET /api/permissions/{type}/{id}` and for the entries
 * sent in `PUT /api/permissions/{type}/{id}`'s `updated[]` / `removed[]` lists.
 *
 * Upstream is permissive about which fields are present per principal type:
 * - user/group: id + name + email + avatar + source + accessRoleId
 * - role: id (role name) + accessRoleId
 * - public: no id, sent via top-level `public: true` + `publicAccessRoleId`
 */
@Serializable
data class Principal(
    val type: String,
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val source: String? = null,
    val avatar: String? = null,
    val description: String? = null,
    val idOnTheSource: String? = null,
    val accessRoleId: String? = null,
    val memberCount: Int? = null,
)

/**
 * The shortest trimmed query `GET /api/permissions/search-principals` accepts; anything shorter is
 * refused with a 400 (`createPrincipalSearch` in packages/api/src/acl/search.ts). Mirror: scripts/mirrors.json
 * `principal-search-min-query`.
 */
const val PRINCIPAL_SEARCH_MIN_QUERY_LENGTH = 2

/** Whether [query] is one the principal search would answer rather than refuse. */
fun isSearchablePrincipalQuery(query: String): Boolean = query.trim().length >= PRINCIPAL_SEARCH_MIN_QUERY_LENGTH

/**
 * The `GET /api/permissions/{resourceType}/{resourceId}` response shape (see
 * PermissionsController.js getResourcePermissions).
 */
@Serializable
data class ResourcePermissions(
    val principals: List<Principal> = emptyList(),
    val public: Boolean = false,
    val publicAccessRoleId: String? = null,
)

/**
 * The `PUT /api/permissions/{resourceType}/{resourceId}` request body
 * (PermissionsController.js updateResourcePermissions:58).
 *
 * [public] is three-state on the wire, and the app's JSON drops nulls and defaults: null leaves
 * public access alone, `true` (with [publicAccessRoleId]) grants it, and `false` revokes it. Send
 * it only when the public state or its role changes, as web does: a truthy `public` runs the
 * server's SHARE_PUBLIC check, which refuses a plain grant from a role that can share but not
 * publicly.
 *
 * The response is NOT a [ResourcePermissions]: it is `{ message, results: { principals, public?,
 * publicAccessRoleId } }`, and `results.principals` holds only what that call granted. Refetch
 * with `GET` for the resource's grants.
 */
@Serializable
data class UpdateResourcePermissionsRequest(
    val updated: List<Principal> = emptyList(),
    val removed: List<Principal> = emptyList(),
    val public: Boolean? = null,
    val publicAccessRoleId: String? = null,
)

/**
 * One available role for a resource type — returned by `GET
 * /api/permissions/{resourceType}/roles`. Wraps the bits each role grants.
 */
@Serializable
data class AccessRole(
    val accessRoleId: String,
    val name: String? = null,
    val description: String? = null,
    val resourceType: String? = null,
    @SerialName("permBits") val permissionBits: Int? = null,
)

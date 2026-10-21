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
 * (PermissionsController.js updateResourcePermissions).
 *
 * [public] is three-state on the wire: null leaves public access as it is (and skips the server's
 * SHARE_PUBLIC check), `false` revokes it, `true` grants [publicAccessRoleId]. It is nullable so
 * that `false` is not the default and survives the client's `encodeDefaults = false`.
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

package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * Response of `GET /api/admin/roles` (upstream `ListRolesResponse`): every role the server has,
 * custom ones included, for the admin role-permission editors' picker.
 */
@Serializable
data class RoleListResponse(
    val roles: List<RoleListItem> = emptyList(),
)

@Serializable
data class RoleListItem(
    val name: String,
)

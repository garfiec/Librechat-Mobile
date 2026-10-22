package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `PUT /api/roles/:roleName/memories` (upstream partial
 * `memoryPermissionsSchema`) — the role's MEMORIES permission booleans. All fields
 * optional; the server merges this partial onto the role's existing MEMORIES
 * permissions, so a null field is left as it is. No `arg`-wrap. Admin-only (manageRoles).
 */
@Serializable
data class UpdateRoleMemoriesRequest(
    @SerialName("USE") val use: Boolean? = null,
    @SerialName("CREATE") val create: Boolean? = null,
    @SerialName("UPDATE") val update: Boolean? = null,
    @SerialName("READ") val read: Boolean? = null,
    @SerialName("OPT_OUT") val optOut: Boolean? = null,
)

package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * `GET /api/permissions/{resourceType}/{resourceId}/effective`: the caller's
 * [com.garfiec.librechat.core.model.PermissionBits] on one resource. Zero grants nothing.
 */
@Serializable
data class EffectivePermissionsResponse(
    val permissionBits: Int = 0,
)

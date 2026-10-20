package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest

/**
 * Granular ACL surface for sharing agents (and other resources) with users,
 * groups, roles, and the public.
 */
interface PermissionsRepository {
    suspend fun getResourcePermissions(
        resourceType: String,
        resourceId: String,
    ): Result<ResourcePermissions>

    suspend fun updateResourcePermissions(
        resourceType: String,
        resourceId: String,
        body: UpdateResourcePermissionsRequest,
    ): Result<ResourcePermissions>

    suspend fun getResourceRoles(resourceType: String): Result<List<AccessRole>>

    suspend fun searchPrincipals(query: String, limit: Int? = null): Result<List<Principal>>

    /** The caller's permission bits per resource id, for every [resourceType] resource they can reach. */
    suspend fun getAllEffectivePermissions(resourceType: String): Result<Map<String, Int>>
}

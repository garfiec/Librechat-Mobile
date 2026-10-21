package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.network.api.PermissionsApi

class PermissionsRepositoryImpl(
    private val api: PermissionsApi,
) : PermissionsRepository {

    override suspend fun getResourcePermissions(
        resourceType: String,
        resourceId: String,
    ): Result<ResourcePermissions> = safeApiCall {
        api.getResourcePermissions(resourceType, resourceId)
    }

    override suspend fun updateResourcePermissions(
        resourceType: String,
        resourceId: String,
        body: UpdateResourcePermissionsRequest,
    ): Result<ResourcePermissions> = safeApiCall {
        api.updateResourcePermissions(resourceType, resourceId, body)
    }

    override suspend fun getResourceRoles(resourceType: String): Result<List<AccessRole>> = safeApiCall {
        api.getResourceRoles(resourceType)
    }

    override suspend fun searchPrincipals(
        query: String,
        limit: Int?,
    ): Result<List<Principal>> = safeApiCall {
        api.searchPrincipals(query, limit)
    }

    override suspend fun getEffectivePermissions(resourceType: String, resourceId: String): Result<Int> =
        safeApiCall { api.getEffectivePermissions(resourceType, resourceId).permissionBits }

    override suspend fun getAllEffectivePermissions(resourceType: String): Result<Map<String, Int>> = safeApiCall {
        api.getAllEffectivePermissions(resourceType)
    }
}

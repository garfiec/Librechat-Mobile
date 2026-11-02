package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.apiCallCatching
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.common.result.toSafeError
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
    ): Result<ResourcePermissions> = apiCallCatching(
        { Result.Success(api.updateResourcePermissions(resourceType, resourceId, body)) },
    ) { e ->
        // The audited update fails closed with a retryable code (v0.8.9-rc1); a 5xx would otherwise
        // read as a generic server error.
        permissionFailureOf(e)?.let { failure ->
            Result.Error(PermissionUpdateRefusedException(failure, e), kind = FailureKind.Server)
        } ?: e.toSafeError()
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

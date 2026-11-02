package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.network.api.CodeEnvironmentsApi

/** Live status of the attached code machines a chat's agents run on. Network-direct, no cache. */
interface CodeEnvironmentRepository {
    /** The machine's state and registered workspaces. A failure's status code is on the exception. */
    suspend fun status(environmentId: String): Result<CodeEnvironmentStatus>
}

class CodeEnvironmentRepositoryImpl(
    private val api: CodeEnvironmentsApi,
) : CodeEnvironmentRepository {
    override suspend fun status(environmentId: String): Result<CodeEnvironmentStatus> =
        safeApiCall { api.status(environmentId) }
}

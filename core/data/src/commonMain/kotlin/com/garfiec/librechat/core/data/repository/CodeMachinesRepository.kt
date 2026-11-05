package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.code.CodeEnvironmentPairingRequest
import com.garfiec.librechat.core.model.code.CodeEnvironmentPairingResponse
import com.garfiec.librechat.core.model.code.CodeEnvironmentUserSettings
import com.garfiec.librechat.core.model.code.CodeEnvironmentsResponse
import com.garfiec.librechat.core.model.code.CodeSetupApprovalRequest
import com.garfiec.librechat.core.model.code.CodeSetupAttempt
import com.garfiec.librechat.core.network.api.CodeEnvironmentsApi

/** Why pairing a machine failed, as the pairing route's statuses distinguish it. */
enum class CodePairingError {
    /** 400: the name was refused. */
    INVALID_NAME,

    /** 403: personal workers are off for this user. */
    DISABLED,

    /** 404: the control plane is gone or no longer authorized. */
    CONTROL_PLANE_GONE,

    /** 409 "limit reached": this user already has as many machines as allowed. */
    LIMIT_REACHED,

    /** 409 otherwise: the server can't pair for this account (principal auth off, account being deleted). */
    REFUSED,

    /** 429. */
    RATE_LIMITED,

    /** 502 / 503 / 504, or no response. */
    UNAVAILABLE,
}

sealed interface CodePairingOutcome {
    data class Paired(val response: CodeEnvironmentPairingResponse) : CodePairingOutcome
    data class Failed(val error: CodePairingError) : CodePairingOutcome
}

/** Why removing a machine failed. */
enum class CodeRemovalError {
    /** 409: an agent still uses the machine. */
    IN_USE,

    /** 404: already gone. */
    GONE,

    /** Anything else, including a worker that could not be revoked (502/504). */
    FAILED,
}

/** Why a setup approval page can't go on. */
enum class CodeSetupError {
    /** 400 / 403 / 404 / 410: not this account's, expired, or no longer allowed. Web offers no retry. */
    UNAVAILABLE,

    /** 429. */
    RATE_LIMITED,

    /** Anything else: worth a retry. */
    FAILED,
}

sealed interface CodeSetupOutcome {
    data class Attempt(val attempt: CodeSetupAttempt) : CodeSetupOutcome
    data class Failed(val error: CodeSetupError) : CodeSetupOutcome
}

/**
 * Settings › Code execution: the user's machines, pairing, permissions, removal and approving a
 * worker's setup. Network-direct, never cached: the list carries live permissions and a removal
 * revokes a worker. A change refreshes `/api/endpoints`, whose `statefulCodeSessions.environments`
 * the chat composer reads, as web invalidates it.
 */
interface CodeMachinesRepository {
    suspend fun list(): Result<CodeEnvironmentsResponse>

    suspend fun pair(name: String, controlPlaneId: String): CodePairingOutcome

    /** The machine's new permission choices. True when saved. */
    suspend fun updatePermissions(environmentId: String, settings: CodeEnvironmentUserSettings): Boolean

    /** Null when removed. */
    suspend fun remove(environmentId: String): CodeRemovalError?

    suspend fun setupAttempt(attemptId: String): CodeSetupOutcome

    suspend fun approveSetup(attemptId: String, controlPlaneId: String): CodeSetupOutcome

    suspend fun denySetup(attemptId: String): CodeSetupOutcome
}

class CodeMachinesRepositoryImpl(
    private val api: CodeEnvironmentsApi,
    private val configRepository: ConfigRepository,
) : CodeMachinesRepository {
    override suspend fun list(): Result<CodeEnvironmentsResponse> = safeApiCall { api.list() }

    override suspend fun pair(name: String, controlPlaneId: String): CodePairingOutcome =
        when (val result = safeApiCall { api.pair(CodeEnvironmentPairingRequest(name, controlPlaneId)) }) {
            is Result.Success -> {
                configRepository.fetchEndpoints()
                CodePairingOutcome.Paired(result.data)
            }
            is Result.Error -> CodePairingOutcome.Failed(pairingError(result.exception as? ApiException))
            Result.Loading -> CodePairingOutcome.Failed(CodePairingError.UNAVAILABLE)
        }

    private fun pairingError(error: ApiException?): CodePairingError = when (error?.statusCode) {
        HTTP_BAD_REQUEST -> CodePairingError.INVALID_NAME
        HTTP_FORBIDDEN -> CodePairingError.DISABLED
        HTTP_NOT_FOUND -> CodePairingError.CONTROL_PLANE_GONE
        // The server's own words are the only thing telling the limit apart from the other 409s.
        HTTP_CONFLICT -> if (error.body?.contains(LIMIT_REACHED_TEXT, ignoreCase = true) == true) {
            CodePairingError.LIMIT_REACHED
        } else {
            CodePairingError.REFUSED
        }
        HTTP_TOO_MANY_REQUESTS -> CodePairingError.RATE_LIMITED
        else -> CodePairingError.UNAVAILABLE
    }

    override suspend fun updatePermissions(environmentId: String, settings: CodeEnvironmentUserSettings): Boolean {
        val saved = safeApiCall { api.updateSettings(environmentId, settings) } is Result.Success
        if (saved) configRepository.fetchEndpoints()
        return saved
    }

    override suspend fun remove(environmentId: String): CodeRemovalError? =
        when (val result = safeApiCall { api.remove(environmentId) }) {
            is Result.Success -> {
                configRepository.fetchEndpoints()
                null
            }
            is Result.Error -> when ((result.exception as? ApiException)?.statusCode) {
                HTTP_CONFLICT -> CodeRemovalError.IN_USE
                HTTP_NOT_FOUND -> CodeRemovalError.GONE
                else -> CodeRemovalError.FAILED
            }
            Result.Loading -> CodeRemovalError.FAILED
        }

    override suspend fun setupAttempt(attemptId: String): CodeSetupOutcome = setup(attemptId) { api.setupAttempt(attemptId) }

    override suspend fun approveSetup(attemptId: String, controlPlaneId: String): CodeSetupOutcome =
        setup(attemptId) { api.approveSetup(attemptId, CodeSetupApprovalRequest(controlPlaneId)) }
            .also { if (it is CodeSetupOutcome.Attempt) configRepository.fetchEndpoints() }

    override suspend fun denySetup(attemptId: String): CodeSetupOutcome = setup(attemptId) { api.denySetup(attemptId) }

    /** Web refuses an answer naming another attempt; so does this. */
    private suspend fun setup(attemptId: String, call: suspend () -> CodeSetupAttempt): CodeSetupOutcome =
        when (val result = safeApiCall { call() }) {
            is Result.Success -> if (result.data.attemptId == attemptId) {
                CodeSetupOutcome.Attempt(result.data)
            } else {
                CodeSetupOutcome.Failed(CodeSetupError.FAILED)
            }
            is Result.Error -> CodeSetupOutcome.Failed(
                when ((result.exception as? ApiException)?.statusCode) {
                    HTTP_BAD_REQUEST, HTTP_FORBIDDEN, HTTP_NOT_FOUND, HTTP_GONE -> CodeSetupError.UNAVAILABLE
                    HTTP_TOO_MANY_REQUESTS -> CodeSetupError.RATE_LIMITED
                    else -> CodeSetupError.FAILED
                },
            )
            Result.Loading -> CodeSetupOutcome.Failed(CodeSetupError.FAILED)
        }

    private companion object {
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_GONE = 410
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val LIMIT_REACHED_TEXT = "limit reached"
    }
}

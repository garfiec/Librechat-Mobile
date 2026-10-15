package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.request.ResendVerificationRequest
import com.garfiec.librechat.core.model.request.VerifyEmailRequest
import com.garfiec.librechat.core.model.response.TermsAcceptResponse
import com.garfiec.librechat.core.model.response.TermsResponse
import com.garfiec.librechat.core.network.api.UserApi

class UserRepositoryImpl(
    private val userApi: UserApi,
    private val accountRoster: AccountRoster,
    private val activeAccountProvider: ActiveAccountProvider,
) : UserRepository {

    override suspend fun getUser(): Result<User> = safeApiCall {
        userApi.getUser()
    }

    override suspend fun deleteUser(token: String?, backupCode: String?): Result<Unit> = safeApiCall {
        userApi.deleteUser(token = token, backupCode = backupCode)
    }

    override suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String, fileName: String): Result<String> {
        // Captured before the request so a switch mid-upload can't move the avatar onto another account.
        val accountId = activeAccountProvider.currentAccountId()
        val result = safeApiCall { userApi.uploadAvatar(imageBytes, mimeType, fileName).url }
        if (result is Result.Success && accountId != null) {
            // Best-effort: the server already holds the avatar, and the next session start backfills it.
            suspendRunCatching { accountRoster.updateAvatar(accountId.value, result.data) }
                .onFailure { Logger.w(it) { "Avatar uploaded but the roster entry was not updated" } }
        }
        return result
    }

    override suspend fun verifyEmail(token: String): Result<Unit> = safeApiCall {
        userApi.verifyEmail(VerifyEmailRequest(token = token))
    }

    override suspend fun resendVerification(email: String): Result<Unit> = safeApiCall {
        userApi.resendVerification(ResendVerificationRequest(email = email))
    }

    override suspend fun getTerms(): Result<TermsResponse> = safeApiCall {
        userApi.getTerms()
    }

    override suspend fun acceptTerms(): Result<TermsAcceptResponse> = safeApiCall {
        userApi.acceptTerms()
    }
}

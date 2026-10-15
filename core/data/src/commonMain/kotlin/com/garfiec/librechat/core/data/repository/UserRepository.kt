package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.response.TermsAcceptResponse
import com.garfiec.librechat.core.model.response.TermsResponse

interface UserRepository {
    suspend fun getUser(): Result<User>
    suspend fun deleteUser(token: String? = null, backupCode: String? = null): Result<Unit>

    /**
     * Uploads a profile picture and returns the avatar URL the server stored (relative under local
     * storage; resolve it with `resolveAvatarUrl` before display). Also updates the active account's
     * roster entry so the drawer shows it without waiting for the next session start.
     */
    suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String, fileName: String): Result<String>

    suspend fun verifyEmail(token: String): Result<Unit>
    suspend fun resendVerification(email: String): Result<Unit>
    suspend fun getTerms(): Result<TermsResponse>
    suspend fun acceptTerms(): Result<TermsAcceptResponse>
}

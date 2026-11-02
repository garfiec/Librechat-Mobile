package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.auth.Passkey
import com.garfiec.librechat.core.network.api.PasskeyApi

/**
 * Passkey management (v0.8.9-rc1): list, rename, remove. Network-direct, never cached: a password
 * reset on the server deletes every passkey, and a cache would keep showing them.
 */
interface PasskeyRepository {
    suspend fun list(): Result<List<Passkey>>
    suspend fun rename(id: String, name: String): Result<Passkey>

    /** [password] is the account password, or null for an account the server lets remove without one. */
    suspend fun remove(id: String, password: String?): Result<Unit>
}

class PasskeyRepositoryImpl(
    private val api: PasskeyApi,
) : PasskeyRepository {
    override suspend fun list(): Result<List<Passkey>> = safeApiCall { api.listPasskeys() }

    override suspend fun rename(id: String, name: String): Result<Passkey> =
        safeApiCall { api.renamePasskey(id, name) }

    override suspend fun remove(id: String, password: String?): Result<Unit> =
        safeApiCall { api.deletePasskey(id, password) }
}

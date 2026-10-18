package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.apiCallCatching
import com.garfiec.librechat.core.common.result.toSafeError
import com.garfiec.librechat.core.model.Balance
import com.garfiec.librechat.core.network.api.BalanceApi

class BalanceRepositoryImpl(
    private val balanceApi: BalanceApi,
) : BalanceRepository {

    /**
     * A 404 is the server saying the user has no balance record yet, and a 204 (null) that balance
     * tracking is off for this user; web shows both as a zero balance. The 404 is mapped here rather
     * than through [toSafeError] so it is neither logged as a failure nor shown as one.
     */
    override suspend fun getBalance(): Result<Balance> =
        apiCallCatching({ Result.Success(balanceApi.getBalance() ?: Balance()) }) { e ->
            if (e is ApiException && e.statusCode == HTTP_NOT_FOUND) Result.Success(Balance()) else e.toSafeError()
        }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}

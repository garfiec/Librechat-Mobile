package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Balance
import com.garfiec.librechat.core.network.api.BalanceApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BalanceRepositoryImplTest {

    private val api = mockk<BalanceApi>()
    private val repository = BalanceRepositoryImpl(api)

    @Test
    fun `a 404 is a user with no balance record, shown as zero like web`() = runTest {
        coEvery { api.getBalance() } throws ApiException(statusCode = 404, message = "Not found")

        assertThat(repository.getBalance()).isEqualTo(Result.Success(Balance()))
    }

    @Test
    fun `a 204 for a user whose balance tracking is off is shown as zero like web`() = runTest {
        coEvery { api.getBalance() } returns null

        assertThat(repository.getBalance()).isEqualTo(Result.Success(Balance()))
    }

    @Test
    fun `any other failure is an error`() = runTest {
        coEvery { api.getBalance() } throws ApiException(statusCode = 500, message = "Server error")

        assertThat(repository.getBalance()).isInstanceOf(Result.Error::class.java)
    }
}

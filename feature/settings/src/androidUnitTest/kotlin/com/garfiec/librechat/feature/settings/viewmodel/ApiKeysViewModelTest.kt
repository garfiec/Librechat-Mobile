package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ApiKeyRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApiKeysViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository = mockk<ApiKeyRepository>(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a 404 says the server has no API keys`() = runTest {
        coEvery { repository.listApiKeys() } returns
            Result.Error(exception = ApiException(statusCode = 404, message = "Not Found"), message = "Not Found")

        val viewModel = ApiKeysViewModel(repository)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadFailure).isEqualTo(ApiKeysLoadFailure.NotSupported)
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `any other failure keeps its message, and Retry clears it once the list loads`() = runTest {
        coEvery { repository.listApiKeys() } returns
            Result.Error(exception = ApiException(statusCode = 500, message = "Server error"), message = "Server error")

        val viewModel = ApiKeysViewModel(repository)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadFailure).isEqualTo(ApiKeysLoadFailure.Failed("Server error"))

        coEvery { repository.listApiKeys() } returns Result.Success(emptyList())
        viewModel.loadKeys()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadFailure).isNull()
    }
}

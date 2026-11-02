package com.garfiec.librechat.feature.auth.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AuthRepository
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

/**
 * The pre-auth refusals (v0.8.9-rc1) are keyed on the server's code, so the other 403s on these
 * routes (the user-agent soft-ban, a ban, `terms_not_accepted`, a proxy page) keep their own handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRefusalTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository = mockk<AuthRepository>(relaxed = true)

    @Before
    fun setup() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `only a coded 403 is a refusal`() {
        assertThat(authRefusalOf(403, TENANT_BODY)).isEqualTo(AuthRefusal.TenantRequired)
        assertThat(authRefusalOf(403, FEDERATED_BODY)).isEqualTo(AuthRefusal.FederatedBlocked)
        assertThat(authRefusalOf(400, TENANT_BODY)).isNull()
    }

    @Test
    fun `the soft-ban, ban, terms and proxy 403s are not refusals`() {
        // The ua-parser-js soft-ban and the ban response are a bare message.
        assertThat(authRefusalOf(403, """{"message":"Illegal request"}""")).isNull()
        assertThat(authRefusalOf(403, """{"message":"Your account has been temporarily banned"}""")).isNull()
        assertThat(authRefusalOf(403, """{"code":"terms_not_accepted"}""")).isNull()
        assertThat(authRefusalOf(403, "<html><body>Forbidden</body></html>")).isNull()
        assertThat(authRefusalOf(403, null)).isNull()
    }

    @Test
    fun `forgot password words a tenant-required 403`() = runTest {
        coEvery { authRepository.requestPasswordReset(any()) } returns tenantError()
        val viewModel = ForgotPasswordViewModel(authRepository)
        viewModel.onEmailChanged("user@example.com")
        viewModel.requestReset()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.refusal).isEqualTo(AuthRefusal.TenantRequired)
        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.onEmailChanged("")
        viewModel.requestReset()
        assertThat(viewModel.uiState.value.refusal).isNull()
    }

    @Test
    fun `reset password words a tenant-required 403`() = runTest {
        coEvery { authRepository.resetPassword(any(), any(), any(), any()) } returns tenantError()
        val viewModel = ResetPasswordViewModel(authRepository, initialUserId = "u1", initialToken = "t1")
        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("password123")
        viewModel.resetPassword()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.refusal).isEqualTo(AuthRefusal.TenantRequired)
        assertThat(viewModel.uiState.value.error).isNotNull()
    }

    @Test
    fun `an ordinary 403 on reset keeps the screen's own wording`() = runTest {
        coEvery { authRepository.resetPassword(any(), any(), any(), any()) } returns Result.Error(
            exception = ApiException(403, "Illegal request", body = """{"message":"Illegal request"}"""),
            message = "Illegal request",
        )
        val viewModel = ResetPasswordViewModel(authRepository, initialUserId = "u1", initialToken = "t1")
        viewModel.onPasswordChanged("password123")
        viewModel.onConfirmPasswordChanged("password123")
        viewModel.resetPassword()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.refusal).isNull()
    }

    private fun tenantError() = Result.Error(
        exception = ApiException(403, "server message", body = TENANT_BODY, serverAuthored = true),
        message = "server message",
    )

    private companion object {
        const val TENANT_BODY =
            """{"message":"Tenant context required in strict isolation mode","code":"TENANT_CONTEXT_REQUIRED"}"""
        const val FEDERATED_BODY =
            """{"code":"TWO_FACTOR_FEDERATED_LOGIN_BLOCKED","message":"Sign in with your identity provider to continue."}"""
    }
}

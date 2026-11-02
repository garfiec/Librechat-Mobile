package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PasskeyRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.auth.Passkey
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class PasskeysViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val passkeyRepository = mockk<PasskeyRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore> {
        every { currentUrlFlow } returns MutableStateFlow("https://chat.chen.family/")
    }
    private val pixel = Passkey(id = "a", name = "Pixel 9 Pro", backedUp = true, createdAt = "2026-09-14T10:00:00Z")
    private val yubikey = Passkey(id = "b", name = "YubiKey 5C")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { passkeyRepository.list() } returns Result.Success(listOf(pixel, yubikey))
        coEvery { userRepository.getUser() } returns Result.Success(User(email = "m@x.dev", provider = "local"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        PasskeysViewModel(passkeyRepository, userRepository, configRepository, serverDataStore)

    private fun status(code: Int) = Result.Error(exception = ApiException(statusCode = code, message = "x"), message = "x")

    private val PasskeysViewModel.rows get() = (uiState.value.content as PasskeysContent.Loaded).rows

    @Test
    fun `loads the list and the server host for the browser line`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.rows.map { it.name }).containsExactly("Pixel 9 Pro", "YubiKey 5C").inOrder()
        assertThat(viewModel.rows.first().synced).isTrue()
        assertThat(viewModel.uiState.value.serverHost).isEqualTo("chat.chen.family")
    }

    @Test
    fun `a failed first load is the error state, not the empty one`() = runTest {
        coEvery { passkeyRepository.list() } returns Result.Error(message = "offline")
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.content).isEqualTo(PasskeysContent.Failed)
    }

    @Test
    fun `a 404 refetches the config, since passkeys were switched off`() = runTest {
        coEvery { passkeyRepository.list() } returns status(404)
        createViewModel()
        advanceUntilIdle()
        coVerify { configRepository.fetchStartupConfig() }
    }

    @Test
    fun `a quiet refresh keeps the shown list when it fails`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { passkeyRepository.list() } returns Result.Error(message = "offline")
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        assertThat(viewModel.rows).hasSize(2)
    }

    @Test
    fun `rename trims, saves and replaces the row`() = runTest {
        coEvery { passkeyRepository.rename("b", "Work key") } returns Result.Success(yubikey.copy(name = "Work key"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRename("b")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rename?.canSave).isFalse()
        viewModel.onRenameChanged("  Work key ")
        viewModel.saveRename()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.rename).isNull()
        assertThat(viewModel.rows.last().name).isEqualTo("Work key")
    }

    @Test
    fun `rename caps the name at the server's length`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.startRename("a")
        viewModel.onRenameChanged("x".repeat(80))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rename?.name).hasLength(60)
    }

    @Test
    fun `remove asks a local account for its password`() = runTest {
        coEvery { passkeyRepository.remove("b", "pw") } returns Result.Success(Unit)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRemove("b")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.remove?.showPasswordField).isTrue()
        assertThat(viewModel.uiState.value.remove?.canRemove).isFalse()
        viewModel.onRemovePasswordChanged("pw")
        viewModel.confirmRemove()
        advanceUntilIdle()

        coVerify { passkeyRepository.remove("b", "pw") }
        assertThat(viewModel.uiState.value.remove).isNull()
    }

    @Test
    fun `a wrong password shows inline and keeps the dialog`() = runTest {
        coEvery { passkeyRepository.remove(any(), any()) } returns status(403)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRemove("b")
        viewModel.onRemovePasswordChanged("nope")
        viewModel.confirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.remove?.error).isEqualTo(PasskeyRemoveError.IncorrectPassword)
        assertThat(viewModel.uiState.value.remove?.isRemoving).isFalse()
    }

    @Test
    fun `a non-local account removes without a password until the server demands one`() = runTest {
        coEvery { userRepository.getUser() } returns Result.Success(User(email = "m@x.dev", provider = "openid"))
        coEvery { passkeyRepository.remove("b", null) } returns status(403)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRemove("b")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.remove?.showPasswordField).isFalse()
        assertThat(viewModel.uiState.value.remove?.canRemove).isTrue()
        viewModel.confirmRemove()
        advanceUntilIdle()

        coVerify { passkeyRepository.remove("b", null) }
        assertThat(viewModel.uiState.value.remove?.showPasswordField).isTrue()
    }

    @Test
    fun `a 404 on remove is treated as done and says so`() = runTest {
        coEvery { passkeyRepository.remove(any(), any()) } returns status(404)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRemove("b")
        viewModel.onRemovePasswordChanged("pw")
        coEvery { passkeyRepository.list() } returns Result.Success(listOf(pixel))
        viewModel.confirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.remove).isNull()
        assertThat(viewModel.uiState.value.notice).isEqualTo(PasskeysNotice.AlreadyRemoved)
        assertThat(viewModel.rows.map { it.id }).containsExactly("a")
    }

    @Test
    fun `a 429 on remove says to wait`() = runTest {
        coEvery { passkeyRepository.remove(any(), any()) } returns status(429)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRemove("b")
        viewModel.onRemovePasswordChanged("pw")
        viewModel.confirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.remove?.error).isEqualTo(PasskeyRemoveError.TooManyAttempts)
    }

    @Test
    fun `dismissing drops the typed password`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.startRemove("b")
        viewModel.onRemovePasswordChanged("pw")
        viewModel.dismissRemove()
        viewModel.startRemove("b")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.remove?.password).isEmpty()
    }

    @Test
    fun `one dialog at a time`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.startRename("a")
        viewModel.startRemove("b")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.remove).isNull()
    }
}

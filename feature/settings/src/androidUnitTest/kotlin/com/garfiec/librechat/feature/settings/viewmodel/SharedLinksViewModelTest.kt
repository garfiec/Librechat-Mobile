package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.response.SharedLinksResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedLinksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val shareRepository = mockk<ShareRepository>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val version = MutableStateFlow<String?>(null)

    private fun link(shareId: String, title: String = "Chat $shareId") =
        SharedLink(conversationId = "c-$shareId", shareId = shareId, title = title, createdAt = "2026-01-01")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { serverDataStore.currentUrlFlow } returns MutableStateFlow("https://chat.example.com")
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { configRepository.detectedBackendVersion } returns version
        coEvery { shareRepository.getSharedLinksPaginated(null) } returns
            Result.Success(SharedLinksResponse(links = listOf(link("a"), link("b")), nextCursor = "n1", hasNextPage = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        SharedLinksViewModel(shareRepository, serverDataStore, roleRepository, configRepository)

    @Test
    fun `loads the first page on creation`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.links.map { it.shareId }).containsExactly("a", "b").inOrder()
        assertThat(state.hasNextPage).isTrue()
        assertThat(state.isLoading).isFalse()
        assertThat(state.serverUrl).isEqualTo("https://chat.example.com")
    }

    @Test
    fun `loadMore appends the next page`() = runTest {
        coEvery { shareRepository.getSharedLinksPaginated("n1") } returns
            Result.Success(SharedLinksResponse(links = listOf(link("c")), hasNextPage = false))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.links.map { it.shareId }).containsExactly("a", "b", "c").inOrder()
        assertThat(viewModel.uiState.value.hasNextPage).isFalse()
    }

    @Test
    fun `update patches the row's shareId and keeps its title`() = runTest {
        coEvery { shareRepository.updateShareLink("a") } returns
            Result.Success(SharedLink(conversationId = "c-a", shareId = "a2"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.update("a")
        advanceUntilIdle()

        val row = viewModel.uiState.value.links.first()
        assertThat(row.shareId).isEqualTo("a2")
        assertThat(row.title).isEqualTo("Chat a")
    }

    @Test
    fun `a 403 on update explains that delete still works`() = runTest {
        coEvery { shareRepository.updateShareLink("a") } returns
            Result.Error(exception = ApiException(statusCode = 403, message = "Forbidden"))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.update("a")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).contains("You can still delete this link")
        viewModel.dismissError()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `delete removes the row`() = runTest {
        coEvery { shareRepository.deleteShareLink("a") } returns Result.Success(Unit)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.delete("a")
        advanceUntilIdle()

        coVerify { shareRepository.deleteShareLink("a") }
        assertThat(viewModel.uiState.value.links.map { it.shareId }).containsExactly("b")
    }

    @Test
    fun `update keeps the URL only on a confirmed rc1+ server`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.updateKeepsUrl).isFalse()

        version.value = "0.8.8-rc1"
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.updateKeepsUrl).isTrue()
    }
}

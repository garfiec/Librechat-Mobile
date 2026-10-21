package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationPage
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectChatsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val conversationRepository = mockk<ConversationRepository>(relaxed = true)
    private val projectRepository = mockk<ProjectRepository>(relaxed = true)
    private val revision = MutableStateFlow(0L)
    private val userPermissions = MutableStateFlow<UserRolePermissions?>(null)
    private val startupConfig = MutableStateFlow<StartupConfig?>(StartupConfig(sharedLinksEnabled = true))

    private fun page(vararg ids: String, next: String? = null) =
        Result.Success(ConversationPage(ids.map { Conversation(conversationId = it) }, next))

    private suspend fun MockKMatcherScope.chats(cursor: String? = null, sortBy: String = "updatedAt") =
        conversationRepository.getConversationsForProject("p1", cursor, any(), sortBy, "desc")

    private fun viewModel() = ProjectChatsViewModel(
        projectId = "p1",
        conversationRepository = conversationRepository,
        tagRepository = mockk(relaxed = true),
        shareLinkControllerFactory = mockk(relaxed = true),
        conversationExporter = mockk(relaxed = true),
        roleRepository = mockk(relaxed = true) {
            every { userPermissions } returns this@ProjectChatsViewModelTest.userPermissions
        },
        configRepository = mockk(relaxed = true) {
            every { endpointConfigs } returns MutableStateFlow(emptyMap())
            every { startupConfig } returns this@ProjectChatsViewModelTest.startupConfig
        },
        projectRepository = projectRepository,
        dayBoundaries = emptyFlow(),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepository.revision } returns revision
        coEvery { projectRepository.getProject("p1") } returns Result.Success(ChatProject(id = "p1", name = "Thesis"))
        coEvery { chats() } returns page("a", "b")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an append in flight when the project changes does not land on the reloaded list`() =
        runTest(testDispatcher) {
            coEvery { chats() } returns page("a", next = "c1")
            val slowAppend = CompletableDeferred<Result<ConversationPage>>()
            coEvery { chats(cursor = "c1") } coAnswers { slowAppend.await() }
            val vm = viewModel()
            advanceUntilIdle()

            vm.loadMore()
            runCurrent()
            coEvery { chats() } returns page("x")
            revision.value = 1
            advanceUntilIdle()
            slowAppend.complete(page("stale"))
            advanceUntilIdle()

            assertThat(vm.getConversation("x")).isNotNull()
            assertThat(vm.getConversation("stale")).isNull()
            assertThat(vm.uiState.value.nextCursor).isNull()
        }

    @Test
    fun `a sort change wins over a change reload still in flight`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val slowReload = CompletableDeferred<Result<ConversationPage>>()
        coEvery { chats() } coAnswers { slowReload.await() }
        coEvery { chats(sortBy = "createdAt") } returns page("created")

        revision.value = 1
        runCurrent()
        vm.setSort(ProjectChatsSort.CREATED)
        advanceUntilIdle()
        slowReload.complete(page("updated"))
        advanceUntilIdle()

        assertThat(vm.getConversation("created")).isNotNull()
        assertThat(vm.getConversation("updated")).isNull()
    }

    @Test
    fun `returning reloads without the pull-to-refresh spinner`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.refreshOnReturn()
        runCurrent()
        assertThat(vm.uiState.value.isRefreshing).isFalse()
        assertThat(vm.uiState.value.isLoading).isFalse()
        advanceUntilIdle()

        coVerify(exactly = 2) { chats() }
    }

    @Test
    fun `a next page asked for during a reload runs from the reloaded list`() = runTest(testDispatcher) {
        coEvery { chats() } returns page("a", next = "c1")
        val vm = viewModel()
        advanceUntilIdle()
        val reload = CompletableDeferred<Result<ConversationPage>>()
        coEvery { chats() } coAnswers { reload.await() }

        vm.refreshOnReturn()
        runCurrent()
        vm.loadMore()
        reload.complete(page("a", next = "c2"))
        advanceUntilIdle()

        coVerify(exactly = 0) { chats(cursor = "c1") }
        coVerify(exactly = 1) { chats(cursor = "c2") }
    }

    @Test
    fun `a project the server no longer has is reported deleted`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.projectDeleted).isFalse()

        coEvery { projectRepository.getProject("p1") } returns
            Result.Error(ApiException(statusCode = 404, message = "Project not found"))
        revision.value = 1
        advanceUntilIdle()

        assertThat(vm.uiState.value.projectDeleted).isTrue()
    }

    @Test
    fun `any other failure to load the project keeps the page`() = runTest(testDispatcher) {
        coEvery { projectRepository.getProject("p1") } returns
            Result.Error(ApiException(statusCode = 500, message = "Server error"))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.projectDeleted).isFalse()
    }

    @Test
    fun `a quiet reload keeps as many chats as were loaded`() = runTest(testDispatcher) {
        coEvery { chats() } returns page(*Array(30) { "c$it" })
        val vm = viewModel()
        advanceUntilIdle()

        vm.refreshOnReturn()
        advanceUntilIdle()

        coVerify(exactly = 1) { conversationRepository.getConversationsForProject("p1", null, 30, "updatedAt", "desc") }
    }

    @Test
    fun `returning retries a project that failed to load`() = runTest(testDispatcher) {
        coEvery { projectRepository.getProject("p1") } returns Result.Error(message = "offline")
        val vm = viewModel()
        advanceUntilIdle()
        coEvery { projectRepository.getProject("p1") } returns Result.Success(ChatProject(id = "p1", name = "Thesis"))

        vm.refreshOnReturn()
        advanceUntilIdle()

        assertThat(vm.uiState.value.project?.name).isEqualTo("Thesis")
    }

    @Test
    fun `Share shows when shared links are on and the role may create them`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.sharedLinksEnabled).isTrue()

        userPermissions.value = UserRolePermissions(
            name = "USER",
            permissions = mapOf("SHARED_LINKS" to mapOf("CREATE" to false)),
        )
        advanceUntilIdle()
        assertThat(vm.uiState.value.sharedLinksEnabled).isFalse()

        userPermissions.value = null
        startupConfig.value = StartupConfig(sharedLinksEnabled = false)
        advanceUntilIdle()
        assertThat(vm.uiState.value.sharedLinksEnabled).isFalse()
    }
}

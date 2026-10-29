package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerFailure
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationPage
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.bookmarks_load_failed
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    private val cachedTags = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val tagRepository = mockk<TagRepository>(relaxed = true) {
        every { observeTags() } returns MutableStateFlow(listOf(ConversationTag(tag = "work")))
        every { observeCachedTags(any()) } returns cachedTags
    }
    private val userPermissions = MutableStateFlow<UserRolePermissions?>(null)
    private val startupConfig = MutableStateFlow<StartupConfig?>(StartupConfig(sharedLinksEnabled = true))

    private fun page(vararg ids: String, next: String? = null) =
        Result.Success(ConversationPage(ids.map { Conversation(conversationId = it) }, next))

    private suspend fun MockKMatcherScope.chats(cursor: String? = null, sortBy: String = "updatedAt") =
        conversationRepository.getConversationsForProject("p1", cursor, any(), sortBy, "desc")

    private fun viewModel() = ProjectChatsViewModel(
        projectId = "p1",
        conversationRepository = conversationRepository,
        tagRepository = tagRepository,
        shareLinkControllerFactory = mockk(relaxed = true),
        conversationExporter = mockk(relaxed = true),
        roleRepository = mockk(relaxed = true) {
            every { userPermissions } returns this@ProjectChatsViewModelTest.userPermissions
        },
        configRepository = mockk(relaxed = true) {
            every { endpointConfigs } returns MutableStateFlow(emptyMap())
            every { startupConfig } returns this@ProjectChatsViewModelTest.startupConfig
            every { serverUrl } returns flowOf("https://srv")
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
    fun `a row's favorite state comes from its cached row, which the paged list never carries`() =
        runTest(testDispatcher) {
            cachedTags.value = mapOf("a" to listOf(SAVED_TAG))
            val vm = viewModel()
            advanceUntilIdle()

            assertThat(vm.getConversation("a")?.tags).containsExactly(SAVED_TAG)
            assertThat(vm.getConversation("b")?.tags).isEmpty()

            cachedTags.value = mapOf("a" to emptyList())
            advanceUntilIdle()
            assertThat(vm.getConversation("a")?.tags).isEmpty()
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

    private fun role(bookmarksUse: Boolean) =
        UserRolePermissions(name = "USER", permissions = mapOf("BOOKMARKS" to mapOf("USE" to bookmarksUse)))

    @Test
    fun `bookmarks need both the role's BOOKMARKS USE and interface bookmarks`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.bookmarksEnabled).isTrue()

        startupConfig.value = StartupConfig(interfaceConfig = InterfaceConfig(bookmarks = false))
        advanceUntilIdle()
        assertThat(vm.uiState.value.bookmarksEnabled).isFalse()

        startupConfig.value = StartupConfig()
        userPermissions.value = role(bookmarksUse = false)
        advanceUntilIdle()
        assertThat(vm.uiState.value.bookmarksEnabled).isFalse()
    }

    @Test
    fun `Bookmarks opens the checklist on the chat's server tags`() = runTest(testDispatcher) {
        coEvery { tagRepository.getConversationTags("a") } returns Result.Success(listOf("work"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.openBookmarks("a")
        advanceUntilIdle()

        assertThat(vm.bookmarkPicker.state.value.conversationId).isEqualTo("a")
        assertThat(vm.bookmarkPicker.state.value.attached).containsExactly("work")
    }

    @Test
    fun `the checklist doesn't open while bookmarks are off`() = runTest(testDispatcher) {
        startupConfig.value = StartupConfig(interfaceConfig = InterfaceConfig(bookmarks = false))
        val vm = viewModel()
        advanceUntilIdle()

        vm.openBookmarks("a")
        advanceUntilIdle()

        assertThat(vm.bookmarkPicker.state.value.isOpen).isFalse()
        coVerify(exactly = 0) { tagRepository.getConversationTags(any()) }
    }

    @Test
    fun `bookmarks turning off takes an open checklist down`() = runTest(testDispatcher) {
        coEvery { tagRepository.getConversationTags("a") } returns Result.Success(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        vm.openBookmarks("a")
        advanceUntilIdle()

        userPermissions.value = role(bookmarksUse = false)
        advanceUntilIdle()

        assertThat(vm.bookmarkPicker.state.value.isOpen).isFalse()
    }

    @Test
    fun `a failed tick is reported inside the checklist, not behind it`() = runTest(testDispatcher) {
        coEvery { tagRepository.getConversationTags("a") } returns Result.Success(emptyList())
        coEvery { tagRepository.setConversationTag("a", "work", true) } returns Result.Error(message = "boom")
        val vm = viewModel()
        val events = mutableListOf<ConversationListEvent>()
        val job = launch { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.openBookmarks("a")
        advanceUntilIdle()

        vm.bookmarkPicker.toggle("work")
        advanceUntilIdle()

        assertThat(vm.bookmarkPicker.state.value.error).isEqualTo(BookmarkPickerFailure.ToggleFailed("work"))
        assertThat(events).isEmpty()
        assertThat(vm.bookmarkPicker.state.value.attached).isEmpty()
        job.cancel()
    }

    @Test
    fun `a checklist that can't load closes with the screen's snackbar`() = runTest(testDispatcher) {
        coEvery { tagRepository.getConversationTags("a") } returns Result.Error(message = "offline")
        val vm = viewModel()
        val events = mutableListOf<ConversationListEvent>()
        val job = launch { vm.events.collect { events += it } }
        advanceUntilIdle()

        vm.openBookmarks("a")
        advanceUntilIdle()

        assertThat(vm.bookmarkPicker.state.value.isOpen).isFalse()
        assertThat(events).containsExactly(ConversationListEvent.ShowErrorRes(Res.string.bookmarks_load_failed))
        job.cancel()
    }
}

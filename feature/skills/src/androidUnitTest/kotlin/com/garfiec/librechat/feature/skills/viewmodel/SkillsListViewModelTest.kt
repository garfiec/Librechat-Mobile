package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.model.response.SkillImportFailedResponse
import com.garfiec.librechat.core.model.response.SkillListResponse
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.garfiec.librechat.feature.skills.components.PickedDocument
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillsListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val skillsRepository = mockk<SkillsRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val saveNotices = SkillSaveNotices()
    private val json = Json { ignoreUnknownKeys = true }

    private val me = "65f0c0ffee0000000000abcd"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { configRepository.startupConfig } returns MutableStateFlow<StartupConfig?>(null)
        coEvery { skillsRepository.getSkillStates() } returns Result.Success(emptyMap())
        coEvery { userRepository.getUser() } returns Result.Success(
            json.decodeFromString(User.serializer(), """{"id":"$me","email":"ada@example.com","role":"USER"}"""),
        )
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun vm() = SkillsListViewModel(skillsRepository, roleRepository, userRepository, configRepository, saveNotices)

    private fun summary(id: String) = SkillSummary(id = id, name = "n-$id")

    @Test
    fun `loadFirstPage populates skills and paging state on success`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.skills.map { it.id }).containsExactly("a")
        assertThat(state.hasMore).isTrue()
        assertThat(state.cursor).isEqualTo("cur1")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `loadFirstPage surfaces error on failure`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Error(message = "boom")
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.LOAD_SKILLS, "boom"))
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `loadMore appends the next page using the cursor`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns
            Result.Success(SkillListResponse(skills = listOf(summary("b")), hasMore = false, after = null))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(viewModel.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `refresh replaces the list`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("z"))))
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("z")
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun `canCreate is false for null role and true only when SKILLS CREATE granted`() =
        runTest(testDispatcher) {
            val denied = vm()
            advanceUntilIdle()
            assertThat(denied.uiState.value.canCreate).isFalse()

            every { roleRepository.userPermissions } returns MutableStateFlow(
                UserRolePermissions(name = "USER", permissions = mapOf("SKILLS" to mapOf("CREATE" to true))),
            )
            val granted = vm()
            advanceUntilIdle()
            assertThat(granted.uiState.value.canCreate).isTrue()
        }

    @Test
    fun `refreshOnReturn does the initial load when list empty`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a")
    }

    private val archive = PickedDocument(bytes = byteArrayOf(1), filename = "s.zip", mimeType = "application/zip")

    private fun importRefused(status: Int, body: String) = Result.Error(
        exception = ApiException(statusCode = status, message = "Skill import failed", body = body),
        message = "Skill import failed",
    )

    /**
     * v0.8.8-rc4: a rolled-back import reports the files it lost, and a failed rollback leaves the
     * partial skill listed server-side — so the report must survive the reload, and the reload
     * must happen.
     */
    @Test
    fun `a refused archive import keeps the failure report and reloads the list`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("partial")), hasMore = false, after = null))
        coEvery { skillsRepository.importSkill(any(), any(), any()) } returns importRefused(
            500,
            """{"error":"skill_import_rollback_failed","message":"x",""" +
                """"failedFiles":[{"path":"a.bin","reason":"persistence_failed"}],"skillId":"partial"}""",
        )
        val viewModel = vm()

        viewModel.importSkill(archive)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.importFailure?.error).isEqualTo(SkillImportFailedResponse.ROLLBACK_FAILED)
        assertThat(state.importFailure?.failedFiles?.map { it.path }).containsExactly("a.bin")
        assertThat(state.skills.map { it.id }).containsExactly("partial")
        assertThat(state.isImporting).isFalse()
        coVerify(exactly = 1) { skillsRepository.listSkills(search = null, cursor = null) }

        viewModel.dismissImportFailure()
        assertThat(viewModel.uiState.value.importFailure).isNull()
    }

    // The plain import error is a snackbar, so an empty list doesn't turn into a load-error
    // banner whose Retry reloads the list instead of importing.
    @Test
    fun `any other import failure is a snackbar and does not reload`() = runTest(testDispatcher) {
        coEvery { skillsRepository.importSkill(any(), any(), any()) } returns
            importRefused(400, """{"error":"Validation failed","issues":[]}""")
        val viewModel = vm()
        val events = collectEvents(viewModel)

        viewModel.importSkill(archive)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.importFailure).isNull()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.isImporting).isFalse()
        // A 4xx would fail the same way again: no Retry.
        assertThat(events).containsExactly(
            SkillsListEvent.ImportFailed(SkillError(SkillErrorKind.IMPORT_SKILL, "Skill import failed"), canRetry = false),
        )
        coVerify(exactly = 0) { skillsRepository.listSkills(any(), any()) }
    }

    @Test
    fun `a failed import over a failed first load keeps the load-error banner`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns Result.Error(message = "boom")
        coEvery { skillsRepository.importSkill(any(), any(), any()) } returns Result.Error(message = "offline")
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.importSkill(archive)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.LOAD_SKILLS, "boom"))
    }

    @Test
    fun `a transport import failure offers a retry that re-sends the same file`() = runTest(testDispatcher) {
        coEvery { skillsRepository.importSkill(any(), any(), any()) } returns Result.Error(message = "offline")
        val viewModel = vm()
        val events = collectEvents(viewModel)

        viewModel.importSkill(archive)
        advanceUntilIdle()
        assertThat(events).containsExactly(
            SkillsListEvent.ImportFailed(SkillError(SkillErrorKind.IMPORT_SKILL, "offline"), canRetry = true),
        )

        viewModel.retryImport()
        advanceUntilIdle()
        coVerify(exactly = 2) { skillsRepository.importSkill(archive.bytes, "s.zip", "application/zip") }
    }

    @Test
    fun `an import that fails while the list isn't collected still offers its retry on return`() = runTest(testDispatcher) {
        coEvery { skillsRepository.importSkill(any(), any(), any()) } returns Result.Error(message = "offline")
        val viewModel = vm()

        // The user opened a skill while the import ran: the list's collector is gone.
        viewModel.importSkill(archive)
        advanceUntilIdle()
        val events = collectEvents(viewModel)
        advanceUntilIdle()

        assertThat(events).containsExactly(
            SkillsListEvent.ImportFailed(SkillError(SkillErrorKind.IMPORT_SKILL, "offline"), canRetry = true),
        )
    }

    @Test
    fun `retryImport after a 4xx sends nothing`() = runTest(testDispatcher) {
        coEvery { skillsRepository.importSkill(any(), any(), any()) } returns
            importRefused(400, """{"error":"Validation failed","issues":[]}""")
        val viewModel = vm()
        viewModel.importSkill(archive)
        advanceUntilIdle()

        viewModel.retryImport()
        advanceUntilIdle()
        coVerify(exactly = 1) { skillsRepository.importSkill(any(), any(), any()) }
    }

    @Test
    fun `an import while one is in flight is ignored`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { skillsRepository.importSkill(any(), any(), any()) } coAnswers {
            gate.await()
            Result.Error(message = "offline")
        }
        val viewModel = vm()
        viewModel.importSkill(archive)
        advanceUntilIdle()
        viewModel.importSkill(archive)
        gate.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 1) { skillsRepository.importSkill(any(), any(), any()) }
    }

    // --- failures while skills are listed reach a snackbar ---

    @Test
    fun `a failed refresh over a listed page is a snackbar`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        val viewModel = vm()
        val events = collectEvents(viewModel)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns Result.Error(message = "boom")
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillsListEvent.LoadFailed(SkillError(SkillErrorKind.LOAD_SKILLS, "boom")))
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a")
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun `a pull-to-refresh over the error banner keeps the error until the result`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns Result.Error(message = "boom")
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } coAnswers {
            gate.await()
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        }
        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isRefreshing).isTrue()
        assertThat(viewModel.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.LOAD_SKILLS, "boom"))

        gate.complete(Unit)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a")
    }

    @Test
    fun `a failed first load is the banner's, not a snackbar`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns Result.Error(message = "boom")
        val viewModel = vm()
        val events = collectEvents(viewModel)
        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.LOAD_SKILLS, "boom"))
        assertThat(events).isEmpty()
    }

    @Test
    fun `a failed page is a snackbar`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns Result.Error(message = "boom")
        val viewModel = vm()
        val events = collectEvents(viewModel)
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(events).containsExactly(SkillsListEvent.PageFailed)
    }

    @Test
    fun `a snackbar still on screen doesn't hold the next page back`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns
            Result.Success(SkillListResponse(skills = listOf(summary("b"))))
        val viewModel = vm()
        // The screen takes one event and then sits in showSnackbar.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { awaitCancellation() }
        }
        viewModel.loadFirstPage()
        advanceUntilIdle()

        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns Result.Error(message = "boom")
        viewModel.refresh()
        advanceUntilIdle()
        // The second failure arrives while the first one's snackbar is still up.
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a", "b").inOrder()
    }

    // --- Search visibility and the query the list answers ---

    @Test
    fun `search shows from five skills and stays while a query is typed or shown`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = (1..4).map { summary("s$it") }))
        coEvery { skillsRepository.listSkills(search = "zz", cursor = null) } returns
            Result.Success(SkillListResponse(skills = emptyList()))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showSearch).isFalse()

        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = (1..5).map { summary("s$it") }))
        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.showSearch).isTrue()

        viewModel.onSearchQueryChanged("zz")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo("zz")
        assertThat(viewModel.uiState.value.skills).isEmpty()
        assertThat(viewModel.uiState.value.showSearch).isTrue()

        // Cleared but not yet reloaded: the empty result still answers "zz", so the field stays.
        viewModel.onSearchQueryChanged("")
        assertThat(viewModel.uiState.value.isSearching).isTrue()
        assertThat(viewModel.uiState.value.showSearch).isTrue()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.shownQuery).isEmpty()
        assertThat(viewModel.uiState.value.isSearching).isFalse()
    }

    // --- category presets for the card labels ---

    @Test
    fun `categories load once across reloads`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        coEvery { configRepository.getCategories() } returns
            Result.Success(listOf(Category(label = "com_ui_idea", value = "idea")))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.categories.map { it.value }).containsExactly("idea")
        coVerify(exactly = 1) { configRepository.getCategories() }
    }

    @Test
    fun `a failed categories fetch retries with the next reload`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        coEvery { configRepository.getCategories() } returns Result.Error(message = "boom")
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.categories).isEmpty()

        coEvery { configRepository.getCategories() } returns
            Result.Success(listOf(Category(label = "com_ui_idea", value = "idea")))
        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.categories.map { it.value }).containsExactly("idea")
    }

    private fun TestScope.collectEvents(viewModel: SkillsListViewModel): List<SkillsListEvent> {
        val events = mutableListOf<SkillsListEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return events
    }

    // --- no empty-state flash before the first load ---

    @Test
    fun `the first frame is loading, not empty`() = runTest(testDispatcher) {
        assertThat(vm().uiState.value.isLoading).isTrue()
    }

    // --- a failed page stops auto-paging until a retry ---

    @Test
    fun `a failed page is not re-requested until retried`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns Result.Error(message = "boom")
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadMoreFailed).isTrue()
        assertThat(viewModel.uiState.value.isLoadingMore).isFalse()

        // The scroll trigger re-fires once isLoadingMore drops; nothing goes out.
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 1) { skillsRepository.listSkills(search = null, cursor = "cur1") }

        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns
            Result.Success(SkillListResponse(skills = listOf(summary("b")), hasMore = false))
        viewModel.retryLoadMore()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(viewModel.uiState.value.loadMoreFailed).isFalse()
    }

    @Test
    fun `a refresh clears a failed page`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns Result.Error(message = "boom")
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadMoreFailed).isFalse()
    }

    // --- a replace-load drops a page that was in flight ---

    @Test
    fun `a page in flight when the search changes is dropped`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        val stalePage = CompletableDeferred<Result<SkillListResponse>>()
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } coAnswers { stalePage.await() }
        coEvery { skillsRepository.listSkills(search = "pdf", cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("p")), hasMore = false))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("pdf")
        advanceUntilIdle()
        // The old query's page lands after the replace; it must not be appended.
        stalePage.complete(Result.Success(SkillListResponse(skills = listOf(summary("b")), hasMore = false)))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("p")
        assertThat(viewModel.uiState.value.isLoadingMore).isFalse()
    }

    @Test
    fun `loadMore waits while a replace-load is in flight`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        val refresh = CompletableDeferred<Result<SkillListResponse>>()
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } coAnswers { refresh.await() }
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 0) { skillsRepository.listSkills(search = null, cursor = "cur1") }
        refresh.complete(Result.Success(SkillListResponse(skills = listOf(summary("a")))))
        advanceUntilIdle()
    }

    // --- a return reloads quietly and keeps the loaded count ---

    @Test
    fun `a return reloads quietly and keeps every loaded page`() = runTest(testDispatcher) {
        val page1 = (1..100).map { summary("a$it") }
        val page2 = (1..40).map { summary("b$it") }
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = page1, hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns
            Result.Success(SkillListResponse(skills = page2, hasMore = true, after = "cur2"))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.skills).hasSize(140)

        viewModel.refreshOnReturn()
        // Quiet: no spinner of either kind while it runs.
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.skills).hasSize(140)
        assertThat(state.cursor).isEqualTo("cur2")
        assertThat(state.hasMore).isTrue()
        coVerify(exactly = 2) { skillsRepository.listSkills(search = null, cursor = "cur1") }
        coVerify(exactly = 0) { skillsRepository.listSkills(search = null, cursor = "cur2") }
    }

    @Test
    fun `a return after a failed first load is a visible load, not an empty-state flash`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns Result.Error(message = "boom")
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.error).isEqualTo(SkillError(SkillErrorKind.LOAD_SKILLS, "boom"))

        val retry = CompletableDeferred<Result<SkillListResponse>>()
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } coAnswers { retry.await() }
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        retry.complete(Result.Success(SkillListResponse(skills = listOf(summary("a")))))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a")
    }

    @Test
    fun `a quiet reload stops when the server repeats a page`() = runTest(testDispatcher) {
        val page1 = (1..100).map { summary("a$it") }
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = page1, hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns
            Result.Success(SkillListResponse(skills = listOf(summary("b1")), hasMore = true, after = "cur2"))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        // On return the server hands back page one again under the next cursor.
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns
            Result.Success(SkillListResponse(skills = page1, hasMore = true, after = "cur1"))
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.skills).hasSize(100)
        // The stall is the end of the list: the scroll trigger must not ask for the same page again.
        assertThat(state.hasMore).isFalse()
        assertThat(state.cursor).isNull()
        viewModel.loadMore()
        advanceUntilIdle()
        // Once by the first loadMore, once by the reload that stalled on it, and no more.
        coVerify(exactly = 2) { skillsRepository.listSkills(search = null, cursor = "cur1") }
    }

    @Test
    fun `a next page that adds no new skill ends paging`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasMore).isFalse()
        assertThat(viewModel.uiState.value.cursor).isNull()
        viewModel.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 1) { skillsRepository.listSkills(search = null, cursor = "cur1") }
    }

    @Test
    fun `a loadMore during a quiet reload asks for the next page once the reload lands`() = runTest(testDispatcher) {
        val page1 = (1..100).map { summary("a$it") }
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = page1, hasMore = true, after = "cur1"))
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } returns
            Result.Success(SkillListResponse(skills = listOf(summary("b1")), hasMore = false, after = null))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        val reload = CompletableDeferred<Result<SkillListResponse>>()
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } coAnswers { reload.await() }
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        // The user reaches the end while the quiet reload runs; the screen's trigger fires once.
        viewModel.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 0) { skillsRepository.listSkills(search = null, cursor = "cur1") }

        reload.complete(Result.Success(SkillListResponse(skills = page1, hasMore = true, after = "cur1")))
        advanceUntilIdle()

        coVerify(exactly = 1) { skillsRepository.listSkills(search = null, cursor = "cur1") }
        assertThat(viewModel.uiState.value.skills).hasSize(101)
        assertThat(viewModel.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `a loadMore during a reload that fails is not replayed`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = true, after = "cur1"))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        val reload = CompletableDeferred<Result<SkillListResponse>>()
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } coAnswers { reload.await() }
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        viewModel.loadMore()
        reload.complete(Result.Error(message = "boom"))
        advanceUntilIdle()

        coVerify(exactly = 0) { skillsRepository.listSkills(search = null, cursor = "cur1") }
    }

    // --- save warnings from the editor ---

    @Test
    fun `a create's warnings show on the list it returns to, once`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(any(), any(), any(), any()) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a")), hasMore = false))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        // Posted while the editor was on top, before the list's collector restarts.
        saveNotices.post("sk-new", listOf("Short descriptions may cause the agent to miss triggering opportunities."))
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        val events = mutableListOf<SkillsListEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        advanceUntilIdle()
        assertThat(events).containsExactly(
            SkillsListEvent.SaveWarnings(listOf("Short descriptions may cause the agent to miss triggering opportunities.")),
        )

        viewModel.refreshOnReturn()
        advanceUntilIdle()
        assertThat(events).hasSize(1)
    }

    // --- a page continues the query on screen, not one still in its debounce ---

    @Test
    fun `a page asked for during the search debounce continues the shown query`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = "git", cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("g1")), hasMore = true, after = "git-cur"))
        coEvery { skillsRepository.listSkills(search = "git", cursor = "git-cur") } returns
            Result.Success(SkillListResponse(skills = listOf(summary("g2")), hasMore = false))
        val viewModel = vm()
        viewModel.onSearchQueryChanged("git")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo("git")

        viewModel.onSearchQueryChanged("docker")
        testScheduler.runCurrent()
        viewModel.loadMore()
        testScheduler.runCurrent()

        coVerify(exactly = 1) { skillsRepository.listSkills(search = "git", cursor = "git-cur") }
        coVerify(exactly = 0) { skillsRepository.listSkills(search = "docker", cursor = "git-cur") }
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("g1", "g2").inOrder()
    }
}

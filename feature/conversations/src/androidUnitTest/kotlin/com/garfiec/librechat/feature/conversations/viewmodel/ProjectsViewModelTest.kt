package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AllProjects
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.response.ProjectListResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val projectRepository = mockk<ProjectRepository>(relaxed = true)

    private fun page(vararg names: String, next: String? = null) = Result.Success(
        ProjectListResponse(projects = names.map { ChatProject(id = "id-$it", name = it) }, nextCursor = next),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepository.revision } returns MutableStateFlow(0L)
        every { projectRepository.orderRevision } returns MutableStateFlow(0L)
        coEvery { projectRepository.listProjects(any(), any(), any(), any()) } returns page("Alpha", "Beta")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `first load asks for latest activity, newest first, with no search`() = runTest(testDispatcher) {
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            projectRepository.listProjects(null, "lastConversationAt", "desc", null)
        }
        assertThat(viewModel.uiState.value.projects.map { it.name }).containsExactly("Alpha", "Beta").inOrder()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `custom order shows the drawer's order, every project at once`() = runTest(testDispatcher) {
        coEvery { projectRepository.listAllProjectsInCustomOrder(null) } returns Result.Success(
            AllProjects(listOf(ChatProject(id = "id-Beta", name = "Beta"), ChatProject(id = "id-Alpha", name = "Alpha")), true),
        )
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.setSort(ProjectSort.CUSTOM)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.projects.map { it.name }).containsExactly("Beta", "Alpha").inOrder()
        assertThat(viewModel.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `a new drawer order reloads only the custom sort`() = runTest(testDispatcher) {
        val orderRevision = MutableStateFlow(0L)
        every { projectRepository.orderRevision } returns orderRevision
        coEvery { projectRepository.listAllProjectsInCustomOrder(null) } returns
            Result.Success(AllProjects(listOf(ChatProject(id = "id-Alpha", name = "Alpha")), true))
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        orderRevision.value = 1
        advanceUntilIdle()
        coVerify(exactly = 0) { projectRepository.listAllProjectsInCustomOrder(any()) }

        viewModel.setSort(ProjectSort.CUSTOM)
        advanceUntilIdle()
        orderRevision.value = 2
        advanceUntilIdle()
        coVerify(exactly = 2) { projectRepository.listAllProjectsInCustomOrder(null) }
    }

    @Test
    fun `changing the sort reloads with the new order`() = runTest(testDispatcher) {
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.setSort(ProjectSort.NAME)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.sort).isEqualTo(ProjectSort.NAME)
        coVerify(exactly = 1) { projectRepository.listProjects(null, "name", "asc", null) }
    }

    @Test
    fun `typing collapses into one trimmed search after the debounce`() = runTest(testDispatcher) {
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("th")
        advanceTimeBy(200)
        viewModel.onSearchQueryChange("thesis ")
        advanceUntilIdle()

        coVerify(exactly = 0) { projectRepository.listProjects(any(), any(), any(), "th") }
        coVerify(exactly = 1) { projectRepository.listProjects(null, "lastConversationAt", "desc", "thesis") }
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("thesis ")
    }

    @Test
    fun `clearing the search sends no search parameter`() = runTest(testDispatcher) {
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("x")
        advanceUntilIdle()
        viewModel.onSearchQueryChange("   ")
        advanceUntilIdle()

        coVerify(exactly = 2) { projectRepository.listProjects(null, "lastConversationAt", "desc", null) }
    }

    @Test
    fun `the shown search trails the typed one until its results land`() = runTest(testDispatcher) {
        val unfiltered = CompletableDeferred<Result<ProjectListResponse>>()
        coEvery { projectRepository.listProjects(null, any(), any(), "zzz") } returns page()
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()
        viewModel.onSearchQueryChange("zzz")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo("zzz")
        coEvery { projectRepository.listProjects(null, any(), any(), null) } coAnswers { unfiltered.await() }

        viewModel.onSearchQueryChange("")
        advanceTimeBy(600)
        runCurrent()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo("zzz")
        unfiltered.complete(page("Alpha", "Beta"))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.shownQuery).isEmpty()
        assertThat(viewModel.uiState.value.projects.map { it.name }).containsExactly("Alpha", "Beta").inOrder()
    }

    @Test
    fun `a sort during the search debounce asks only once`() = runTest(testDispatcher) {
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.onSearchQueryChange("thesis")
        advanceTimeBy(200)
        viewModel.setSort(ProjectSort.NAME)
        advanceUntilIdle()

        coVerify(exactly = 1) { projectRepository.listProjects(null, "name", "asc", "thesis") }
    }

    @Test
    fun `a page answering an older query is dropped`() = runTest(testDispatcher) {
        val slowAppend = CompletableDeferred<Result<ProjectListResponse>>()
        coEvery { projectRepository.listProjects(null, any(), any(), null) } returns page("Alpha", next = "c1")
        coEvery { projectRepository.listProjects("c1", any(), any(), null) } coAnswers { slowAppend.await() }
        coEvery { projectRepository.listProjects(null, any(), any(), "zeta") } returns page("Zeta")

        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        // The append is in flight when the user searches; it must not land on the search results.
        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.onSearchQueryChange("zeta")
        advanceUntilIdle()
        slowAppend.complete(page("Stale"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.projects.map { it.name }).containsExactly("Zeta")
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `a project change made elsewhere reloads the list`() = runTest(testDispatcher) {
        val revision = MutableStateFlow(0L)
        every { projectRepository.revision } returns revision
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()
        coEvery { projectRepository.listProjects(any(), any(), any(), any()) } returns page("Alpha", "Beta", "Gamma")

        revision.value = 1
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.projects.map { it.name }).containsExactly("Alpha", "Beta", "Gamma").inOrder()
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun `creating a project opens it, without a blank description`() = runTest(testDispatcher) {
        coEvery { projectRepository.createProject("Thesis", null) } returns
            Result.Success(ChatProject(id = "id-new", name = "Thesis"))
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()
        val events = mutableListOf<ProjectsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.createProject("Thesis", "  ")
        advanceUntilIdle()

        coVerify(exactly = 1) { projectRepository.createProject("Thesis", null) }
        assertThat(events).containsExactly(ProjectsEvent.OpenProject("id-new", "Thesis"))
    }

    @Test
    fun `editing sends an emptied description so the server clears it`() = runTest(testDispatcher) {
        coEvery { projectRepository.updateProject(any(), any(), any()) } returns
            Result.Success(ChatProject(id = "id-1", name = "Thesis"))
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.editProject("id-1", "Thesis", "")
        advanceUntilIdle()

        coVerify(exactly = 1) { projectRepository.updateProject("id-1", name = "Thesis", description = "") }
    }

    @Test
    fun `setting the current sort again does not reload`() = runTest(testDispatcher) {
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.setSort(ProjectSort.ACTIVITY)
        advanceUntilIdle()

        coVerify(exactly = 1) { projectRepository.listProjects(any(), any(), any(), any()) }
    }

    @Test
    fun `returning to the index reloads it without a spinner`() = runTest(testDispatcher) {
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.refreshOnReturn()
        runCurrent()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
        advanceUntilIdle()

        coVerify(exactly = 2) { projectRepository.listProjects(null, "lastConversationAt", "desc", null) }
    }

    @Test
    fun `returning while the first load runs asks only once`() = runTest(testDispatcher) {
        val viewModel = ProjectsViewModel(projectRepository)

        viewModel.refreshOnReturn()
        advanceUntilIdle()

        coVerify(exactly = 1) { projectRepository.listProjects(any(), any(), any(), any()) }
    }

    @Test
    fun `a next page asked for during a reload runs from the reloaded list`() = runTest(testDispatcher) {
        coEvery { projectRepository.listProjects(null, any(), any(), null) } returns page("Alpha", next = "c1")
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()
        val reload = CompletableDeferred<Result<ProjectListResponse>>()
        coEvery { projectRepository.listProjects(null, any(), any(), null) } coAnswers { reload.await() }

        viewModel.refreshOnReturn()
        runCurrent()
        viewModel.loadMore()
        reload.complete(page("Alpha", next = "c2"))
        advanceUntilIdle()

        coVerify(exactly = 0) { projectRepository.listProjects("c1", any(), any(), any()) }
        coVerify(exactly = 1) { projectRepository.listProjects("c2", any(), any(), any()) }
    }

    @Test
    fun `a reload keeps as many projects as were loaded`() = runTest(testDispatcher) {
        coEvery { projectRepository.listProjects(null, any(), any(), null, null) } returns
            page(*Array(30) { "P$it" })
        val viewModel = ProjectsViewModel(projectRepository)
        advanceUntilIdle()

        viewModel.refreshOnReturn()
        advanceUntilIdle()

        coVerify(exactly = 1) { projectRepository.listProjects(null, "lastConversationAt", "desc", null, 30) }
    }
}

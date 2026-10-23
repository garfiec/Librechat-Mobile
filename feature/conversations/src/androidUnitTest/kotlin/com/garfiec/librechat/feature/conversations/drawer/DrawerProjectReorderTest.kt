package com.garfiec.librechat.feature.conversations.drawer

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.AllProjects
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerProjectReorderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val projectRepository = mockk<ProjectRepository>(relaxed = true)
    private val account = AccountId("srv:user")
    private val accountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(account))

    private fun project(id: String) = ChatProject(id = id, name = id)

    private fun all(vararg ids: String, complete: Boolean = true) =
        Result.Success(AllProjects(ids.map(::project), isComplete = complete))

    private fun viewModel() = DrawerViewModel(
        conversationRepository = mockk(relaxed = true),
        roleRepository = mockk(relaxed = true),
        tagRepository = mockk(relaxed = true),
        projectRepository = projectRepository,
        configRepository = mockk<ConfigRepository>(relaxed = true) {
            every { detectedBackendVersion } returns MutableStateFlow(null)
        },
        shareLinkControllerFactory = mockk(relaxed = true),
        conversationExporter = mockk(relaxed = true),
        activeAccountProvider = accountProvider,
        settingsDataStore = mockk<SettingsDataStore>(relaxed = true) {
            every { drawerLibraryTab } returns flowOf(null)
        },
        chatRepository = mockk(relaxed = true),
        search = mockk(relaxed = true),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepository.revision } returns MutableStateFlow(0L)
        coEvery { projectRepository.listAllProjectsInCustomOrder(any()) } returns all("a", "b", "c")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // The drawer runs polling loops in viewModelScope, so the scheduler is never idle: the scope is
    // cancelled at the end, or runTest waits on it for ever.
    private fun drawerTest(body: suspend TestScope.(DrawerViewModel) -> Unit) = runTest(testDispatcher) {
        val vm = viewModel()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a drop shows at once and is saved`() = drawerTest { vm ->
        vm.loadProjects()
        runCurrent()

        vm.reorderProjects(listOf("c", "a", "b"))

        assertThat(vm.projects.value.map { it.id }).containsExactly("c", "a", "b").inOrder()
        runCurrent()
        coVerify { projectRepository.saveProjectOrder(account, listOf("c", "a", "b")) }
    }

    @Test
    fun `a project the drag never saw keeps its place at the end`() = drawerTest { vm ->
        vm.loadProjects()
        runCurrent()

        vm.reorderProjects(listOf("b", "a"))
        runCurrent()

        assertThat(vm.projects.value.map { it.id }).containsExactly("b", "a", "c").inOrder()
        coVerify { projectRepository.saveProjectOrder(account, listOf("b", "a", "c")) }
    }

    @Test
    fun `a load in flight during a drop is rerun after the save, not landed over it`() = drawerTest { vm ->
        vm.loadProjects()
        runCurrent()
        val stale = CompletableDeferred<Result<AllProjects>>()
        coEvery { projectRepository.listAllProjectsInCustomOrder(any()) } coAnswers { stale.await() }
        vm.loadProjects()
        runCurrent()

        vm.reorderProjects(listOf("c", "b", "a"))
        coEvery { projectRepository.listAllProjectsInCustomOrder(any()) } returns all("c", "b", "a")
        stale.complete(all("a", "b", "c"))
        runCurrent()

        assertThat(vm.projects.value.map { it.id }).containsExactly("c", "b", "a").inOrder()
    }

    @Test
    fun `a save queued behind another goes to the account the drop was made on`() = drawerTest { vm ->
        vm.loadProjects()
        runCurrent()
        val firstSave = CompletableDeferred<Unit>()
        coEvery { projectRepository.saveProjectOrder(account, listOf("b", "a", "c")) } coAnswers { firstSave.await() }
        vm.reorderProjects(listOf("b", "a", "c"))
        runCurrent()

        vm.reorderProjects(listOf("c", "b", "a"))
        accountProvider.set(AccountId("srv:other"))
        firstSave.complete(Unit)
        runCurrent()

        coVerify(exactly = 0) { projectRepository.saveProjectOrder(AccountId("srv:other"), any()) }
    }
}

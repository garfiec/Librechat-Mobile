package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ProjectOrderDao
import com.garfiec.librechat.core.data.db.entity.ProjectOrderEntity
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.response.DeleteChatProjectResponse
import com.garfiec.librechat.core.model.response.ProjectListResponse
import com.garfiec.librechat.core.network.api.ProjectsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProjectCustomOrderTest {

    private val account = "srv:user"
    private val projectsApi = mockk<ProjectsApi>()
    private val orderDao = mockk<ProjectOrderDao>(relaxed = true)
    private val accountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId(account)))
    private val repository = ProjectRepositoryImpl(
        projectsApi = projectsApi,
        conversationDao = mockk<ConversationDao>(relaxed = true),
        projectOrderDao = orderDao,
        activeAccountProvider = accountProvider,
        projectChanges = ProjectChangeSignal(),
    )

    private fun project(id: String) = ChatProject(id = id, name = id)

    private fun saved(vararg ids: String) = ids.mapIndexed { i, id -> ProjectOrderEntity(account, id, i.toLong()) }

    @Test
    fun followsTheCursorUntilTheLastPage() = runTest {
        coEvery { projectsApi.listProjects(null, 100, null, null, null) } returns
            ProjectListResponse(listOf(project("a"), project("b")), nextCursor = "c1")
        coEvery { projectsApi.listProjects("c1", 100, null, null, null) } returns
            ProjectListResponse(listOf(project("c")), nextCursor = null)
        coEvery { orderDao.positionsForAccount(account) } returns emptyList()

        val result = repository.listAllProjectsInCustomOrder()

        assertThat((result as Result.Success).data.projects.map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun theFirstLoadIsSavedAsTheOrder() = runTest {
        coEvery { projectsApi.listProjects(any(), any(), any(), any(), any()) } returns
            ProjectListResponse(listOf(project("b"), project("a")))
        coEvery { orderDao.positionsForAccount(account) } returns emptyList()

        repository.listAllProjectsInCustomOrder()

        coVerify { orderDao.replaceForAccount(account, listOf("b", "a")) }
    }

    @Test
    fun anUnchangedOrderIsNotRewritten() = runTest {
        coEvery { projectsApi.listProjects(any(), any(), any(), any(), any()) } returns
            ProjectListResponse(listOf(project("a"), project("b")))
        coEvery { orderDao.positionsForAccount(account) } returns saved("b", "a")

        val result = repository.listAllProjectsInCustomOrder()

        assertThat((result as Result.Success).data.projects.map { it.id }).containsExactly("b", "a").inOrder()
        coVerify(exactly = 0) { orderDao.replaceForAccount(any(), any()) }
    }

    @Test
    fun aSearchNeverSaves() = runTest {
        coEvery { projectsApi.listProjects(any(), any(), any(), any(), "a") } returns
            ProjectListResponse(listOf(project("a")))
        coEvery { orderDao.positionsForAccount(account) } returns saved("b", "a")

        repository.listAllProjectsInCustomOrder(search = "a")

        coVerify(exactly = 0) { orderDao.replaceForAccount(any(), any()) }
    }

    @Test
    fun withNoAccountTheListIsReturnedAndNothingIsSaved() = runTest {
        accountProvider.clear()
        coEvery { projectsApi.listProjects(any(), any(), any(), any(), any()) } returns
            ProjectListResponse(listOf(project("a")))

        val result = repository.listAllProjectsInCustomOrder()

        assertThat((result as Result.Success).data.projects.map { it.id }).containsExactly("a")
        coVerify(exactly = 0) { orderDao.replaceForAccount(any(), any()) }
    }

    @Test
    fun aListCutShortByThePageCapIsIncompleteAndNeverSaves() = runTest {
        coEvery { projectsApi.listProjects(any(), any(), any(), any(), any()) } answers {
            ProjectListResponse(listOf(project("p-${firstArg<String?>()}")), nextCursor = "more")
        }
        coEvery { orderDao.positionsForAccount(account) } returns saved("kept")

        val result = repository.listAllProjectsInCustomOrder()

        assertThat((result as Result.Success).data.isComplete).isFalse()
        coVerify(exactly = 0) { orderDao.replaceForAccount(any(), any()) }
    }

    @Test
    fun aSaveGoesToTheAccountItWasMadeForAndSignalsTheOrder() = runTest {
        accountProvider.clear()
        val start = repository.orderRevision.value
        val serverRevision = repository.revision.value

        repository.saveProjectOrder(AccountId("srv:other"), listOf("b", "a"))

        coVerify { orderDao.replaceForAccount("srv:other", listOf("b", "a")) }
        assertThat(repository.orderRevision.value).isEqualTo(start + 1)
        assertThat(repository.revision.value).isEqualTo(serverRevision)
    }

    @Test
    fun aDeletedProjectLeavesTheOrder() = runTest {
        coEvery { projectsApi.deleteProject("a") } returns DeleteChatProjectResponse()

        repository.deleteProject("a")

        coVerify { orderDao.delete(account, "a") }
    }
}

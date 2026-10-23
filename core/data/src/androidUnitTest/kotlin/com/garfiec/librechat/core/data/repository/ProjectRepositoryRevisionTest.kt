package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ProjectOrderDao
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.response.DeleteChatProjectResponse
import com.garfiec.librechat.core.model.response.ProjectListResponse
import com.garfiec.librechat.core.network.api.ProjectsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `revision` is how the drawer learns about a project made on the Projects index (and the other way
 * round): each holds its own list. A missed bump leaves the other surface showing the old folders.
 */
class ProjectRepositoryRevisionTest {

    private val projectsApi = mockk<ProjectsApi>(relaxed = true)
    private val repository = ProjectRepositoryImpl(
        projectsApi = projectsApi,
        conversationDao = mockk<ConversationDao>(relaxed = true),
        projectOrderDao = mockk<ProjectOrderDao>(relaxed = true),
        activeAccountProvider = mockk<ActiveAccountProvider>(relaxed = true),
        projectChanges = ProjectChangeSignal(),
    )

    private val project = ChatProject(id = "p-1", name = "Thesis")

    @Test
    fun everyAcceptedMutationBumpsTheRevision() = runTest {
        coEvery { projectsApi.createProject(any(), any()) } returns project
        coEvery { projectsApi.updateProject(any(), any(), any()) } returns project
        coEvery { projectsApi.deleteProject(any()) } returns DeleteChatProjectResponse()

        val start = repository.revision.value
        repository.createProject("Thesis")
        repository.updateProject("p-1", name = "Thesis 2")
        repository.assignConversation("c-1", "p-1")
        repository.deleteProject("p-1")

        assertThat(repository.revision.value).isEqualTo(start + 4)
    }

    @Test
    fun aRejectedMutationDoesNotBump() = runTest {
        coEvery { projectsApi.createProject(any(), any()) } throws IllegalStateException("boom")

        val start = repository.revision.value
        repository.createProject("Thesis")

        assertThat(repository.revision.value).isEqualTo(start)
    }

    @Test
    fun listingDoesNotBump() = runTest {
        coEvery { projectsApi.listProjects(any(), any(), any(), any(), any()) } returns ProjectListResponse()

        val start = repository.revision.value
        repository.listProjects()

        assertThat(repository.revision.value).isEqualTo(start)
    }
}

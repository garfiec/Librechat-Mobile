package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.response.ProjectListResponse
import com.garfiec.librechat.core.network.api.ProjectsApi
import kotlinx.coroutines.flow.StateFlow

class ProjectRepositoryImpl(
    private val projectsApi: ProjectsApi,
    private val conversationDao: ConversationDao,
    private val activeAccountProvider: ActiveAccountProvider,
    private val projectChanges: ProjectChangeSignal,
) : ProjectRepository {

    override val revision: StateFlow<Long> = projectChanges.revision

    private fun <T> Result<T>.bumpOnSuccess(): Result<T> = also { if (it is Result.Success) projectChanges.bump() }

    override suspend fun listProjects(
        cursor: String?,
        sortBy: String?,
        sortDirection: String?,
        search: String?,
        limit: Int?,
    ): Result<ProjectListResponse> =
        safeApiCall {
            projectsApi.listProjects(
                cursor = cursor,
                limit = limit,
                sortBy = sortBy,
                sortDirection = sortDirection,
                search = search,
            )
        }

    override suspend fun getProject(projectId: String): Result<ChatProject> =
        safeApiCall { projectsApi.getProject(projectId) }

    override suspend fun createProject(name: String, description: String?): Result<ChatProject> =
        safeApiCall { projectsApi.createProject(name, description) }.bumpOnSuccess()

    override suspend fun updateProject(
        projectId: String,
        name: String?,
        description: String?,
    ): Result<ChatProject> =
        safeApiCall { projectsApi.updateProject(projectId, name, description) }.bumpOnSuccess()

    override suspend fun deleteProject(projectId: String): Result<Unit> =
        safeApiCall<Unit> { projectsApi.deleteProject(projectId) }.bumpOnSuccess()

    override suspend fun assignConversation(
        conversationId: String,
        projectId: String?,
    ): Result<Unit> {
        // The assign endpoint is API-only, but the cached row carries chatProjectId (read by the
        // drawer move-picker to pre-select the current folder). Mirror the new assignment into Room
        // on success so the picker self-corrects immediately instead of lagging until the next full
        // sync. projectId == null is an unassign and clears the column. Capture identity before the
        // network suspend; skip the account-scoped local write when unresolved.
        val accountId = activeAccountProvider.currentAccountId()?.value
        return safeApiCall<Unit> { projectsApi.assignConversation(conversationId, projectId) }
            .also { result ->
                if (result is Result.Success && accountId != null) {
                    conversationDao.updateChatProjectId(conversationId, projectId, accountId)
                }
            }
            .bumpOnSuccess()
    }
}

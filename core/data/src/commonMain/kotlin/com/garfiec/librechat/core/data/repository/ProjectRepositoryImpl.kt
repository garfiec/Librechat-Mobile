package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ProjectOrderDao
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.response.ProjectListResponse
import com.garfiec.librechat.core.network.api.ProjectsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProjectRepositoryImpl(
    private val projectsApi: ProjectsApi,
    private val conversationDao: ConversationDao,
    private val projectOrderDao: ProjectOrderDao,
    private val activeAccountProvider: ActiveAccountProvider,
    private val projectChanges: ProjectChangeSignal,
) : ProjectRepository {

    private companion object {
        /** The server's per-page cap. */
        const val PAGE_LIMIT = 100

        /** 5,000 projects; a guard against a server that keeps handing back a cursor. */
        const val MAX_PAGES = 50
    }

    override val revision: StateFlow<Long> = projectChanges.revision

    private val _orderRevision = MutableStateFlow(0L)
    override val orderRevision: StateFlow<Long> = _orderRevision.asStateFlow()
    private val orderLock = Mutex()

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

    override suspend fun listAllProjectsInCustomOrder(search: String?): Result<AllProjects> {
        // Captured before the first suspend: an account switch mid-fetch must not file this
        // account's order under the next one.
        val accountId = activeAccountProvider.currentAccountId()?.value
        val fetched = safeApiCall {
            val all = mutableListOf<ChatProject>()
            var cursor: String? = null
            var pages = 0
            do {
                val page = projectsApi.listProjects(cursor = cursor, limit = PAGE_LIMIT, search = search)
                all += page.projects
                cursor = page.nextCursor
            } while (cursor != null && ++pages < MAX_PAGES)
            AllProjects(all.distinctBy { it.id }, isComplete = cursor == null)
        }
        if (fetched !is Result.Success || accountId == null) return fetched

        // Read, arrange and write under one lock: a drop saved between this read and this write
        // would otherwise be overwritten by the order it replaced.
        val arranged = orderLock.withLock {
            val saved = projectOrderDao.positionsForAccount(accountId)
            val arranged = ProjectOrdering.arrange(fetched.data.projects, saved.associate { it.projectId to it.position })
            val arrangedIds = arranged.map { it.id }
            // Only a whole, unfiltered list may save: it is what drops the projects it no longer has.
            if (search == null && fetched.data.isComplete && arrangedIds != saved.map { it.projectId }) {
                projectOrderDao.replaceForAccount(accountId, arrangedIds)
            }
            arranged
        }
        return Result.Success(fetched.data.copy(projects = arranged))
    }

    override suspend fun saveProjectOrder(accountId: AccountId, projectIds: List<String>) {
        orderLock.withLock { projectOrderDao.replaceForAccount(accountId.value, projectIds) }
        _orderRevision.update { it + 1 }
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

    override suspend fun deleteProject(projectId: String): Result<Unit> {
        val accountId = activeAccountProvider.currentAccountId()?.value
        return safeApiCall<Unit> { projectsApi.deleteProject(projectId) }
            .also { result ->
                if (result is Result.Success && accountId != null) projectOrderDao.delete(accountId, projectId)
            }
            .bumpOnSuccess()
    }

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

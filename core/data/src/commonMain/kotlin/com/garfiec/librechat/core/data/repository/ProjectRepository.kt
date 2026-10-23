package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.response.ProjectListResponse
import kotlinx.coroutines.flow.StateFlow

/**
 * Chat Projects (folders) repository (v0.8.7). Server is the sole source of
 * truth — no local caching (mirrors [McpRepository]/[MemoryRepository]).
 */
interface ProjectRepository {
    /**
     * Bumped after every successful create, update, delete or conversation assignment made through
     * this repository, and when a new chat filed into a project has its first reply saved (see
     * [ProjectChangeSignal]). The drawer, the Projects index and the project page each hold their own
     * copy of the list and reload on a change, so a change made in one shows up in the others.
     */
    val revision: StateFlow<Long>

    /** [sortBy]/[sortDirection]/[search] are passed through as-is; null leaves the server default. */
    suspend fun listProjects(
        cursor: String? = null,
        sortBy: String? = null,
        sortDirection: String? = null,
        search: String? = null,
        /** Page size; null leaves it to the server (25). The server caps it at 100. */
        limit: Int? = null,
    ): Result<ProjectListResponse>

    /**
     * **Switchboard-only.** Every project, in the user's saved drawer order (see [ProjectOrdering]),
     * fetched page by page until the server has no more.
     *
     * Without [search], the arranged order is saved back when it differs from what was stored: that
     * freezes the first load, gives a project made elsewhere its slot at the end, and drops one deleted
     * elsewhere. A search answers with only some projects, so it never saves; neither does a list cut
     * short by the page cap ([AllProjects.isComplete]).
     */
    suspend fun listAllProjectsInCustomOrder(search: String? = null): Result<AllProjects>

    /**
     * **Switchboard-only.** Bumped when the user saves a new drawer order, so a list showing that
     * order elsewhere (the Projects index's Custom sort) can reload. Separate from [revision]: nothing
     * changed on the server, and the views sorted by the server have nothing to refetch.
     */
    val orderRevision: StateFlow<Long>

    /**
     * **Switchboard-only.** Saves [projectIds] as [accountId]'s drawer order. The account is the
     * caller's, captured when the user dropped the folder: by the time this runs another one may be
     * active.
     */
    suspend fun saveProjectOrder(accountId: AccountId, projectIds: List<String>)

    suspend fun getProject(projectId: String): Result<ChatProject>
    suspend fun createProject(name: String, description: String? = null): Result<ChatProject>
    suspend fun updateProject(
        projectId: String,
        name: String? = null,
        description: String? = null,
    ): Result<ChatProject>
    suspend fun deleteProject(projectId: String): Result<Unit>

    /** Assigns [conversationId] to [projectId], or unassigns it when [projectId] is null. */
    suspend fun assignConversation(conversationId: String, projectId: String?): Result<Unit>
}

/**
 * **Switchboard-only.** Every project, in custom order. [isComplete] is false when the fetch stopped at
 * its page cap with the server still handing back a cursor: a project missing from [projects] may then
 * just lie past the cut-off, not be deleted.
 */
data class AllProjects(val projects: List<ChatProject>, val isComplete: Boolean)

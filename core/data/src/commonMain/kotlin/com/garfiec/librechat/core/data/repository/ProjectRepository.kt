package com.garfiec.librechat.core.data.repository

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

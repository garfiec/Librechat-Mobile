package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.request.AddChatProjectFileRequest
import com.garfiec.librechat.core.model.request.AssignConversationToProjectRequest
import com.garfiec.librechat.core.model.request.CreateChatProjectRequest
import com.garfiec.librechat.core.model.request.UpdateChatProjectInstructionsRequest
import com.garfiec.librechat.core.model.request.UpdateChatProjectRequest
import com.garfiec.librechat.core.model.response.AssignConversationToProjectResponse
import com.garfiec.librechat.core.model.response.AvailableProjectFilesResponse
import com.garfiec.librechat.core.model.response.ChatProjectFile
import com.garfiec.librechat.core.model.response.DeleteChatProjectResponse
import com.garfiec.librechat.core.model.response.ProjectListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path

/**
 * Chat Projects (folders) API (v0.8.7). Unlike the conversation mutation
 * endpoints, the project bodies are NOT `arg`-wrapped — the routes read
 * `req.body` fields directly.
 */
class ProjectsApi(
    private val client: HttpClient,
) {
    suspend fun listProjects(
        cursor: String? = null,
        /** Null leaves it to the server: 25, capped at 100. */
        limit: Int? = null,
        sortBy: String? = null,
        sortDirection: String? = null,
        search: String? = null,
    ): ProjectListResponse =
        client.get {
            url { path("api/projects") }
            parameter("cursor", cursor)
            limit?.let { parameter("limit", it) }
            sortBy?.let { parameter("sortBy", it) }
            sortDirection?.let { parameter("sortDirection", it) }
            search?.let { parameter("search", it) }
        }.body()

    suspend fun getProject(projectId: String): ChatProject =
        client.get {
            url { path("api/projects/$projectId") }
        }.body()

    suspend fun createProject(name: String, description: String? = null): ChatProject =
        client.post {
            url { path("api/projects") }
            setBody(CreateChatProjectRequest(name = name, description = description))
        }.body()

    suspend fun updateProject(
        projectId: String,
        name: String? = null,
        description: String? = null,
    ): ChatProject =
        client.patch {
            url { path("api/projects/$projectId") }
            setBody(UpdateChatProjectRequest(name = name, description = description))
        }.body()

    /**
     * Saves the project's instructions (v0.8.9-rc1). [contextRevision] is the revision the draft was
     * based on; a stale one answers 409 `{ message, code: "CHAT_PROJECT_CONFLICT" }`.
     */
    suspend fun updateInstructions(
        projectId: String,
        instructions: String,
        contextRevision: Int?,
    ): ChatProject =
        client.patch {
            url { path("api/projects/$projectId") }
            setBody(UpdateChatProjectInstructionsRequest(instructions = instructions, contextRevision = contextRevision))
        }.body()

    /** The project's reference files, in order (v0.8.9-rc1). Not paged. */
    suspend fun listProjectFiles(projectId: String): List<ChatProjectFile> =
        client.get {
            url { path("api/projects/$projectId/files") }
        }.body()

    /**
     * The user's files that could be added (v0.8.9-rc1). [cursor] is left off entirely when null:
     * the server answers an empty `cursor=` with 400. [limit] is capped at 50 by the server.
     */
    suspend fun listAvailableProjectFiles(
        projectId: String,
        cursor: String? = null,
        limit: Int? = null,
        search: String? = null,
    ): AvailableProjectFilesResponse =
        client.get {
            url { path("api/projects/$projectId/files/available") }
            cursor?.takeIf { it.isNotEmpty() }?.let { parameter("cursor", it) }
            limit?.let { parameter("limit", it) }
            search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
        }.body()

    /**
     * Attaches one of the user's files (v0.8.9-rc1); answers the updated project. 409 at the
     * project's file limit (`{ error }`, no code), 400 for a file that can't be a project file.
     */
    suspend fun addProjectFile(projectId: String, fileId: String): ChatProject =
        client.post {
            url { path("api/projects/$projectId/files") }
            setBody(AddChatProjectFileRequest(fileId = fileId))
        }.body()

    /** Detaches a file (v0.8.9-rc1); answers the updated project. The file itself is kept. */
    suspend fun removeProjectFile(projectId: String, fileId: String): ChatProject =
        client.delete {
            url { path("api/projects/$projectId/files/$fileId") }
        }.body()

    suspend fun deleteProject(projectId: String): DeleteChatProjectResponse =
        client.delete {
            url { path("api/projects/$projectId") }
        }.body()

    suspend fun assignConversation(
        conversationId: String,
        projectId: String?,
    ): AssignConversationToProjectResponse =
        client.put {
            url { path("api/projects/conversations/$conversationId") }
            setBody(AssignConversationToProjectRequest(projectId = projectId))
        }.body()
}

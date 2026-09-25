package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Prompt
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.request.AddPromptToGroupRequest
import com.garfiec.librechat.core.model.request.CreatePromptRequest
import com.garfiec.librechat.core.model.request.UpdatePromptGroupRequest
import com.garfiec.librechat.core.model.response.AddPromptToGroupResponse
import com.garfiec.librechat.core.model.response.CreatePromptResponse
import com.garfiec.librechat.core.model.response.PromptGroupListResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Response payload for `POST /api/prompts/groups/:id/use`.
 * Added in upstream v0.8.5 for server-side usage analytics.
 */
@Serializable
data class PromptUseResponse(
    val numberOfGenerations: Int = 0,
)

class PromptsApi constructor(
    private val client: HttpClient,
    private val json: Json,
) {
    suspend fun getPromptGroups(
        pageSize: Int = 10,
        cursor: String? = null,
        name: String? = null,
        category: String? = null,
    ): PromptGroupListResponse =
        client.get {
            url { path("api/prompts/groups") }
            parameter("pageSize", pageSize)
            cursor?.let { parameter("cursor", it) }
            name?.let { parameter("name", it) }
            category?.let { parameter("category", it) }
        }.body()

    /**
     * Every prompt group the user can see, unpaginated — what the composer's `/` picker needs so a
     * large library isn't silently truncated to one page.
     *
     * Returns a raw JSON array, not the [PromptGroupListResponse] envelope `groups` uses. Both routes
     * share the same ACL-aware projection, so `command` is absent here too; match on name/oneliner.
     */
    suspend fun getAllPromptGroups(): List<PromptGroup> =
        client.get {
            url { path("api/prompts/all") }
        }.body()

    /**
     * Null when the group exists but the content filter withheld it: the route projects through
     * `projectStoredPromptGroup`, which returns null for a group whose stored content is blocked,
     * and `res.send(null)` is an empty body with no Content-Type — so ContentNegotiation never
     * engages and a typed decode throws instead of reporting absence. Reachable on a traversal
     * error with no PII finding, where the 400 guard and the projector disagree.
     */
    suspend fun getPromptGroup(groupId: String): PromptGroup? {
        val text = client.get {
            url { path("api/prompts/groups/$groupId") }
        }.bodyAsText().trim()
        if (text.isEmpty() || text == "null") return null
        return json.decodeFromString<PromptGroup>(text)
    }

    /** Creates a prompt and its group. This route alone answers with `{ prompt, group }` — see [CreatePromptResponse]. */
    suspend fun createPrompt(prompt: CreatePromptRequest): PromptGroup =
        client.post {
            url { path("api/prompts") }
            setBody(prompt)
        }.body<CreatePromptResponse>().group

    suspend fun updatePromptGroup(groupId: String, update: UpdatePromptGroupRequest): PromptGroup =
        client.patch {
            url { path("api/prompts/groups/$groupId") }
            setBody(update)
        }.body()

    suspend fun deletePromptGroup(groupId: String) {
        client.delete {
            url { path("api/prompts/groups/$groupId") }
        }
    }

    /**
     * Adds a new version to an existing group, and answers the version it created.
     *
     * Adding a version does not make it live — the group's `productionId` still points at the old
     * one, so callers editing a prompt must follow this with [updatePromptProductionTag], using the
     * returned id. Request and response both nest under `prompt`; see [AddPromptToGroupRequest] and
     * [AddPromptToGroupResponse].
     */
    suspend fun addPromptToGroup(groupId: String, request: AddPromptToGroupRequest): Prompt =
        client.post {
            url { path("api/prompts/groups/$groupId/prompts") }
            setBody(request)
        }.body<AddPromptToGroupResponse>().prompt

    /**
     * Promotes a version to its group's production prompt — the body every prompt surface reads.
     *
     * Deliberately sends no body: the route promotes `req.params.promptId` and never reads
     * `req.body`, so a body naming a different prompt is silently ignored while reading as though
     * it chose. The response is `{ "message": ... }` with HTTP 200 either way, so there is nothing
     * worth decoding; callers re-read the group to confirm the tag moved.
     */
    suspend fun updatePromptProductionTag(promptId: String) {
        client.patch {
            url { path("api/prompts/$promptId/tags/production") }
        }
    }

    /**
     * Get all prompts belonging to a group.
     * Backend returns a raw JSON array of Prompt objects.
     */
    suspend fun getPromptsByGroupId(groupId: String): List<Prompt> =
        client.get {
            url { path("api/prompts") }
            parameter("groupId", groupId)
        }.body()

    /**
     * Records a prompt-group usage event for analytics (v0.8.5+).
     * Fire-and-forget — callers should not block UI on the response.
     */
    suspend fun recordPromptGroupUse(groupId: String): PromptUseResponse =
        client.post {
            url { path("api/prompts/groups/$groupId/use") }
        }.body()
}

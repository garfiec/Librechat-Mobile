package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.request.TagCreateRequest
import com.garfiec.librechat.core.model.request.TagEditRequest
import com.garfiec.librechat.core.model.request.TagUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.appendPathSegments
import io.ktor.http.encodeURLParameter
import io.ktor.http.path

/** `/api/tags`: the user's conversation tags (bookmarks). Every route needs BOOKMARKS USE; none wraps in `arg`. */
class TagsApi(
    private val client: HttpClient,
) {
    suspend fun getTags(): List<ConversationTag> =
        client.get {
            url { path("api/tags") }
        }.body()

    /** Creates [tag] at the end of the order. An existing name comes back unchanged and is not attached. */
    suspend fun createTag(
        tag: String,
        description: String? = null,
        addToConversation: Boolean = false,
        conversationId: String? = null,
    ): ConversationTag =
        client.post {
            url { path("api/tags") }
            setBody(TagCreateRequest(tag, description, addToConversation, conversationId))
        }.body()

    /**
     * Renames, re-describes or moves [currentTag]. Null fields are left alone; `description = ""`
     * clears it. A rename cascades to every chat server-side; renaming onto an existing name is a 500.
     */
    suspend fun updateTag(
        currentTag: String,
        newTag: String? = null,
        description: String? = null,
        position: Int? = null,
    ): ConversationTag =
        client.put {
            tagPath(currentTag)
            setBody(TagEditRequest(tag = newTag, description = description, position = position))
        }.body()

    /** Deletes [tag] and pulls it from every chat. 404 when it doesn't exist. */
    suspend fun deleteTag(tag: String): ConversationTag =
        client.delete {
            tagPath(tag)
        }.body()

    /** Replaces [conversationId]'s whole tag set with [tags]; returns the set the server stored. */
    suspend fun updateConversationTags(conversationId: String, tags: List<String>): List<String> =
        client.put {
            url { path("api/tags/convo/$conversationId") }
            setBody(TagUpdateRequest(tags = tags))
        }.body()

    /**
     * `api/tags/{tag}` with the name escaped TWICE, unlike every other path parameter in the app.
     * Upstream `api/server/routes/tags.js` runs `decodeURIComponent(req.params.tag)` on a param
     * Express has already decoded once, so a name escaped once (as the web client sends it) loses a
     * `%`: `50%` throws in the second decode (500) and a literal `%20` turns into a space (404).
     * Escaping twice makes the two decodes give back the name exactly. Registered in
     * `scripts/mirrors.json`: if upstream drops its extra decode, this must go back to escaping once.
     */
    private fun HttpRequestBuilder.tagPath(tag: String) {
        url {
            appendPathSegments("api", "tags")
            appendPathSegments(tag.encodeURLParameter(), encodeSlash = true)
        }
    }
}

package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.request.CreateShareRequest
import com.garfiec.librechat.core.model.request.ForkSharedConversationRequest
import com.garfiec.librechat.core.model.request.UpdateShareRequest
import com.garfiec.librechat.core.model.response.ForkConversationResponse
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.response.SharedConversationSnapshot
import com.garfiec.librechat.core.model.response.SharedLinksResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.path

class ShareApi constructor(
    private val client: HttpClient,
) {
    /**
     * GET /api/share — one page of the caller's links.
     *
     * [sortBy] is `createdAt` | `title` and [sortDirection] `asc` | `desc`; the route falls back
     * to newest first on anything else. A [cursor] is only valid for the sort that produced it
     * (400 otherwise), and [search] is trimmed and capped at 256 characters (400 past it).
     */
    suspend fun getSharedLinks(
        cursor: String? = null,
        sortBy: String? = null,
        sortDirection: String? = null,
        search: String? = null,
        isPublic: Boolean? = null,
    ): SharedLinksResponse =
        client.get {
            url { path("api/share") }
            parameter("cursor", cursor)
            parameter("pageSize", 25)
            parameter("sortBy", sortBy)
            parameter("sortDirection", sortDirection)
            parameter("search", search)
            // Older backends (< 0.8.7) filter the route on `isPublic`, defaulting to false when
            // absent — so the param must be sent explicitly there. Ktor omits null parameters.
            parameter("isPublic", isPublic)
        }.body()

    /**
     * The caller's live share link for a conversation, if any. Present on every supported backend
     * (0.8.4 onward); the web client reads it before offering to create one. Its `_id` is the
     * link's ACL resource id, which the list does not carry.
     */
    suspend fun getShareLink(conversationId: String): ShareLinkLookup =
        client.get {
            url { path("api/share/link/$conversationId") }
        }.body()

    /**
     * Mints a new link. 409 `SHARE_EXISTS` while a live one exists; see [getShareLink].
     *
     * [snapshotFiles] null is omitted, which the route reads as "include files". 400
     * `TARGET_MESSAGE_NOT_FOUND` when [targetMessageId] isn't saved yet, `NO_MESSAGES` when there
     * is nothing to share, and a content-filter 400 when the server's filter refuses the chat.
     */
    suspend fun createShareLink(
        conversationId: String,
        targetMessageId: String? = null,
        snapshotFiles: Boolean? = null,
    ): SharedLink =
        client.post {
            url { path("api/share/$conversationId") }
            setBody(CreateShareRequest(targetMessageId = targetMessageId, snapshotFiles = snapshotFiles))
        }.body()

    /**
     * PATCH /api/share/:shareId — re-publishes the link against the conversation's current state.
     *
     * **Not a visibility toggle, and not a refresh.** From v0.8.8-rc1 the `shareId` is stable
     * across a re-publish and the link keeps working; what changes is the content behind it.
     * Earlier servers mint a new id and orphan the URL already handed out.
     *
     * Requires the **SHARED_LINKS CREATE** permission — re-scoping a link re-publishes
     * conversation content, so revoking CREATE has to stop updates too. `DELETE` on this route
     * stays ungated, so a role that may no longer re-publish may still revoke. A role with
     * `SHARED_LINKS.USE` but not `CREATE` gets 403 here where it previously succeeded.
     *
     * The route re-derives the link's file choice from the body on every call, and an absent
     * `snapshotFiles` means "include files": send the link's stored choice in [request] or an
     * update quietly turns files back on. A link that is gone answers 404 with no body.
     */
    suspend fun updateShareLink(shareId: String, request: UpdateShareRequest): SharedLink =
        client.patch {
            url { path("api/share/$shareId") }
            setBody(request)
        }.body()

    /** Revokes a link. 404 when it is already gone. Never permission-gated. */
    suspend fun deleteShareLink(shareId: String) {
        client.delete {
            url { path("api/share/$shareId") }
        }
    }

    /**
     * GET /api/share/:shareId — the shared conversation as a viewer sees it. 404 with no body
     * when the link is gone, 429 past the share-view rate limit, and a content-filter 400 when
     * the server's filter now refuses the content.
     */
    suspend fun getSharedSnapshot(shareId: String): SharedConversationSnapshot =
        client.get {
            url { path("api/share/$shareId") }
        }.body()

    /**
     * GET /api/share/:shareId/files/:fileId — one file of a shared conversation, as a viewer may
     * read it: only files the link snapshotted (404 otherwise, and when the link excludes files or
     * the original is gone). A file whose source is `text` comes back as its extracted text,
     * `text/plain`. The owner's `/api/files/download/...` route refuses a viewer; this one checks
     * the share's own ACL.
     */
    suspend fun downloadSharedFile(shareId: String, fileId: String): ByteArray =
        client.get {
            url { path("api/share/$shareId/files/$fileId") }
        }.body()

    /**
     * Copies a shared conversation into the caller's own account so they can continue it
     * (v0.8.8 line, #13714).
     *
     * Not the same route as `POST /api/convos/fork`, and not reachable through it: that one
     * forks a conversation the caller already owns, this one forks from a share link under the
     * share's own ACL (`canAccessSharedLink`), which is how a recipient who owns nothing gets a
     * copy. It requires a signed-in caller and is rate-limited per IP and per user (429).
     *
     * [targetMessageIndex] is the index, in the snapshot's `messages`, of the tip of the branch
     * to copy; the server copies the direct path to it. [shareRevision] is the snapshot's
     * `updatedAt`: the server refuses a link updated since with 409 `SHARE_REVISION_MISMATCH`,
     * and IGNORES the index without one, copying the whole share. The response is the same
     * `{conversation, messages}` pair the owner-side fork returns.
     */
    suspend fun forkSharedConversation(
        shareId: String,
        targetMessageIndex: Int? = null,
        shareRevision: String? = null,
    ): ForkConversationResponse =
        client.post {
            url { path("api/share/$shareId/fork") }
            setBody(ForkSharedConversationRequest(targetMessageIndex, shareRevision))
        }.body()
}

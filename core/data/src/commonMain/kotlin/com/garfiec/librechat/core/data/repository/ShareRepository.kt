package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.response.ForkConversationResponse
import com.garfiec.librechat.core.model.response.ShareLinkLookup
import com.garfiec.librechat.core.model.response.SharedConversationSnapshot
import com.garfiec.librechat.core.model.response.SharedLinksResponse
import com.garfiec.librechat.core.model.share.SharedLinksSort
import kotlinx.coroutines.flow.StateFlow

interface ShareRepository {
    /**
     * Bumped after every change to the user's links this app made or found: a create, an update,
     * a delete, and an update or delete that found the link already gone. Lists of links reload on
     * it (`.drop(1)`, as with [ProjectRepository.revision]).
     */
    val revision: StateFlow<Long>

    /** The public URL of a link on the active server: `<server>/share/<shareId>`. The one builder. */
    fun shareUrl(shareId: String): String

    /**
     * The conversation's newest live link (`GET /api/share/link/:conversationId`), or a lookup
     * with `success = false` when it has none. Its `_id` is the link's ACL id and its
     * `snapshotFiles` the stored file choice; the list carries neither.
     */
    suspend fun lookupShareLink(conversationId: String): Result<ShareLinkLookup>

    /**
     * Mints a link and returns its `shareId`.
     *
     * The server refuses a second link while one is live (409 `SHARE_EXISTS`). That happens when a
     * link appeared since the caller's lookup (another device), so a conflict is answered with the
     * live link's id from a fresh lookup; the conflict stands only when no live link turns up.
     *
     * @param targetMessageId the branch tail to share up to, or null for the newest branch.
     * @param snapshotFiles whether the link includes files; null is omitted, which the server
     *   reads as true.
     */
    suspend fun createShareLink(
        conversationId: String,
        targetMessageId: String?,
        snapshotFiles: Boolean?,
    ): Result<String>

    /**
     * Re-publishes a link against the conversation's current state.
     *
     * The `shareId` is stable across this on v0.8.8-rc1+ (earlier servers mint a new one). Needs
     * SHARED_LINKS CREATE; a role that lost it gets 403 here while its DELETE still works. A link
     * that is gone answers 404, which is an error here (unlike [deleteShareLink]).
     *
     * @param snapshotFiles the link's file choice. The server re-derives it from every update and
     *   reads an omitted value as true, so a null here turns files back on: pass the stored choice.
     */
    suspend fun updateShareLink(
        shareId: String,
        targetMessageId: String?,
        snapshotFiles: Boolean?,
    ): Result<SharedLink>

    /** Revokes a link. A link that is already gone (404) counts as deleted. */
    suspend fun deleteShareLink(shareId: String): Result<Unit>

    /**
     * One page of the user's links. A [cursor] belongs to the [sort] and [search] that produced
     * it; the server 400s a cursor from another sort, so a change of either starts from null.
     * [search] is trimmed and cut to [com.garfiec.librechat.core.model.share.MAX_SHARED_LINK_SEARCH_LENGTH].
     */
    suspend fun getSharedLinksPaginated(
        cursor: String? = null,
        sort: SharedLinksSort = SharedLinksSort.NEWEST,
        search: String? = null,
    ): Result<SharedLinksResponse>

    /** The shared conversation as a viewer sees it (`GET /api/share/:shareId`). */
    suspend fun getSharedSnapshot(shareId: String): Result<SharedConversationSnapshot>

    /**
     * The bytes of one file in a shared conversation, through the share's own route (a viewer
     * can't use the owner's download route). A `text`-source file comes back as its text.
     */
    suspend fun downloadSharedFile(shareId: String, fileId: String): Result<ByteArray>

    /**
     * Copies a shared conversation into the caller's account so they can carry it on
     * (`POST /api/share/:shareId/fork`, v0.8.8 line).
     *
     * Distinct from [ConversationRepository.forkConversation], which needs the caller to already
     * own the source; this forks under the share link's own ACL. [targetMessageIndex] is the index
     * of the active branch's tip in the snapshot's messages (a recipient never sees server message
     * ids), and [shareRevision] the snapshot's `updatedAt`: the server refuses a link updated since
     * (409 `SHARE_REVISION_MISMATCH`) and ignores the index without a revision.
     */
    suspend fun forkSharedConversation(
        shareId: String,
        targetMessageIndex: Int?,
        shareRevision: String?,
    ): Result<ForkConversationResponse>
}

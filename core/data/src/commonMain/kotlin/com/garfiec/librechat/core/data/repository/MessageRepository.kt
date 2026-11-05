package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.response.OwnerText
import com.garfiec.librechat.core.model.response.SubagentAttachmentCallsEntry
import com.garfiec.librechat.core.model.response.SubagentAttachmentFile
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /**
     * Fetches a conversation's messages and upserts them into the cache. [toolPreviews] asks the
     * server for bounded previews of settled tool calls (v0.8.9-rc1); previewed rows are cached WITH
     * their markers, so nothing reads them as complete. Deliberately no default: every caller states
     * whether shortened tool output is acceptable for what it does with the result.
     */
    suspend fun getMessages(conversationId: String, toolPreviews: Boolean): Result<List<Message>>

    /**
     * The conversation's messages with every tool call in full, never cached: an export must not be
     * able to flip cached rows, and a preview must never reach an export. Falls back to the cached
     * rows when the network fails; callers that need fullness check `containsToolCallPreview`.
     */
    suspend fun fetchMessagesFull(conversationId: String): Result<List<Message>>

    /**
     * One stored tool call in full (`GET …/parts/:partIndex`), network-direct: nothing is written.
     * A caller that wants it kept stores it with [storeToolCallParts]. [partIndex] is the raw index
     * into `Message.content`.
     */
    suspend fun getToolCallPart(
        conversationId: String,
        messageId: String,
        partIndex: Int,
        toolCallId: String,
        stepId: String?,
        agentId: String?,
    ): Result<AgentToolCall>

    /**
     * Full tool calls stored for [refs] in [conversationId], for the active account. Rows are
     * returned as stored: whether one still stands for the preview the message now carries is the
     * caller's check (same full lengths), since only it holds the preview.
     */
    /**
     * Which child calls of [messageId]'s stored subagent transcripts [files] were saved under
     * (`GET …/subagent-calls`, v0.8.9-rc1), asked in batches of 32. Network-direct, never cached.
     * Only meaningful for a message holding a previewed subagent call (`subagentContentOmitted`),
     * which only an rc1+ server sends, so the route exists whenever a caller asks.
     */
    suspend fun getSubagentAttachmentCalls(
        conversationId: String,
        messageId: String,
        files: List<SubagentAttachmentFile>,
    ): Result<List<SubagentAttachmentCallsEntry>>

    suspend fun storedToolCallParts(conversationId: String, refs: List<ToolCallPartRef>): Map<ToolCallPartRef, AgentToolCall>

    /**
     * Keeps fetched full tool calls of **finished** messages, beside (never inside) the message rows,
     * so the next session opens them without a fetch. Rows whose message is not cached for the
     * account are skipped. [originAccount] follows the [cacheMessages] contract. Callers must not
     * call this while their conversation is streaming (the no-Room-write-mid-stream invariant).
     */
    suspend fun storeToolCallParts(conversationId: String, parts: Map<ToolCallPartRef, AgentToolCall>, originAccount: AccountId?)

    /** The refs of every full tool call stored for [conversationId], with what each costs in characters. */
    suspend fun storedToolCallPartSizes(conversationId: String, originAccount: AccountId?): Map<ToolCallPartRef, Long>

    /**
     * Persists [messages] to the local cache only — no network call. Records what a completed
     * stream already delivered (the SSE Final event is authoritative) so reopening shows it,
     * without a redundant `GET /messages` round-trip. The Room flow auto-emits.
     *
     * [originAccount] is the account captured at send time (origin-capture provenance): this write
     * lands after the stream, possibly after an account switch, so the rows are stamped with the
     * originating account rather than the live active one. Null (foreground callers) stamps the live
     * active account. See `resolveWriteAccountId`.
     */
    suspend fun cacheMessages(messages: List<Message>, originAccount: AccountId?)

    /**
     * Re-reads a conversation's messages from the server and **replaces** its cached rows, so
     * server-side deletions disappear locally where [getMessages]' upsert would leave them behind.
     *
     * [originAccount] carries the same origin-capture contract as [cacheMessages], and here it gates
     * the network leg too: a deferred caller whose origin is no longer the live account is skipped
     * rather than sending that account's conversation id to a different server under a different
     * bearer. Deliberately no default — a silent one would let new deferred callers compile with
     * land-time attribution, which is the mis-attribution this parameter exists to prevent. Pass null
     * from foreground callers, where entry *is* land time. [toolPreviews] as for [getMessages], and
     * equally without a default.
     */
    suspend fun refreshMessages(
        conversationId: String,
        originAccount: AccountId?,
        toolPreviews: Boolean,
    ): Result<List<Message>>
    suspend fun updateFeedback(conversationId: String, messageId: String, feedback: MinimalFeedback?): Result<Unit>

    /**
     * Edits the message's flat `text`. For a reply rendered from content parts this writes a field
     * nothing displays — use [updateMessageContentPart] instead.
     */
    suspend fun updateMessageText(conversationId: String, messageId: String, text: String): Result<Unit>

    /**
     * Edits one content part in place, by its raw index into `Message.content`.
     *
     * The server refuses an index that is out of range, or one naming a part that is neither TEXT
     * nor THINK, so a wrong index surfaces as [Result.Error] rather than writing the wrong part.
     * Each call rewrites the whole stored content array, so concurrent calls for two parts of the
     * same message race — callers save sequentially.
     */
    suspend fun updateMessageContentPart(
        conversationId: String,
        messageId: String,
        index: Int,
        text: String,
    ): Result<Unit>
    suspend fun branchMessage(conversationId: String, messageId: String, agentId: String? = null): Result<Message>

    /**
     * The author's original text of redacted user turns (v0.8.9-rc1), network-direct and never
     * cached: held in memory by the caller only. [messageIds] is one batch of at most
     * [OWNER_TEXT_BATCH] ids.
     */
    suspend fun getOwnerTexts(conversationId: String, messageIds: List<String>): Result<List<OwnerText>>
}

/** The owner-text route's per-request cap. */
const val OWNER_TEXT_BATCH = 50

/**
 * Who a stored tool call is: its message plus the full-part route's identity (`id + stepId +
 * part.agentId`). Blank [stepId] / [agentId] mean absent.
 */
data class ToolCallPartRef(
    val messageId: String,
    val toolCallId: String,
    val stepId: String?,
    val agentId: String?,
) {
    /** The same ref with blanks read as absent, so `""` and null never name two different rows. */
    fun normalized(): ToolCallPartRef = copy(stepId = stepId?.ifEmpty { null }, agentId = agentId?.ifEmpty { null })
}

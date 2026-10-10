package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ContextUsageTotals
import com.garfiec.librechat.core.model.usage.UsageAmount
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.PendingUsage
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft

enum class ChatScreenState { LANDING, LOADING, ACTIVE }

/**
 * The message tree + live streaming surface: persisted messages, the active display path,
 * branch selection, and everything the SSE stream mutates (streaming text, tool calls,
 * attachments, retry/refresh flags, context/token usage). All five audited atomic transactions
 * (completion flash, begin-stream, reset, applyComposer, branch switch) live within this one
 * slice so they stay single StateFlow emissions. Written by [ChatViewModel],
 * ConversationLoadDelegate (every Room emission), SendDispatchDelegate (the optimistic insert
 * that starts a turn), StreamingManagerDelegate, MessageTreeDelegate, MessageEditingDelegate,
 * ComparisonModeDelegate and OfficePreviewDelegate.
 */
@Immutable
data class MessagesState(
    val screenState: ChatScreenState = ChatScreenState.LANDING,
    val messages: List<Message> = emptyList(),
    val displayMessages: List<MessageNode> = emptyList(),
    /**
     * A just-sent optimistic user message handed off from the NewChat landing VM, kept on screen
     * until the server persists its own copy. `loadConversation` drops it once the server's copy
     * arrives, matched by id or by content ([isServerCopyOf] — rc3+ re-mints the id). Null in every
     * other case. See [NewChatSelectionHandoff].
     */
    val pendingResumeUserMessage: Message? = null,
    val activeBranches: Map<String, Int> = emptyMap(),
    /**
     * The response message that just took over from the streaming bubble, or null.
     *
     * Written in the same atomic update as the swap, so by the time the finalized message is
     * composable this already says which one it is — a UI-side derivation cannot do that, because
     * an effect body runs after the composition that registered it and the groups have already
     * chosen their initial state by then.
     *
     * Only a finalize writes it, which is what makes it a TRANSITION rather than the state "not
     * streaming" — simply opening a conversation must not mark its last message as freshly
     * settled. It is deliberately NOT cleared at the next turn boundary: a drain of a non-empty
     * queue runs `beginStreaming` inline in the same Main dispatch as the finalize (nothing on
     * that path suspends — `awaitReplySettled`'s predicate is already true), so a clear there
     * lands before Compose ever sees the flag set. Letting it persist is safe because the value
     * is a message id: it can only ever re-match the one message it named, and the next finalize
     * overwrites it.
     *
     * The one consumer is [ActivityGroup]'s auto-collapse suppression.
     */
    val justSettledMessageId: String? = null,
    val isStreaming: Boolean = false,
    /**
     * A manual compaction this client submitted is still running (v0.8.8-rc3). Distinct from
     * [isStreaming], which a compaction also sets: the compact action needs to label ITSELF as
     * running, and a compaction produces no message deltas, so nothing else distinguishes it from
     * an ordinary turn. Retired by whichever teardown ends the run.
     */
    val isCompacting: Boolean = false,
    val streamingContent: String = "",
    /**
     * The live reply's reasoning, apart from [streamingContent] so the streaming bubble renders it
     * in a collapsed Thinking block rather than as body text. Cleared with it everywhere, including
     * in the atomic finalize, where the persisted message's THINK part takes over.
     */
    val streamingThinking: String = "",
    val activeToolCalls: List<ActiveToolCall> = emptyList(),
    /** Attachments received during SSE streaming (e.g., tool-generated images). Cleared when
     *  streaming ends. */
    val streamingAttachments: List<Attachment> = emptyList(),
    /** SSE reconnection retry state (null when not retrying). */
    val retryInfo: RetryInfo? = null,
    /**
     * A messages fetch is in flight — a pull-to-refresh, or the background revalidate behind a
     * cache-first open. Drives `MessageList`'s pull-to-refresh indicator, which animates off this
     * boolean alone and needs no gesture. `refreshMessages` early-returns while it is set, so the
     * two paths cannot race each other's clear.
     */
    val isRefreshingMessages: Boolean = false,
    /**
     * The message fetch failed *and* the cache had nothing to fall back to. Drives the retryable
     * empty state; the `error` banner is transient and cannot carry this on its own. Cleared by
     * any load that succeeds (including an offline cache hit).
     */
    val messagesLoadFailed: Boolean = false,
    /**
     * The gauge's context-window reading: live from the stream, or, outside one, resolved by
     * `ContextProjectionDelegate` from the displayed branch.
     */
    val contextUsage: ContextUsage? = null,
    /** Where [contextUsage] came from; written together with it, null when it is null. */
    val contextUsageSource: ContextUsageSource? = null,
    /** Usage folded from the run's live `on_token_usage` events, until its reply carries its own. */
    val pendingUsage: PendingUsage = PendingUsage.EMPTY,
    /** Subagent usage committed this session (shown as "all branches"). */
    val sessionSubagentUsage: UsageAmount = UsageAmount.EMPTY,
    /** The breakdown's branch-wide figures, derived by `ContextProjectionDelegate`. */
    val contextUsageTotals: ContextUsageTotals = ContextUsageTotals(),
    /**
     * The live human-review pause blocking this run, or null when nothing is awaiting the user
     * (v0.8.8 HITL). Set from `on_pending_action`, from `resumeState.pendingAction` on a
     * reconnect, and from `/chat/status` on a cold open; cleared when the user's decision is
     * accepted and at every stream end.
     *
     * [isStreaming] stays TRUE alongside it — the run has not finished, the SSE stream is still
     * open, and no `final` frame is coming until the pause resolves. Rendering keys off this
     * field, not off `isStreaming`, to tell "waiting on the model" from "waiting on you".
     */
    val pendingAction: PendingAction? = null,
    /** A decision for [pendingAction] is in flight; the resolve controls are disabled meanwhile. */
    val isResolvingPendingAction: Boolean = false,
    /**
     * A resume for [pendingAction] has failed and the pause is still up. Gates the ask panel's
     * Stop: Skip goes through the same resume, so a pause the client cannot resolve (a fingerprint
     * 403) leaves Stop as the only way out while the composer is hidden. Cleared when the pause
     * resolves, dies, or is replaced by a different one.
     */
    val pendingActionResumeFailed: Boolean = false,
    /**
     * Per-question answer drafts for an `ask_user_question` [pendingAction], keyed by the batch
     * item's id (the action id for a single question).
     *
     * Hoisted out of the panel because the panel is not the only input: a composer send during
     * the pause fills the active tab's question, and the panel has to *show* that — both so the
     * user can see what was recorded and so its Send enables once the rest are in. Cleared when
     * the pause changes or dies, and set aside across a reconnect into the same pause; see
     * `PendingActionDelegate`.
     */
    val askAnswerDrafts: Map<String, AskAnswerDraft> = emptyMap(),
    /**
     * The question tab the docked ask panel is showing, or null for the first one. Hoisted with
     * [askAnswerDrafts] because the composer's send answers the tab the user is looking at.
     */
    val askActiveQuestionId: String? = null,
    /** The docked ask panel is minimized to its header so the reply above can be read. */
    val askPanelCollapsed: Boolean = false,
    /**
     * Per-call decision drafts for a `tool_approval` [pendingAction], keyed by `tool_call_id`.
     * Hoisted for the same reasons as [askAnswerDrafts]: the docked panel swaps layouts on a fold
     * or rotation, and a reconnect into the same pause must not cost the user their decisions.
     */
    val toolDecisionDrafts: Map<String, ToolDecisionDraft> = emptyMap(),
    /** The paused call the docked tool-approval panel is showing, or null for the first one. */
    val toolActiveCallId: String? = null,
    /** The docked tool-approval panel is minimized to its header. */
    val toolPanelCollapsed: Boolean = false,
)

@Immutable
data class RetryInfo(
    val attempt: Int,
    val maxAttempts: Int,
)

@Immutable
data class ActiveToolCall(
    val id: String,
    val name: String,
    val isComplete: Boolean = false,
    val output: String? = null,
    /** Raw tool-call arguments JSON from [StreamEvent.ToolCallStart]. Holds the
     *  image prompt/quality for image-gen tools so a placeholder can render mid-stream. */
    val input: String? = null,
    /**
     * The run's own terminal verdict from `on_run_step_closed` (v0.8.8-rc2), or null on a server
     * or endpoint that does not emit it — where [isComplete] alone decides, as it always did.
     * A closed step is never running, whatever else the stream did or did not send.
     */
    val closedStatus: RunStepStatus? = null,
)

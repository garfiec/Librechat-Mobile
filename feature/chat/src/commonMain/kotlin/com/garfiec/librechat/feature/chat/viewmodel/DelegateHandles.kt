package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ContextUsageTotals
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.CatalogSkill
import com.garfiec.librechat.feature.chat.util.SpecTools
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-delegate narrowed write handles over the shared [ChatStateHandle].
 *
 * Each delegate receives a handle that can mutate only the [ChatUiState] slices it owns
 * (enforced at compile time by the corresponding `*Writes` class exposing only those slices
 * as vars), plus the shared `error` channel. Reads stay global via [DelegateHandle.state]; a
 * delegate may legitimately read any slice. Writes go through each handle's `update`, whose block
 * receives a mutable writer and is applied as a single [ChatStateHandle.update] — so a multi-field
 * change is one StateFlow emission, preserving the atomic-transaction invariants (completion
 * flash, etc.).
 *
 * The root [ChatStateHandle] is held only by [ChatViewModel] for its orchestration
 * transactions; delegates never receive it. See feature/chat/CLAUDE.md.
 *
 * `error` is intentionally writable from every handle — it is a shared, transient banner
 * channel, not a per-delegate slice. Set it inside an `update` block alongside a slice write,
 * or via [DelegateHandle.setError] on its own.
 */

/**
 * Shared read/error surface for every delegate handle. Holds the global read view ([state]),
 * the delegate's [scope], and the shared-channel [setError]. Subclasses add only their typed
 * `update` (and, where needed, extra read views like [ModelSelectionHandle.stateFlow]) — the
 * per-delegate write narrowing lives entirely in each `*Writes` class.
 */
abstract class DelegateHandle(protected val root: ChatStateHandle) {
    val state: ChatUiState get() = root.state
    val scope: CoroutineScope get() = root.scope
    fun setError(message: String?) = root.update { copy(error = message) }
}

// ── StreamingManagerDelegate ──────────────────────────────────────────────
class StreamingWrites internal constructor(state: ChatUiState) {
    var content: MessagesState = state.content
    var conversation: ConversationMetaState = state.conversation
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(content = content, conversation = conversation, error = error)
}

class StreamingHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: StreamingWrites.() -> Unit) =
        root.update { StreamingWrites(this).apply(block).applyTo(this) }
}

// ── JumpToMessageDelegate ─────────────────────────────────────────────────
class JumpWrites internal constructor(state: ChatUiState) {
    var content: MessagesState = state.content
    var search: ChatSearchState = state.search
    internal fun applyTo(s: ChatUiState) = s.copy(content = content, search = search)
}

/** Writes the branch selection and the find-in-page focus; reads the live state to wait on it. */
class JumpHandle(root: ChatStateHandle) : DelegateHandle(root) {
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: JumpWrites.() -> Unit) =
        root.update { JumpWrites(this).apply(block).applyTo(this) }
}

// ── MessageTreeDelegate ───────────────────────────────────────────────────
class MessageTreeWrites internal constructor(state: ChatUiState) {
    var content: MessagesState = state.content
    var conversation: ConversationMetaState = state.conversation
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(content = content, conversation = conversation, error = error)
}

class MessageTreeHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: MessageTreeWrites.() -> Unit) =
        root.update { MessageTreeWrites(this).apply(block).applyTo(this) }
}

// ── MessageEditingDelegate ────────────────────────────────────────────────
class MessageEditingWrites internal constructor(state: ChatUiState) {
    var editing: MessageEditingState = state.editing
    var content: MessagesState = state.content
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(editing = editing, content = content, error = error)
}

class MessageEditingHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: MessageEditingWrites.() -> Unit) =
        root.update { MessageEditingWrites(this).apply(block).applyTo(this) }
}

// ── ComparisonModeDelegate ────────────────────────────────────────────────
class ComparisonWrites internal constructor(state: ChatUiState) {
    var comparisonState: ComparisonState = state.comparisonState
    var content: MessagesState = state.content
    var conversation: ConversationMetaState = state.conversation
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(
        comparisonState = comparisonState,
        content = content,
        conversation = conversation,
        error = error,
    )
}

class ComparisonHandle(root: ChatStateHandle) : DelegateHandle(root) {
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: ComparisonWrites.() -> Unit) =
        root.update { ComparisonWrites(this).apply(block).applyTo(this) }
}

// ── ModelSelectionDelegate ────────────────────────────────────────────────
class ModelSelectionWrites internal constructor(state: ChatUiState) {
    var selection: ModelSelectionState = state.selection
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(selection = selection, error = error)
}

class ModelSelectionHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the seeder combines over agents/conversationId). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: ModelSelectionWrites.() -> Unit) =
        root.update { ModelSelectionWrites(this).apply(block).applyTo(this) }
}

// ── SkillsToggleDelegate ──────────────────────────────────────────────────
// Owns only the conversation's Skills toggle value; everything that decides which slot it reads
// (conversation id, temp-ness, spec) is written by others.
class SkillsToggleWrites internal constructor(state: ChatUiState) {
    var skillsToggle: Boolean = state.selection.skillsToggle
    internal fun applyTo(s: ChatUiState) = s.copy(selection = s.selection.copy(skillsToggle = skillsToggle))
}

class SkillsToggleHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the slot follows conversation, temp-ness and spec). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: SkillsToggleWrites.() -> Unit) =
        root.update { SkillsToggleWrites(this).apply(block).applyTo(this) }
}

// ── ReasoningCatalogDelegate ──────────────────────────────────────────────
// Owns the selection's reasoning catalog, and on the composer only drops a staged Thinking choice
// the selection no longer accepts.
class ReasoningWrites internal constructor(state: ChatUiState) {
    var reasoningCatalog: ReasoningCatalogState? = state.selection.reasoningCatalog
    var pendingReasoningOverride: ReasoningOverride? = state.composer.pendingReasoningOverride
    internal fun applyTo(s: ChatUiState) = s.copy(
        selection = s.selection.copy(reasoningCatalog = reasoningCatalog),
        composer = s.composer.copy(pendingReasoningOverride = pendingReasoningOverride),
    )
}

class ReasoningHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the target and its resolved setting). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: ReasoningWrites.() -> Unit) =
        root.update { ReasoningWrites(this).apply(block).applyTo(this) }
}

// ── EndpointKeyStatusDelegate ─────────────────────────────────────────────
// Narrower than ModelSelectionHandle on purpose: this delegate owns only the
// per-endpoint key-state map, so it may not touch the rest of the selection slice.
class EndpointKeyWrites internal constructor(state: ChatUiState) {
    var endpointKeyStates: Map<String, KeyState> = state.selection.endpointKeyStates
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) =
        s.copy(selection = s.selection.copy(endpointKeyStates = endpointKeyStates), error = error)
}

class EndpointKeyHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: EndpointKeyWrites.() -> Unit) =
        root.update { EndpointKeyWrites(this).apply(block).applyTo(this) }
}

// ── CodeWorkspaceDelegate ─────────────────────────────────────────────────
class CodeWorkspaceWrites internal constructor(state: ChatUiState) {
    var codeWorkspace: CodeWorkspaceUiState = state.codeWorkspace
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(codeWorkspace = codeWorkspace, error = error)
}

class CodeWorkspaceHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (agent, agents list, endpoints config, chat id). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: CodeWorkspaceWrites.() -> Unit) =
        root.update { CodeWorkspaceWrites(this).apply(block).applyTo(this) }
}

// ── PullRequestDelegate ──────────────────────────────────────────────────
class PullRequestWrites internal constructor(state: ChatUiState) {
    var pullRequest: PullRequestUiState = state.pullRequest
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(pullRequest = pullRequest, error = error)
}

class PullRequestHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (chat id, first-turn streaming). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: PullRequestWrites.() -> Unit) =
        root.update { PullRequestWrites(this).apply(block).applyTo(this) }
}

// ── PresetPromptDelegate ──────────────────────────────────────────────────
class PresetPromptWrites internal constructor(state: ChatUiState) {
    var presetPrompts: PresetPromptState = state.presetPrompts
    var selection: ModelSelectionState = state.selection
    var composer: ComposerState = state.composer
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(
        presetPrompts = presetPrompts,
        selection = selection,
        composer = composer,
        error = error,
    )
}

/**
 * Composer text written here is the user's (an inserted or filled-in prompt), so a change to it is
 * saved as the chat's draft through [persistDraft], as typing is.
 */
class PresetPromptHandle(root: ChatStateHandle, private val persistDraft: (String) -> Unit) : DelegateHandle(root) {
    fun update(block: PresetPromptWrites.() -> Unit) = root.updatePersistingDraft(persistDraft) {
        PresetPromptWrites(this).apply(block).applyTo(this)
    }
}

// ── SkillPickerDelegate ───────────────────────────────────────────────────
// Owns only the picker's skill catalog; picking a skill goes through the send-dispatch chips.
class SkillPickerWrites internal constructor(state: ChatUiState) {
    var skillCatalog: List<CatalogSkill>? = state.presetPrompts.skillCatalog
    var isLoadingSkills: Boolean = state.presetPrompts.isLoadingSkills
    var skillsLoadFailed: Boolean = state.presetPrompts.skillsLoadFailed
    internal fun applyTo(s: ChatUiState) = s.copy(
        presetPrompts = s.presetPrompts.copy(
            skillCatalog = skillCatalog,
            isLoadingSkills = isLoadingSkills,
            skillsLoadFailed = skillsLoadFailed,
        ),
    )
}

class SkillPickerHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: SkillPickerWrites.() -> Unit) =
        root.update { SkillPickerWrites(this).apply(block).applyTo(this) }
}

// ── ConversationActionsDelegate ───────────────────────────────────────────
class ConversationActionsWrites internal constructor(state: ChatUiState) {
    var actions: ConversationActionsState = state.actions
    var conversation: ConversationMetaState = state.conversation
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(actions = actions, conversation = conversation, error = error)
}

class ConversationActionsHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the link-active lookup follows the conversation). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: ConversationActionsWrites.() -> Unit) =
        root.update { ConversationActionsWrites(this).apply(block).applyTo(this) }
}

// ── ChatBookmarksDelegate ─────────────────────────────────────────────────
class BookmarksWrites internal constructor(state: ChatUiState) {
    var bookmarks: ChatBookmarksState = state.bookmarks
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(bookmarks = bookmarks, error = error)
}

class BookmarksHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the checklist follows the open chat and its gate). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: BookmarksWrites.() -> Unit) =
        root.update { BookmarksWrites(this).apply(block).applyTo(this) }
}

// ── SendCompletionDelegate ────────────────────────────────────────────────
class SendCompletionWrites internal constructor(state: ChatUiState) {
    var conversation: ConversationMetaState = state.conversation
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(conversation = conversation, error = error)
}

class SendCompletionHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: SendCompletionWrites.() -> Unit) =
        root.update { SendCompletionWrites(this).apply(block).applyTo(this) }
}

// ── MessageQueueDelegate ──────────────────────────────────────────────────
class QueueWrites internal constructor(state: ChatUiState) {
    var queue: QueueState = state.queue
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(queue = queue, error = error)
}

class QueueHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: QueueWrites.() -> Unit) =
        root.update { QueueWrites(this).apply(block).applyTo(this) }
}

// ── InConversationSearchDelegate ──────────────────────────────────────────
class SearchWrites internal constructor(state: ChatUiState) {
    var search: ChatSearchState = state.search
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(search = search, error = error)
}

class SearchHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: SearchWrites.() -> Unit) =
        root.update { SearchWrites(this).apply(block).applyTo(this) }
}

// ── FavoritesDelegate ─────────────────────────────────────────────────────
class FavoritesWrites internal constructor(state: ChatUiState) {
    var favorites: FavoritesState = state.favorites
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(favorites = favorites, error = error)
}

class FavoritesHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: FavoritesWrites.() -> Unit) =
        root.update { FavoritesWrites(this).apply(block).applyTo(this) }
}

// ── SubagentTraceDelegate ─────────────────────────────────────────────────
class SubagentWrites internal constructor(state: ChatUiState) {
    var subagents: SubagentState = state.subagents
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(subagents = subagents, error = error)
}

class SubagentHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: SubagentWrites.() -> Unit) =
        root.update { SubagentWrites(this).apply(block).applyTo(this) }
}

// ── AttachmentPreviewDelegate ─────────────────────────────────────────────
class AttachmentPreviewWrites internal constructor(state: ChatUiState) {
    var content: MessagesState = state.content
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(content = content, error = error)
}

class AttachmentPreviewHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: AttachmentPreviewWrites.() -> Unit) =
        root.update { AttachmentPreviewWrites(this).apply(block).applyTo(this) }
}

// ── ContextProjectionDelegate ─────────────────────────────────────────────
// Narrow on purpose: this delegate only resolves the context-usage gauge, so it may write
// nothing on the content slice but the reading, its source and the totals derived from it.
class ContextProjectionWrites internal constructor(state: ChatUiState) {
    var contextUsage: ContextUsage? = state.content.contextUsage
    var contextUsageSource: ContextUsageSource? = state.content.contextUsageSource
    var contextUsageTotals: ContextUsageTotals = state.content.contextUsageTotals
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(
        content = s.content.copy(
            contextUsage = contextUsage,
            contextUsageSource = contextUsageSource,
            contextUsageTotals = contextUsageTotals,
        ),
        error = error,
    )
}

class ContextProjectionHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the observer keys off conversation/endpoint/model). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: ContextProjectionWrites.() -> Unit) =
        root.update { ContextProjectionWrites(this).apply(block).applyTo(this) }
}

// ── PendingActionDelegate ─────────────────────────────────────────────────
// Narrow on purpose: resolving a human-review pause only clears/marks the pause fields, so this
// delegate may not touch the streaming text, tool calls, or message tree the resumed run writes.
class PendingActionWrites internal constructor(state: ChatUiState) {
    var pendingAction: PendingAction? = state.content.pendingAction
    var isResolvingPendingAction: Boolean = state.content.isResolvingPendingAction
    var pendingActionResumeFailed: Boolean = state.content.pendingActionResumeFailed
    var askAnswerDrafts: Map<String, AskAnswerDraft> = state.content.askAnswerDrafts
    var askActiveQuestionId: String? = state.content.askActiveQuestionId
    var askPanelCollapsed: Boolean = state.content.askPanelCollapsed
    var toolDecisionDrafts: Map<String, ToolDecisionDraft> = state.content.toolDecisionDrafts
    var toolActiveCallId: String? = state.content.toolActiveCallId
    var toolPanelCollapsed: Boolean = state.content.toolPanelCollapsed
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(
        content = s.content.copy(
            pendingAction = pendingAction,
            isResolvingPendingAction = isResolvingPendingAction,
            pendingActionResumeFailed = pendingActionResumeFailed,
            askAnswerDrafts = askAnswerDrafts,
            askActiveQuestionId = askActiveQuestionId,
            askPanelCollapsed = askPanelCollapsed,
            toolDecisionDrafts = toolDecisionDrafts,
            toolActiveCallId = toolActiveCallId,
            toolPanelCollapsed = toolPanelCollapsed,
        ),
        error = error,
    )
}

class PendingActionHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: PendingActionWrites.() -> Unit) =
        root.update { PendingActionWrites(this).apply(block).applyTo(this) }
}

// ── SteeringDelegate ──────────────────────────────────────────────────────
// Owns only the steer slice. Deliberately cannot write `queue`: a degraded steer must go
// through MessageQueueDelegate's enqueue so it gets a full send spec (model, tools, params,
// account stamp) rather than a bare text row this delegate has no way to build.
class SteeringWrites internal constructor(state: ChatUiState) {
    var steer: SteerState = state.steer
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(steer = steer, error = error)
}

class SteeringHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: SteeringWrites.() -> Unit) =
        root.update { SteeringWrites(this).apply(block).applyTo(this) }
}

// ── UploadIntakeDelegate ──────────────────────────────────────────────────
// Narrow on purpose: this delegate owns only the upload-gate / routing-sheet / intake-count
// fields of the composer, so it may not touch the draft text or the queued-edit session.
class UploadIntakeWrites internal constructor(state: ChatUiState) {
    var isAwaitingUploadSend: Boolean = state.composer.isAwaitingUploadSend
    var pendingUploadRouting: PendingUploadRouting? = state.composer.pendingUploadRouting
    var resolvingPickCount: Int = state.composer.resolvingPickCount
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(
        composer = s.composer.copy(
            isAwaitingUploadSend = isAwaitingUploadSend,
            pendingUploadRouting = pendingUploadRouting,
            resolvingPickCount = resolvingPickCount,
        ),
        error = error,
    )
}

class UploadIntakeHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: UploadIntakeWrites.() -> Unit) =
        root.update { UploadIntakeWrites(this).apply(block).applyTo(this) }
}

// ── ChatConfigDelegate ────────────────────────────────────────────────────
// Narrow on purpose: this delegate owns only the gates, the account slice and the share-link
// bit, so it may not touch the rest of the conversation slice (identity, title, …) that the
// streaming, send and conversation-action paths write.
class ChatConfigWrites internal constructor(state: ChatUiState) {
    var gates: FeatureGatesState = state.gates
    var account: AccountConfigState = state.account
    var sharedLinksEnabled: Boolean = state.conversation.sharedLinksEnabled
    internal fun applyTo(s: ChatUiState) = s.copy(
        gates = gates,
        account = account,
        conversation = s.conversation.copy(sharedLinksEnabled = sharedLinksEnabled),
    )
}

class ChatConfigHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: ChatConfigWrites.() -> Unit) =
        root.update { ChatConfigWrites(this).apply(block).applyTo(this) }
}

// ── ConversationLoadDelegate ──────────────────────────────────────────────
// Owns the message slice wholesale (every Room emission rebuilds messages/displayMessages and
// the load flags live there too). On the composer it may only put text and quote chips back,
// and on the conversation slice only the title: identity, temp-chat and navigation stay with
// the ViewModel's open/reset transactions. Skill chips come back with the text the same way.
class ConversationLoadWrites internal constructor(state: ChatUiState) {
    var content: MessagesState = state.content
    var inputText: String = state.composer.inputText
    var pendingQuotes: List<String> = state.composer.pendingQuotes
    var pendingManualSkills: List<String> = state.composer.pendingManualSkills
    var draftRestore: DraftRestore = state.composer.draftRestore
    var conversationTitle: String? = state.conversation.conversationTitle
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(
        content = content,
        composer = s.composer.copy(
            inputText = inputText,
            pendingQuotes = pendingQuotes,
            pendingManualSkills = pendingManualSkills,
            draftRestore = draftRestore,
        ),
        conversation = s.conversation.copy(conversationTitle = conversationTitle),
        error = error,
    )
}

class ConversationLoadHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the active-branch `combine` input). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: ConversationLoadWrites.() -> Unit) =
        root.update { ConversationLoadWrites(this).apply(block).applyTo(this) }
}

// ── SendReadinessDelegate ─────────────────────────────────────────────────
// Reads only: the gate decides whether a send may go and surfaces the selector through the
// ViewModel, so it has no writer at all.
class SendReadinessHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the readiness await). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
}

// ── SendDispatchDelegate ──────────────────────────────────────────────────
// Owns the message slice for the optimistic insert that starts a turn (the same whole-slice
// write StreamingManagerDelegate makes), and on the composer only what a send consumes: the
// text, the quote chips and a staged routing batch. The queued-edit session belongs to
// QueueOrchestrationDelegate; the send-block reason stays with the ViewModel.
class SendDispatchWrites internal constructor(state: ChatUiState) {
    var content: MessagesState = state.content
    var inputText: String = state.composer.inputText
    var pendingQuotes: List<String> = state.composer.pendingQuotes
    var pendingManualSkills: List<String> = state.composer.pendingManualSkills
    var pendingReasoningOverride: ReasoningOverride? = state.composer.pendingReasoningOverride
    var pendingUploadRouting: PendingUploadRouting? = state.composer.pendingUploadRouting
    var pendingDraftUndo: DraftUndo? = state.composer.pendingDraftUndo
    var draftRestore: DraftRestore = state.composer.draftRestore
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(
        content = content,
        composer = s.composer.copy(
            inputText = inputText,
            pendingQuotes = pendingQuotes,
            pendingManualSkills = pendingManualSkills,
            pendingReasoningOverride = pendingReasoningOverride,
            pendingUploadRouting = pendingUploadRouting,
            pendingDraftUndo = pendingDraftUndo,
            draftRestore = draftRestore,
        ),
        error = error,
    )
}

class SendDispatchHandle(root: ChatStateHandle) : DelegateHandle(root) {
    /** Read-only observation of the full state (the reply-settled await before a drain). */
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: SendDispatchWrites.() -> Unit) =
        root.update { SendDispatchWrites(this).apply(block).applyTo(this) }
}

// ── QueueOrchestrationDelegate ────────────────────────────────────────────
// Writes nothing on the queue slice: every row change goes through MessageQueueDelegate and
// QueuedTurnDelegate. What it owns is the composer swap a queued edit makes — the text, a staged
// batch, the skill and quote chips, the edit session, a pending Undo — and the selection fields that
// swap carries, so `applyComposer` stays one emission.
class QueueOrchestrationWrites internal constructor(state: ChatUiState) {
    var inputText: String = state.composer.inputText
    var pendingUploadRouting: PendingUploadRouting? = state.composer.pendingUploadRouting
    var editingQueuedItem: QueuedEditSession? = state.composer.editingQueuedItem
    var pendingManualSkills: List<String> = state.composer.pendingManualSkills
    var pendingQuotes: List<String> = state.composer.pendingQuotes
    var pendingDraftUndo: DraftUndo? = state.composer.pendingDraftUndo
    var pendingReasoningOverride: ReasoningOverride? = state.composer.pendingReasoningOverride
    var draftRestore: DraftRestore = state.composer.draftRestore
    var choice: ModelChoice = state.selection.choice
    var enabledTools: Set<String> = state.selection.enabledTools
    var selectedMcpServerNames: Set<String> = state.selection.selectedMcpServerNames
    var specTools: SpecTools? = state.selection.specTools
    var modelParameters: ModelParameters = state.selection.modelParameters
    var skillsToggle: Boolean = state.selection.skillsToggle
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(
        composer = s.composer.copy(
            inputText = inputText,
            pendingUploadRouting = pendingUploadRouting,
            editingQueuedItem = editingQueuedItem,
            pendingManualSkills = pendingManualSkills,
            pendingQuotes = pendingQuotes,
            pendingDraftUndo = pendingDraftUndo,
            pendingReasoningOverride = pendingReasoningOverride,
            draftRestore = draftRestore,
        ),
        selection = s.selection.copy(
            choice = choice,
            enabledTools = enabledTools,
            selectedMcpServerNames = selectedMcpServerNames,
            specTools = specTools,
            modelParameters = modelParameters,
            skillsToggle = skillsToggle,
        ),
        error = error,
    )
}

class QueueOrchestrationHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: QueueOrchestrationWrites.() -> Unit) =
        root.update { QueueOrchestrationWrites(this).apply(block).applyTo(this) }
}

// ── DraftItemsDelegate ────────────────────────────────────────────────────
// Owns the attachment strip's Undo slot and nothing else: the removals and restores themselves go
// through the file handler and SendDispatchDelegate, which own those lists.
class DraftItemsWrites internal constructor(state: ChatUiState) {
    var pendingDraftUndo: DraftUndo? = state.composer.pendingDraftUndo
    internal fun applyTo(s: ChatUiState) = s.copy(composer = s.composer.copy(pendingDraftUndo = pendingDraftUndo))
}

class DraftItemsHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: DraftItemsWrites.() -> Unit) =
        root.update { DraftItemsWrites(this).apply(block).applyTo(this) }
}

// ── Platform voice input (VoiceInputDelegate / IosVoiceInput) ─────────────
class VoiceWrites internal constructor(state: ChatUiState) {
    var voice: VoiceState = state.voice
    var composer: ComposerState = state.composer
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(voice = voice, composer = composer, error = error)
}

/** Dictated composer text is saved as the chat's draft through [persistDraft], as typing is. */
class VoiceHandle(root: ChatStateHandle, private val persistDraft: (String) -> Unit) : DelegateHandle(root) {
    fun update(block: VoiceWrites.() -> Unit) = root.updatePersistingDraft(persistDraft) {
        VoiceWrites(this).apply(block).applyTo(this)
    }
}

/** Applies [transform] and hands the composer text to [persistDraft] when the write changed it. */
private fun ChatStateHandle.updatePersistingDraft(
    persistDraft: (String) -> Unit,
    transform: ChatUiState.() -> ChatUiState,
) {
    var written: String? = null
    update {
        val next = transform()
        written = next.composer.inputText.takeIf { it != composer.inputText }
        next
    }
    written?.let(persistDraft)
}

// ── Platform TTS (TextToSpeechDelegate / IosTts) ──────────────────────────
class TtsWrites internal constructor(state: ChatUiState) {
    var voice: VoiceState = state.voice
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(voice = voice, error = error)
}

class TtsHandle(root: ChatStateHandle) : DelegateHandle(root) {
    fun update(block: TtsWrites.() -> Unit) =
        root.update { TtsWrites(this).apply(block).applyTo(this) }
}

// ── Error-only (FileAttachmentDelegate / IosFileHandler) ──────────────────
/** For delegates that keep their real data in a separate flow and only surface errors here. */
class ErrorOnlyHandle(root: ChatStateHandle) : DelegateHandle(root)

// ── OwnerTextDelegate ─────────────────────────────────────────────────────
// Narrow on purpose: it writes the owner texts and nothing else on the content slice.
class OwnerTextWrites internal constructor(state: ChatUiState) {
    var ownerTexts: Map<String, OwnerTextState> = state.content.ownerTexts
    var error: String? = state.error
    internal fun applyTo(s: ChatUiState) = s.copy(content = s.content.copy(ownerTexts = ownerTexts), error = error)
}

class OwnerTextHandle(root: ChatStateHandle) : DelegateHandle(root) {
    val stateFlow: StateFlow<ChatUiState> get() = root.stateFlow
    fun update(block: OwnerTextWrites.() -> Unit) =
        root.update { OwnerTextWrites(this).apply(block).applyTo(this) }
}

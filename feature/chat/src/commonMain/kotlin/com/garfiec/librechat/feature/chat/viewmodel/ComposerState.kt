package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.ReasoningOverride
import com.garfiec.librechat.core.model.request.MAX_MANUAL_SKILLS
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.model.steer.mergeRestagedQuotes
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.util.SpecTools
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PickedFile

/**
 * The editable composer surface: the draft text, any send-block reason, and the active
 * queued-edit session. Written by [ChatViewModel], UploadIntakeDelegate, PresetPromptDelegate,
 * ConversationLoadDelegate (draft / unsent-text restores only), SendDispatchDelegate (the text,
 * quote chips and staged batch a send consumes), QueueOrchestrationDelegate (the composer swap
 * around a queued edit) and the platform voice-input delegates.
 */
@Immutable
data class ComposerState(
    val inputText: String = "",
    /** Set when a send was blocked for a selection/readiness reason. Resolved to a
     *  user-facing string in the Compose layer. Null means no send-block to show. */
    val sendBlockReason: SendBlockReason? = null,
    /** Non-null while a queued item is being edited in the composer (queued-edit mode). Holds the
     *  stashed new-message draft, restored when the edit ends. */
    val editingQueuedItem: QueuedEditSession? = null,
    /** True while a tapped send is parked waiting for its attachment(s) to finish uploading (see
     *  `UploadIntakeDelegate.withUploadGate`). Drives the composer's send button into a spinner the user
     *  can tap to cancel the deferred send. */
    val isAwaitingUploadSend: Boolean = false,
    /**
     * Files picked in Manual routing mode that are waiting on the user's choice. Non-null means
     * the routing sheet is open and NOTHING has been uploaded yet — cancelling leaves no orphaned
     * server record. Written by `UploadIntakeDelegate`; `QueueOrchestrationDelegate.applyComposer` and
     * `SendDispatchDelegate.clearComposer` also null it, inside their own atomic composer transactions.
     */
    val pendingUploadRouting: PendingUploadRouting? = null,
    /**
     * How many picks are between the picker handing files back and those files reaching either the
     * upload handler or [pendingUploadRouting].
     *
     * Intake is asynchronous — it reads the routing preference and may wait on the agent's provider
     * — and during that window the files are in no list any send gate inspects. Without this a send
     * fired in the gap goes out with no attachment and the files ride the *next* message.
     *
     * A **count**, not a flag: nothing disables the attach affordance while intake runs, and a
     * share can arrive on top of a pick, so two intakes overlap readily. With a boolean the first
     * to finish clears the gate while the second is still resolving, re-opening the exact window
     * this exists to close. Written only by `UploadIntakeDelegate`.
     */
    val resolvingPickCount: Int = 0,
    /**
     * Verbatim excerpts staged via the selection toolbar's "Add to chat" (v0.8.7, upstream
     * #13868), one item each in the composer's attachment strip until a fresh send or a
     * composer-origin queue takes them. During a queued edit these are the edited item's own; the
     * draft's are stashed in `ComposerSnapshot`. Taken atomically at spec-mint time (`SendDispatchDelegate.takePendingQuotes`);
     * composer-origin steers leave them staged, and assistants endpoints never take them.
     */
    val pendingQuotes: List<String> = emptyList(),
    /**
     * Skill names picked for the next turn, one item each in the composer's attachment strip (deduped by name,
     * at most `MAX_MANUAL_SKILLS`). Taken with the quote chips by a fresh send or a composer-origin
     * queue (`SendDispatchDelegate.takePendingManualSkills`); a steer leaves them staged (the steer
     * route carries no skills), and assistants endpoints never take them. During a queued edit
     * these are the edited item's own picks; the draft's are stashed in `ComposerSnapshot`.
     */
    val pendingManualSkills: List<String> = emptyList(),
    /**
     * The Thinking chip's choice for the next message only (v0.8.9-rc1 `reasoningOverride`). Taken
     * by a fresh send or a composer-origin queue (`SendDispatchDelegate.takePendingReasoningOverride`),
     * which drops it if the selected model no longer accepts it; a model or endpoint switch drops it
     * the same way (`ReasoningCatalogDelegate`). During a queued edit this is the edited item's.
     */
    val pendingReasoningOverride: ReasoningOverride? = null,
    /**
     * The attachment strip's last removal, while its Undo snackbar can still bring it back (see
     * `DraftItemsDelegate`). Dropped by a send (`SendDispatchDelegate.clearComposer`), the queued-edit
     * swap (`QueueOrchestrationDelegate.applyComposer`) and a conversation switch (the landing
     * reset), each inside its own composer write, so an Undo never lands on a different draft.
     */
    val pendingDraftUndo: DraftUndo? = null,
    /**
     * What a recovery put back while a queued edit held the composer. It belongs to the stashed
     * draft, not the item on screen, so the edit's end adds it whether saved or cancelled
     * (`QueueOrchestrationDelegate.applyComposer`). Always empty outside an edit.
     */
    val draftRestore: DraftRestore = DraftRestore.NONE,
) {
    /** True while at least one intake is still in flight. See [resolvingPickCount]. */
    val isResolvingPickedFiles: Boolean get() = resolvingPickCount > 0
}

/** See [ComposerState.draftRestore]. [text] is set only when the draft had none. */
@Immutable
data class DraftRestore(
    val text: String = "",
    val quotes: List<String> = emptyList(),
    val manualSkills: List<String> = emptyList(),
) {
    companion object {
        val NONE = DraftRestore()
    }
}

/** The stashed draft with [restore] added: the text only into a blank draft, the chips merged. */
internal fun ComposerSnapshot.withRestore(restore: DraftRestore): ComposerSnapshot {
    if (restore == DraftRestore.NONE) return this
    return copy(
        text = text.ifBlank { restore.text },
        quotes = mergeRestagedQuotes(quotes, restore.quotes),
        manualSkills = (restore.manualSkills + manualSkills).distinct().take(MAX_MANUAL_SKILLS),
    )
}

/**
 * A removal from the attachment strip that Undo can reverse. [id] tells one removal from the next,
 * so a snackbar left over from an earlier one can't undo this one.
 */
@Immutable
data class DraftUndo(val id: Long, val removed: RemovedDraftItem)

/** What a [DraftUndo] puts back, and where: [index] in its own list, clamped on the way back. */
@Immutable
sealed interface RemovedDraftItem {
    val index: Int

    /** An uploaded or failed file. A file still uploading is cancelled instead, with no Undo. */
    data class File(val file: AttachedFile, override val index: Int) : RemovedDraftItem

    /** A picked skill; [heldBack] keeps a chip a new chat's first send held back waiting on the catalog. */
    data class Skill(val name: String, override val index: Int, val heldBack: Boolean) : RemovedDraftItem

    data class Quote(val text: String, override val index: Int) : RemovedDraftItem
}

/**
 * One file in a staged routing batch: the pick itself, the route currently selected for it, and
 * the routes it may take (`ChatUiState.uploadRoutesFor`).
 *
 * [choosable] is false for files with only one usable mode (an image, or a type the server cannot
 * extract). Those still ride along so the batch stays whole and the sheet can show what will
 * happen to them — the sheet disables their control rather than hiding the file.
 */
@Immutable
data class PendingUploadFile(
    val file: PickedFile,
    val route: UploadRoute,
    val routes: List<UploadRoute>,
) {
    val choosable: Boolean get() = routes.size > 1
}

/**
 * A staged batch of picked files awaiting a routing decision.
 *
 * [context] is snapshotted with the batch: the sheet is a window in which the user can switch
 * endpoints, and a file must be routed against the selection it was picked under — the same reason
 * `FileAttachmentDelegate` snapshots the endpoint before sizing.
 */
@Immutable
data class PendingUploadRouting(
    val files: List<PendingUploadFile>,
    val context: UploadRoutingContext,
)

/** The selection a staged batch was described against. */
@Immutable
data class UploadRoutingContext(
    val endpoint: String,
    val endpointType: String?,
    val agentProvider: String?,
)

/**
 * Reasons why a send attempt was blocked. Resolved to a user-facing string in the Compose
 * layer via `stringResource`, so the ViewModel stays free of hard-coded English UI copy.
 */
sealed interface SendBlockReason {
    data object SelectAgent : SendBlockReason
    data object SelectModel : SendBlockReason

    /** Compare's second model needs a spec on this server; the selector opens on lane B. */
    data object SelectSecondaryModel : SendBlockReason
    data object AgentsUnavailable : SendBlockReason
    data object AgentNotAvailable : SendBlockReason
    data object ModelNotAvailable : SendBlockReason
    data object ModelLoadFailed : SendBlockReason

    /**
     * The selected endpoint takes the user's own API key, and none is set (or it expired).
     * [endpointLabel] is the name the picker shows for it.
     */
    data class ApiKeyRequired(val endpointLabel: String) : SendBlockReason
}

/**
 * Snapshot of the editable composer surface (the "new message" draft) stashed when entering
 * queued-edit mode and restored on commit/cancel, so editing a queued item never clobbers what
 * the user was already composing.
 *
 * These fields mirror [QueuedMessage]'s source-config fields (model/endpoint/tools/mcp/params) and
 * are captured/applied by `QueueOrchestrationDelegate.captureComposer`/`applyComposer`/`toComposerSnapshot` — a
 * new composer setting must be threaded through all of them (no compiler enforcement).
 */
@Immutable
data class ComposerSnapshot(
    val text: String,
    val attachments: List<AttachedFile> = emptyList(),
    val choice: ModelChoice,
    val enabledTools: Set<String> = emptySet(),
    val mcpServerNames: Set<String> = emptySet(),
    /** The tools added on top of a spec, which ride the swap like the user's own toggles. */
    val specTools: SpecTools? = null,
    val modelParameters: ModelParameters = ModelParameters.DEFAULT,
    /** The pending skill chips: the draft's while stashed, the queued item's picks while editing. */
    val manualSkills: List<String> = emptyList(),
    /**
     * The Skills toggle (`ephemeralAgent.skills`): the conversation's while stashed, the queued
     * item's own while editing, so saving the edit sends it as it was queued.
     */
    val skillsToggle: Boolean = false,
    /** The pending Thinking choice: the draft's while stashed, the queued item's while editing. */
    val reasoningOverride: ReasoningOverride? = null,
    /** The staged quote chips: the draft's while stashed, the queued item's own while editing. */
    val quotes: List<String> = emptyList(),
) {
    val endpoint: String get() = choice.endpoint
    val model: String? get() = choice.model
    val modelSpec: String? get() = choice.spec
}

/**
 * Active queued-edit session: the composer is loaded with [original]'s content + config for
 * editing, while [stashed] holds the new-message draft to restore afterward. [original] stays in
 * the queue until the edit is committed; [originalIndex] is where the edit goes back in if that row
 * has already left the queue by then.
 */
@Immutable
data class QueuedEditSession(
    val original: QueuedMessage,
    val originalIndex: Int,
    val stashed: ComposerSnapshot,
)

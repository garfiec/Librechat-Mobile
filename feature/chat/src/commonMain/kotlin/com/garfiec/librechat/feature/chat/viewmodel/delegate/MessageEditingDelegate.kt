package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.request.EditedContent
import com.garfiec.librechat.feature.chat.util.MessageEditShape
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.util.changedPartIndices
import com.garfiec.librechat.feature.chat.util.editShapeOf
import com.garfiec.librechat.feature.chat.util.findRerunParent
import com.garfiec.librechat.feature.chat.util.hasBlankPartEdit
import com.garfiec.librechat.feature.chat.util.partEditSeeds
import com.garfiec.librechat.feature.chat.util.resubmittablePartIndex
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.MessageEditingHandle
import com.garfiec.librechat.feature.chat.viewmodel.MessageEditingState
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Owns the message-mutation send paths — edit, regenerate, and continue — plus the inline edit
 * field's state. Each path optimistically reshapes the tree (a new sibling or a stream anchor via
 * [MessageTreeDelegate]) and resubmits through [StreamingManagerDelegate], reusing
 * [ChatRequestBuilder] for the shared request pieces.
 *
 * The send-readiness gate lives in [SendReadinessDelegate], injected as [runWhenSendReady].
 *
 * ## Two editors
 * `editShapeOf` picks one when the editor opens, and the same call gates the Edit affordance in
 * `MessageList`, so a button is never offered for a shape this delegate would refuse.
 * - [MessageEditShape.PLAIN_TEXT] edits the flat `text`: every user turn, and a reply with no
 *   content parts. Save sends no `index`. A rerun resubmits the user's draft as a new sibling; on
 *   a reply it can only be a plain regenerate, because `editedContent` is index-addressed over a
 *   content array this message does not have.
 * - [MessageEditShape.CONTENT_PARTS] edits one field per editable part. Save sends each changed
 *   part's `index`; a rerun carries exactly one changed TEXT part as `editedContent` so the reply
 *   continues from it.
 *
 * ## Nothing here may close the editor before the server has answered
 * Every sink reads its `Result` and keeps the editor open on failure, so an offline save, a 5xx or
 * a content-filter refusal never silently leaves the old text in place (#579).
 */
class MessageEditingDelegate(
    private val handle: MessageEditingHandle,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val treeDelegate: MessageTreeDelegate,
    private val streamingManager: StreamingManagerDelegate,
    private val requestBuilder: ChatRequestBuilder,
    private val runWhenSendReady: (onRefused: () -> Unit, action: () -> Unit) -> Unit,
) {

    /**
     * Message ids whose save is still out. Tracked here rather than read off
     * [MessageEditingState.isSavingEdit], because `startEditing` replaces that state: a second Edit
     * on the same row while its save is in flight would otherwise clear the flag and let a second
     * PUT sequence race the first over the same content array — the race the sequential save exists
     * to avoid. Main-thread confined, like every other write in this delegate.
     */
    private val savesInFlight = mutableSetOf<String>()

    private fun closeEditor() = handle.update { editing = MessageEditingState() }

    @OptIn(ExperimentalUuidApi::class)
    private fun editUserMessage(originalMessage: Message, newText: String) {
        val parentMessageId = originalMessage.parentMessageId

        // Optimistically insert the edited text as a new sibling of the original user
        // message and anchor the stream to it, so the new message — with the response
        // streaming below it — replaces the old branch in place. The anchor truncates
        // the active path here (see buildActiveMessagePath), mirroring the web client's
        // `currentMsg + initialResponse` placeholder insert. The optimistic id is sent
        // as `userMessageId` so the server adopts it; that lets the message reconcile by
        // id on Final — via loadConversation() for normal chats, or in-memory via
        // mergeFinalMessagesInMemory for temp chats (which never touch Room).
        val optimisticMessage = Message(
            messageId = Uuid.random().toString(),
            conversationId = handle.state.conversationId ?: "",
            parentMessageId = parentMessageId,
            text = newText,
            isCreatedByUser = true,
            sender = "User",
            createdAt = Clock.System.now().toString(),
            files = originalMessage.files,
            // The edited turn keeps its skill pills and primes the same skills (web's
            // `overrideManualSkills: message.manualSkills` on save-and-submit).
            manualSkills = originalMessage.manualSkills,
        )
        handle.update {
            val updatedMessages = content.messages + optimisticMessage
            content = content.copy(
                messages = updatedMessages,
                displayMessages = buildActiveMessagePath(updatedMessages, content.activeBranches, optimisticMessage.messageId),
            )
        }

        launchSend(
            text = newText,
            parentMessageId = parentMessageId,
            userMessageId = optimisticMessage.messageId,
            files = originalMessage.files,
            manualSkills = originalMessage.manualSkills,
            isEdited = true,
            logLabel = "editUserMessage",
        )
    }

    /**
     * Replays [parentUserMessage] to produce a new reply, optionally continuing from an edited
     * content part of the old one.
     *
     * [editedContent] and the wire `responseMessageId` are a pair: the server reads the latter as
     * the history `head`, so it is what makes the edited message's content the prefix the reply
     * continues from. Passing the content without the id loads an unrelated branch and silently
     * drops the edit. With neither, this is an ordinary regenerate.
     */
    private fun rerunReply(
        aiMessage: Message,
        parentUserMessage: Message,
        editedContent: EditedContent?,
    ) {
        treeDelegate.anchorStreamTo(parentUserMessage.messageId)
        launchSend(
            text = parentUserMessage.text,
            parentMessageId = parentUserMessage.parentMessageId,
            overrideParentMessageId = parentUserMessage.messageId,
            responseMessageId = aiMessage.messageId.takeIf { editedContent != null },
            files = parentUserMessage.files,
            quotes = parentUserMessage.quotes,
            manualSkills = parentUserMessage.manualSkills,
            isEdited = editedContent != null,
            isRegenerate = true,
            editedContent = editedContent,
            logLabel = if (editedContent != null) "rerunEditedReply" else "rerunReply",
        )
    }

    fun regenerateMessage(messageId: String) {
        if (handle.state.isStreaming) return

        val aiMessage = handle.state.messages.find { it.messageId == messageId } ?: return
        if (aiMessage.isCreatedByUser) return

        val parentUserMessage = handle.state.messages.find {
            it.messageId == aiMessage.parentMessageId
        } ?: return

        runWhenSendReady({}) { regenerateMessageNow(parentUserMessage) }
    }

    private fun regenerateMessageNow(parentUserMessage: Message) {
        treeDelegate.anchorStreamTo(parentUserMessage.messageId)
        launchSend(
            text = parentUserMessage.text,
            parentMessageId = parentUserMessage.parentMessageId,
            overrideParentMessageId = parentUserMessage.messageId,
            files = parentUserMessage.files,
            quotes = parentUserMessage.quotes,
            manualSkills = parentUserMessage.manualSkills,
            isRegenerate = true,
            logLabel = "regenerateMessage",
        )
    }

    /**
     * Manual context compaction (v0.8.8-rc3): summarize the branch up to its leaf and end WITHOUT a
     * reply. Regenerate-shaped — no user message is created — which is why it goes down this path
     * and not the new-message one.
     *
     * The shape is borrowed for the CLIENT's benefit only: `isRegenerate` is set here and stripped
     * again by `ChatPayloadBuilder`, because rc3 400s it arriving beside `compact`.
     */
    fun compactConversation() {
        val state = handle.state
        if (!state.canCompactNow) return
        val leaf = state.compactionLeaf ?: return
        runWhenSendReady({}) { compactConversationNow(leaf) }
    }

    private fun compactConversationNow(leaf: Message) {
        handle.update { content = content.copy(isCompacting = true) }
        // The leaf is both the summary's parent and the server-side anchor: `parentMessageId` is
        // what the controller compacts up to.
        treeDelegate.anchorStreamTo(leaf.messageId)
        launchSend(
            text = "",
            parentMessageId = leaf.messageId,
            userMessageId = leaf.messageId,
            // The leaf is a PERSISTED row, not a message this turn minted, so the early-abort
            // un-send must never remove it — the same rule regenerate and continue follow.
            optimisticUserMessageId = null,
            isRegenerate = true,
            compact = true,
            logLabel = "compactConversation",
        )
    }

    fun continueGeneration() {
        if (handle.state.isStreaming) return
        val lastAiMessage = handle.state.displayMessages.lastOrNull {
            !it.message.isCreatedByUser
        } ?: return

        val parentUserMessage = handle.state.messages.find {
            it.messageId == lastAiMessage.message.parentMessageId
        } ?: return

        runWhenSendReady({}) { continueGenerationNow(lastAiMessage.message, parentUserMessage) }
    }

    private fun continueGenerationNow(lastAiMessage: Message, parentUserMessage: Message) {
        launchSend(
            text = parentUserMessage.text,
            parentMessageId = parentUserMessage.parentMessageId,
            overrideParentMessageId = parentUserMessage.messageId,
            responseMessageId = lastAiMessage.messageId,
            files = parentUserMessage.files,
            isEdited = true,
            isRegenerate = true,
            isContinued = true,
            logLabel = "continueGeneration",
        )
    }

    /**
     * Shared tail for the edit / regenerate / continue paths: snapshots the current
     * selection, builds the per-send request pieces via [ChatRequestBuilder], and launches
     * the resubmit stream. Each caller first reshapes the tree (optimistic insert or
     * [MessageTreeDelegate.anchorStreamTo]) and then supplies only the args that differ.
     *
     * Resubmits carry the original user turn's [files] so attachments survive an edit /
     * regenerate / continue (the server otherwise loses them). Regenerate-shaped replays of
     * the parent user turn also carry its persisted [quotes] (web's `overrideQuotes`): the
     * server rebuilds the user message from `req.body.quotes` on a regenerate, so omitting
     * them silently drops the quoted context while the chips stay visible. Continue sends
     * none, matching web. Regenerate and both edits likewise replay the user turn's persisted
     * [manualSkills] (web's `overrideManualSkills`), so the new reply is primed with the skills
     * its pills show; continue and compaction send none. The new-message send path
     * (`SendDispatchDelegate.doSendWithSpec`) is separate: it additionally carries an
     * added-conversation for comparison mode and a bespoke stream-terminated callback this helper
     * deliberately omits.
     */
    @Suppress("LongParameterList")
    private fun launchSend(
        text: String,
        parentMessageId: String?,
        logLabel: String,
        userMessageId: String? = null,
        /**
         * The id the early-abort un-send may remove. Defaults to [userMessageId] because for every
         * path but compaction they are the same message — compaction sends an existing leaf's id as
         * the wire `messageId` while minting nothing, so the two must be separable.
         */
        optimisticUserMessageId: String? = userMessageId,
        overrideParentMessageId: String? = null,
        responseMessageId: String? = null,
        files: List<FileReference>? = null,
        quotes: List<String>? = null,
        manualSkills: List<String>? = null,
        isEdited: Boolean = false,
        isRegenerate: Boolean = false,
        isContinued: Boolean = false,
        compact: Boolean? = null,
        editedContent: EditedContent? = null,
    ) {
        // optimisticUserMessageId is non-null only for the edit-user path, whose optimistic sibling
        // this turn minted; regenerate/continue/rerun/compact resubmit a persisted message the
        // early-abort un-send must never remove.
        streamingManager.prepareForStreaming(
            isEdit = true,
            optimisticUserMessageId = optimisticUserMessageId,
            // The resume pin applies the same skills-flag rule as the body.
            manualSkills = manualSkills,
        )

        val state = handle.state
        val isAgent = state.selectedEndpoint == EndpointConstants.AGENTS
        val webSearchEnabled = state.modelParameters.webSearch
        val ephemeralAgent = requestBuilder.buildEphemeralAgent()
        Logger.d { "$logLabel: webSearch=$webSearchEnabled, ephemeralAgent=$ephemeralAgent" }
        val dispatch = requestBuilder.currentDispatch()
        streamingManager.launchStream(
            chatRepository.startChat(
                text = text,
                conversationId = state.conversationId,
                endpoint = state.selectedEndpoint,
                endpointType = dispatch.endpointType,
                key = dispatch.key,
                modelDisplayLabel = dispatch.modelDisplayLabel,
                model = state.selectedModel,
                spec = state.selectedSpec,
                userMessageId = userMessageId,
                parentMessageId = parentMessageId,
                agentId = if (isAgent) state.selectedModel else null,
                overrideParentMessageId = overrideParentMessageId,
                responseMessageId = responseMessageId,
                isEdited = isEdited,
                isRegenerate = isRegenerate,
                compact = compact,
                isContinued = isContinued,
                webSearch = webSearchEnabled,
                files = files,
                quotes = quotes,
                manualSkills = manualSkills,
                ephemeralAgent = ephemeralAgent,
                isTemporary = state.isTemporaryChat,
                modelParams = requestBuilder.buildModelParams(),
                editedContent = editedContent,
            ),
        )
    }

    // ── Inline edit-field UI state ───────────────────────────────────

    fun startEditing(messageId: String) {
        // A row whose save is still out keeps the editor it has: reseeding it would clear
        // `isSavingEdit` and re-enable a Save that would race the one in flight, and the in-flight
        // one's result would land on an editor it no longer owns.
        if (messageId in savesInFlight) return
        val message = handle.state.messages.find { it.messageId == messageId } ?: return
        when (editShapeOf(message)) {
            // Seeded from the flat text, NOT from `getMessageText`, which concatenates text and
            // reasoning parts for TTS: that would drop the model's reasoning into the answer field,
            // and saving it would write the two back as one blob.
            MessageEditShape.PLAIN_TEXT -> handle.update {
                editing = MessageEditingState(
                    editingMessageId = messageId,
                    editingText = message.text,
                    editingTextOriginal = message.text,
                )
            }
            MessageEditShape.CONTENT_PARTS -> {
                val seeds = partEditSeeds(message)
                handle.update {
                    editing = MessageEditingState(
                        editingMessageId = messageId,
                        partDrafts = seeds,
                        partOriginals = seeds,
                    )
                }
            }
            // The affordance is hidden for this shape; a tap that raced the state change lands here.
            MessageEditShape.NONE -> return
        }
    }

    fun onEditTextChanged(text: String) {
        if (handle.state.isSavingEdit) return
        handle.update { editing = editing.copy(editingText = text) }
    }

    fun onEditPartTextChanged(index: Int, text: String) {
        if (handle.state.isSavingEdit) return
        if (index !in handle.state.editing.partDrafts) return
        handle.update { editing = editing.copy(partDrafts = editing.partDrafts + (index to text)) }
    }

    fun cancelEditing() {
        closeEditor()
    }

    /**
     * Persists the open editor's draft, whichever shape it is. The UI has one Save button and does
     * not route: the live state already says which editor is open, so a single entry point cannot
     * disagree with it.
     */
    fun saveEdit() {
        if (handle.state.editing.isEditingParts) savePartEdits() else saveEditOnly()
    }

    /** Reruns the turn from the open editor, whichever shape it is. See [saveEdit]. */
    fun rerunEdit() {
        if (handle.state.editing.isEditingParts) rerunPartEdit() else rerunPlainTextEdit()
    }

    /**
     * Rerun from the flat-text editor.
     *
     * On a user turn the draft is resubmitted as a new sibling. On a reply it cannot be: there is
     * no content array for `editedContent` to address, so the draft is left unsent and this is a
     * plain regenerate — which the editor's footer says before the button is pressed.
     */
    private fun rerunPlainTextEdit() {
        val state = handle.state
        val messageId = state.editingMessageId ?: return
        val message = state.messages.find { it.messageId == messageId } ?: return
        val newText = state.editingText.trim()
        val original = state.editing.editingTextOriginal
        // Checked BEFORE the editor is cleared: these early returns do not run `onRefused`, so
        // clearing first would throw the draft away for a send that never started.
        if (state.isSavingEdit || state.isStreaming || !state.canStartRun) return

        if (message.isCreatedByUser) {
            // Retained attachments make an otherwise textless turn submittable, as they do in the
            // composer and on the parts path. A plain `isBlank` return here would leave Rerun
            // enabled and silently doing nothing on an image-only turn.
            if (newText.isBlank() && message.files.isNullOrEmpty()) return
            closeEditor()
            // A refusal (no model, a readiness timeout, Compare setup opening during the wait)
            // reopens the edit with its text rather than dropping what the user wrote.
            runWhenSendReady({ reopenPlainText(messageId, newText, original) }) {
                editUserMessage(message, newText)
            }
            return
        }

        val parent = findRerunParent(state.messages, message.parentMessageId) ?: return
        closeEditor()
        runWhenSendReady({ reopenPlainText(messageId, newText, original) }) {
            rerunReply(message, parent, editedContent = null)
        }
    }

    /**
     * Rerun from the content-parts editor.
     *
     * A **user** turn can reach here too — the Assistants thread sync persists one with a
     * `content[]` rather than a flat text — and is resubmitted as a new sibling built from its TEXT
     * drafts joined by newlines, as upstream builds it. Its reasoning parts are not part of what
     * the user said, so they are left out of the new turn.
     *
     * A **reply** has three outcomes, decided by `resubmittablePartIndex`: one changed TEXT part
     * becomes `editedContent` and the reply continues from it; no change is a plain regenerate;
     * anything else is refused here and the footer asks for a Save first (see that function).
     */
    private fun rerunPartEdit() {
        val state = handle.state
        val messageId = state.editingMessageId ?: return
        val message = state.messages.find { it.messageId == messageId } ?: return
        if (state.isSavingEdit || state.isStreaming || !state.canStartRun) return

        val drafts = state.editing.partDrafts
        val originals = state.editing.partOriginals
        if (hasBlankPartEdit(drafts, originals)) return

        if (message.isCreatedByUser) {
            val newText = joinedUserPartText(message, drafts)
            // Retained attachments make an otherwise textless turn submittable, as they do in the
            // composer; a turn with neither text nor files has nothing to send.
            if (newText.isBlank() && message.files.isNullOrEmpty()) return
            closeEditor()
            runWhenSendReady({ reopenParts(messageId, drafts, originals) }) {
                editUserMessage(message, newText)
            }
            return
        }

        val changed = changedPartIndices(drafts, originals)
        val editedIndex = resubmittablePartIndex(message, drafts, originals)
        // Changed, but not in a form one `editedContent` can carry. The affordance is disabled for
        // this, so only a raced tap arrives here.
        if (editedIndex == null && changed.isNotEmpty()) return

        val parent = findRerunParent(state.messages, message.parentMessageId) ?: return
        val editedContent = editedIndex?.let { EditedContent.text(index = it, text = drafts.getValue(it)) }

        closeEditor()
        runWhenSendReady({ reopenParts(messageId, drafts, originals) }) {
            rerunReply(message, parent, editedContent)
        }
    }

    /**
     * A user turn's parts recombined into the text a resubmit sends: its TEXT drafts, in content
     * order, newline-joined (upstream's `join('\n')`). THINK parts are excluded — a user turn does
     * not reason, and anything the sync left in one is not what the user wrote.
     */
    private fun joinedUserPartText(message: Message, drafts: Map<Int, String>): String =
        drafts.keys.sorted()
            .filter { message.content?.getOrNull(it)?.type == ContentType.TEXT }
            .joinToString("\n") { drafts.getValue(it) }
            .trim()

    private fun reopenPlainText(messageId: String, text: String, original: String) = handle.update {
        editing = MessageEditingState(
            editingMessageId = messageId,
            editingText = text,
            editingTextOriginal = original,
        )
    }

    private fun reopenParts(
        messageId: String,
        drafts: Map<Int, String>,
        originals: Map<Int, String>,
    ) = handle.update {
        editing = MessageEditingState(
            editingMessageId = messageId,
            partDrafts = drafts,
            partOriginals = originals,
        )
    }

    /**
     * Persists the flat-text editor's draft, and closes the editor only once the server has taken
     * it. A failure keeps the draft on screen and reports the reason.
     */
    private fun saveEditOnly() {
        val state = handle.state
        val messageId = state.editingMessageId ?: return
        val conversationId = state.conversationId ?: return
        val newText = state.editingText.trim()
        if (newText.isBlank() || state.isSavingEdit) return
        // Gated like `submitFeedback`: this writes Room, which re-emits through the
        // loadConversation observer and rebuilds displayMessages with no streamingLeafId,
        // un-truncating the in-place streaming view. The affordance is disabled too.
        if (state.isStreaming) return
        if (!savesInFlight.add(messageId)) return

        handle.update { editing = editing.copy(isSavingEdit = true) }
        handle.scope.launch {
            val result = try {
                messageRepository.updateMessageText(conversationId, messageId, newText)
            } finally {
                savesInFlight -= messageId
            }
            handle.update {
                // The editor may have moved on — Edit tapped on another row while this was in
                // flight. Closing it then would discard THAT message's draft, and splicing
                // baselines across messages would mark untouched text as unsaved. The write landed either way; only the editor is no longer its business.
                // A reopen of the SAME row cannot happen: `savesInFlight` refuses it.
                if (editing.editingMessageId != messageId) {
                    if (result is Result.Error) error = result.message ?: ChatNotice.MESSAGE_SAVE_FAILED.marker()
                } else if (result is Result.Error) {
                    editing = editing.copy(isSavingEdit = false)
                    error = result.message ?: ChatNotice.MESSAGE_SAVE_FAILED.marker()
                } else {
                    closeEditor()
                }
            }
        }
    }

    /**
     * Persists every changed content part, then closes the editor — but only if all of them landed.
     *
     * Written one part at a time and in order: each call rewrites the whole stored content array
     * server-side, so two in flight would race and the loser's edit would be overwritten by the
     * winner's copy of the old array. Nothing rolls a write back either, so a refused part leaves
     * the earlier ones saved; recording what actually landed is what lets the editor reconcile with
     * the server instead of claiming nothing was saved, and leaves only the refused parts marked as
     * changed.
     */
    private fun savePartEdits() {
        val state = handle.state
        val messageId = state.editingMessageId ?: return
        val conversationId = state.conversationId ?: return
        if (state.isSavingEdit || state.isStreaming) return

        val drafts = state.editing.partDrafts
        val originals = state.editing.partOriginals
        if (hasBlankPartEdit(drafts, originals)) return
        val changed = changedPartIndices(drafts, originals)
        if (changed.isEmpty()) return
        if (!savesInFlight.add(messageId)) return

        handle.update { editing = editing.copy(isSavingEdit = true) }
        handle.scope.launch {
            var failure: Result.Error? = null
            val saved = mutableMapOf<Int, String>()
            try {
                for (index in changed) {
                    // Re-checked per part, not just at the tap: each write reaches Room, and a run
                    // that starts part-way through the sequence would have the rest re-emit through
                    // the loadConversation observer and un-truncate the streaming anchor. Stopping
                    // leaves the unwritten parts marked unsaved, which is what the editor shows.
                    if (handle.state.isStreaming) {
                        failure = Result.Error()
                        break
                    }
                    val text = drafts.getValue(index)
                    val result = messageRepository.updateMessageContentPart(conversationId, messageId, index, text)
                    if (result is Result.Error) {
                        failure = result
                        break
                    }
                    saved[index] = text
                }
            } finally {
                savesInFlight -= messageId
            }
            handle.update {
                // See saveEditOnly: the editor may belong to another message by now.
                if (editing.editingMessageId != messageId) {
                    failure?.let { error = it.message ?: ChatNotice.MESSAGE_SAVE_FAILED.marker() }
                } else if (failure == null) {
                    closeEditor()
                } else {
                    // Advance only the baselines that landed, so the saved parts stop reading as
                    // unsaved and the refused ones keep asking to be saved.
                    editing = editing.copy(
                        isSavingEdit = false,
                        partOriginals = editing.partOriginals + saved,
                    )
                    error = failure.message ?: ChatNotice.MESSAGE_SAVE_FAILED.marker()
                }
            }
        }
    }
}

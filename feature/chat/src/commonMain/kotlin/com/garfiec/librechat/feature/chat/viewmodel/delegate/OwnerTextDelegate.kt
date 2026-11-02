package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.OWNER_TEXT_BATCH
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.OwnerText
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.OwnerTextHandle
import com.garfiec.librechat.feature.chat.viewmodel.OwnerTextState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Reads the author's original text of the open conversation's redacted user turns (v0.8.9-rc1
 * PII protection, upstream `useOwnerMessageTexts`), in memory only.
 *
 * Asks once the chat is not streaming, for every protected turn it holds no answer for, in
 * batches of [OWNER_TEXT_BATCH]; again whenever a new protected turn settles (a reply finished, a
 * reload brought one in). A text is accepted only for the stored turn it was read against: same
 * revision, same filtered text. Never written anywhere but [com.garfiec.librechat.feature.chat.viewmodel.MessagesState.ownerTexts],
 * which a conversation switch clears.
 */
class OwnerTextDelegate(
    private val handle: OwnerTextHandle,
    private val messageRepository: MessageRepository,
) {

    private var shownConversationId: String? = null

    fun start() {
        handle.scope.launch {
            handle.stateFlow
                .map(::Inputs)
                .distinctUntilChanged()
                .collectLatest { inputs ->
                    if (inputs.conversationId != shownConversationId) {
                        shownConversationId = inputs.conversationId
                        if (handle.state.ownerTexts.isNotEmpty()) handle.update { ownerTexts = emptyMap() }
                    }
                    if (!inputs.ready) return@collectLatest
                    val known = handle.state.ownerTexts
                    val missing = inputs.turns.filter { needsOwnerText(it, known[it.messageId]) }
                    fetch(inputs.conversationId ?: return@collectLatest, missing)
                }
        }
    }

    /** "Retry loading original": asks again for every turn that came back without one. */
    fun retry() {
        val state = handle.state
        val conversationId = state.conversationId ?: return
        if (state.isStreaming || !ownerTextSupported(state)) return
        val unavailable = state.ownerTexts.filterValues { it == OwnerTextState.Unavailable }.keys
        val turns = protectedTurns(state).filter { it.messageId in unavailable }
        handle.scope.launch { fetch(conversationId, turns) }
    }

    private suspend fun fetch(conversationId: String, turns: List<Message>) {
        if (turns.isEmpty()) return
        handle.update { ownerTexts = ownerTexts + turns.associate { it.messageId to OwnerTextState.Loading } }
        for (batch in turns.chunked(OWNER_TEXT_BATCH)) {
            val result = messageRepository.getOwnerTexts(conversationId, batch.map { it.messageId })
            val answered = (result as? Result.Success)?.data?.associateBy { it.messageId }
            // A conversation switched while the request was out keeps none of it.
            if (handle.state.conversationId != conversationId) return
            handle.update {
                ownerTexts = batch.fold(ownerTexts) { texts, turn ->
                    when (val state = ownerTextStateFor(turn, answered)) {
                        null -> texts - turn.messageId
                        else -> texts + (turn.messageId to state)
                    }
                }
            }
        }
    }

    /** Everything the fetch reads; it re-runs when any of it changes. */
    private data class Inputs(
        val conversationId: String?,
        val ready: Boolean,
        val turns: List<Message>,
    ) {
        constructor(state: ChatUiState) : this(
            conversationId = state.conversationId,
            ready = !state.isStreaming && state.conversationId != null && ownerTextSupported(state),
            turns = protectedTurns(state),
        )
    }
}

/**
 * The user turns of the open conversation the server redacted, whose originals the author may
 * read. The live optimistic turn is left out: its text is what was typed.
 */
internal fun protectedTurns(state: ChatUiState): List<Message> {
    val conversationId = state.conversationId ?: return emptyList()
    return state.messages.filter {
        it.isCreatedByUser && !it.privacyRevision.isNullOrEmpty() && it.conversationId == conversationId &&
            it.messageId != state.pendingResumeUserMessage?.messageId
    }
}

/**
 * The owner-text route is new in v0.8.9-rc1, as is `privacyRevision`, so a turn carrying the field
 * proves the route; only a server known to be older is refused (the field can't appear there).
 */
internal fun ownerTextSupported(state: ChatUiState): Boolean =
    state.gates.backendVersion?.let { BackendVersion.isCompatibleOrNewer(it, "0.8.9-rc1") } ?: true

/**
 * What one turn's answer means. [answered] is null when the request failed (400, 503, offline):
 * unavailable, with Retry. A row with its text, read against this exact turn, is the original;
 * a row without one (the server couldn't open it), or read against another revision, is
 * unavailable. No row at all is no state: the server holds no original for that id, which is how
 * a client-minted id the server never adopted answers, and that turn already shows what was typed.
 */
internal fun ownerTextStateFor(turn: Message, answered: Map<String, OwnerText>?): OwnerTextState? {
    if (answered == null) return OwnerTextState.Unavailable
    val original = answered[turn.messageId] ?: return null
    val text = original.text
    return if (text != null && original.revision == turn.privacyRevision && original.canonicalText == turn.text) {
        OwnerTextState.Loaded(text, turn.privacyRevision.orEmpty(), turn.text)
    } else {
        OwnerTextState.Unavailable
    }
}

/**
 * Whether [turn] should be asked about: never asked, asked by a fetch a newer state change
 * cancelled (still [OwnerTextState.Loading]), or answered for a revision or text it no longer has.
 * An unavailable answer waits for Retry.
 */
internal fun needsOwnerText(turn: Message, state: OwnerTextState?): Boolean = when (state) {
    null, OwnerTextState.Loading -> true
    is OwnerTextState.Loaded -> state.revision != turn.privacyRevision || state.canonicalText != turn.text
    OwnerTextState.Unavailable -> false
}

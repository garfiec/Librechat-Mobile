package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.ResumePinStore
import com.garfiec.librechat.core.model.AskUserQuestionItem
import com.garfiec.librechat.core.model.AskUserQuestionOption
import com.garfiec.librechat.core.model.AskUserQuestionRequest
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.PendingActionTypes
import com.garfiec.librechat.core.model.ToolApprovalDecisions
import com.garfiec.librechat.core.model.ToolApprovalRequest
import com.garfiec.librechat.core.model.request.ChatResumeRequest
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.feature.chat.components.PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS
import com.garfiec.librechat.feature.chat.components.PausePanelAutoAdvance
import com.garfiec.librechat.feature.chat.components.PausePanelAutoAdvance.AdvanceOrSubmit
import com.garfiec.librechat.feature.chat.components.PausePanelAutoAdvance.NextTab
import com.garfiec.librechat.feature.chat.util.AskAnswerDraft
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.PendingActionHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

/**
 * The pause panels' auto-advance: a complete pick moves the panel on by itself, after a beat, as
 * the layout it was made in says (compact: next open item or submit; wide: next tab only).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingActionAutoAdvanceTest {

    private val chatRepository = mockk<ChatRepository>()
    private val resumes = mutableListOf<ChatResumeRequest>()

    private fun TestScope.delegate(): Pair<PendingActionDelegate, MutableStateFlow<ChatUiState>> {
        coEvery { chatRepository.resumeChat(any()) } answers {
            resumes += firstArg<ChatResumeRequest>()
            Result.Success(ChatResumeResponse())
        }
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "conv-1"),
                selection = ModelSelectionState(selectedEndpoint = "agents", selectedModel = "agent_abc"),
            ),
        )
        val delegate = PendingActionDelegate(
            handle = PendingActionHandle(ChatStateHandle(flow, this)),
            chatRepository = chatRepository,
            requestBuilder = ChatRequestBuilder { flow.value },
            resumeFailureMessage = { it ?: "failed" },
            fingerprintRejectedMessage = { "rejected" },
            restoreAnswer = {},
            resumePinStore = ResumePinStore(),
        )
        return delegate to flow
    }

    private fun TestScope.justBeforeTheBeat() {
        advanceTimeBy(PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS - 1)
        runCurrent()
    }

    private fun TestScope.afterTheBeat() {
        advanceTimeBy(PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS)
        runCurrent()
    }

    // ── ask_user_question ────────────────────────────────────────────────

    private val options = listOf(
        AskUserQuestionOption(label = "Red", value = "red"),
        AskUserQuestionOption(label = "Blue", value = "blue"),
    )

    private fun askBatch(vararg ids: String, multiSelect: Set<String> = emptySet()) = PendingAction(
        actionId = "act-1",
        conversationId = "conv-1",
        payload = PendingActionPayload(
            type = PendingActionTypes.ASK_USER_QUESTION,
            questions = ids.map {
                AskUserQuestionItem(id = it, question = "Which $it?", options = options, multiSelect = it in multiSelect)
            },
        ),
    )

    private fun askSingle() = PendingAction(
        actionId = "act-1",
        conversationId = "conv-1",
        payload = PendingActionPayload(
            type = PendingActionTypes.ASK_USER_QUESTION,
            question = AskUserQuestionRequest(question = "Which?", options = options),
        ),
    )

    private fun pick(vararg values: String) = AskAnswerDraft(selectedOptions = values.toList())

    @Test
    fun `compact - a first pick moves to the next blank question after the beat, not before`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, state) = delegate()
            delegate.onPendingAction(askBatch("a", "b", "c"))
            delegate.updateAskAnswerDraft("b", pick("red"))

            delegate.pickAskOption("a", pick("blue"), AdvanceOrSubmit)
            justBeforeTheBeat()
            assertThat(state.value.askActiveQuestionId).isNull()

            afterTheBeat()
            assertThat(state.value.askActiveQuestionId).isEqualTo("c")
            assertThat(resumes).isEmpty()
        }

    @Test
    fun `compact - the pick filling the last blank question submits the batch`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, _) = delegate()
            delegate.onPendingAction(askBatch("a", "b"))
            delegate.updateAskAnswerDraft("a", pick("red"))
            delegate.selectAskQuestion("b")

            delegate.pickAskOption("b", pick("blue"), AdvanceOrSubmit)
            afterTheBeat()

            assertThat(resumes.single().answers).containsExactly("a", "red", "b", "blue")
        }

    @Test
    fun `compact - a single-question pause submits its answer on the pick`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, _) = delegate()
            delegate.onPendingAction(askSingle())

            delegate.pickAskOption("act-1", pick("red"), AdvanceOrSubmit)
            afterTheBeat()

            assertThat(resumes.single().answer).isEqualTo("red")
            assertThat(resumes.single().answers).isNull()
        }

    @Test
    fun `a re-pick inside the beat moves nothing on`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(askSingle())

        delegate.pickAskOption("act-1", pick("red"), AdvanceOrSubmit)
        delegate.pickAskOption("act-1", pick("blue"), AdvanceOrSubmit)
        afterTheBeat()
        afterTheBeat()

        // The second pick landed on an answered question, so it never moves on either.
        assertThat(resumes).isEmpty()
        assertThat(state.value.askAnswerDrafts["act-1"]).isEqualTo(pick("blue"))
    }

    @Test
    fun `a deselect inside the beat moves nothing on, in either layout`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(askBatch("a", "b"))

        for (advance in PausePanelAutoAdvance.entries) {
            delegate.pickAskOption("a", pick("red"), advance)
            delegate.pickAskOption("a", pick(), advance)
            afterTheBeat()
        }

        assertThat(state.value.askActiveQuestionId).isNull()
        assertThat(resumes).isEmpty()
    }

    @Test
    fun `a typed qualifier inside the beat moves nothing on`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, _) = delegate()
        delegate.onPendingAction(askSingle())

        delegate.pickAskOption("act-1", pick("red"), AdvanceOrSubmit)
        delegate.updateAskAnswerDraft("act-1", pick("red").copy(freeText = "but darker"))
        afterTheBeat()

        assertThat(resumes).isEmpty()
    }

    @Test
    fun `re-picking an already answered question never moves on, in either layout`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, state) = delegate()
            delegate.onPendingAction(askBatch("a", "b"))

            for (advance in PausePanelAutoAdvance.entries) {
                delegate.updateAskAnswerDraft("a", pick("red"))
                delegate.pickAskOption("a", pick("blue"), advance)
                afterTheBeat()
            }

            assertThat(state.value.askActiveQuestionId).isNull()
            assertThat(resumes).isEmpty()
        }

    @Test
    fun `a question already answered by free text or a skip never moves on`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, state) = delegate()
            delegate.onPendingAction(askBatch("a", "b"))

            delegate.updateAskAnswerDraft("a", AskAnswerDraft(freeText = "green"))
            delegate.pickAskOption("a", AskAnswerDraft(selectedOptions = listOf("red"), freeText = "green"), AdvanceOrSubmit)
            afterTheBeat()
            delegate.updateAskAnswerDraft("a", AskAnswerDraft(skipped = true))
            delegate.pickAskOption("a", pick("red"), AdvanceOrSubmit)
            afterTheBeat()

            assertThat(state.value.askActiveQuestionId).isNull()
            assertThat(resumes).isEmpty()
        }

    @Test
    fun `a multi-select pick never moves on`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(askBatch("a", "b", multiSelect = setOf("a")))

        delegate.pickAskOption("a", pick("red"), AdvanceOrSubmit)
        afterTheBeat()

        assertThat(state.value.askActiveQuestionId).isNull()
        assertThat(state.value.askAnswerDrafts["a"]).isEqualTo(pick("red"))
    }

    @Test
    fun `nothing moves on while a resolve is in flight`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(askBatch("a", "b"))

        delegate.pickAskOption("a", pick("red"), AdvanceOrSubmit)
        state.value = state.value.copy(content = state.value.content.copy(isResolvingPendingAction = true))
        afterTheBeat()

        assertThat(state.value.askActiveQuestionId).isNull()
        assertThat(resumes).isEmpty()
    }

    @Test
    fun `moving to another question inside the beat drops the move`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(askBatch("a", "b", "c"))

        delegate.pickAskOption("a", pick("red"), AdvanceOrSubmit)
        delegate.selectAskQuestion("c")
        afterTheBeat()

        assertThat(state.value.askActiveQuestionId).isEqualTo("c")
    }

    @Test
    fun `a cancel from the panel drops the move`() = runTest(UnconfinedTestDispatcher()) {
        // The layout a pick was made in left the screen (a fold or rotation inside the beat).
        val (delegate, _) = delegate()
        delegate.onPendingAction(askSingle())

        delegate.pickAskOption("act-1", pick("red"), AdvanceOrSubmit)
        delegate.cancelAutoAdvance()
        afterTheBeat()

        assertThat(resumes).isEmpty()
    }

    @Test
    fun `a pause cleared inside the beat drops the move`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(askBatch("a", "b"))

        delegate.pickAskOption("a", pick("red"), AdvanceOrSubmit)
        delegate.clear()
        // The same pause re-announced by the reconnect, its drafts restored.
        delegate.onPendingAction(askBatch("a", "b"))
        afterTheBeat()

        assertThat(state.value.askActiveQuestionId).isNull()
    }

    @Test
    fun `wide - a pick steps to the next tab in order, even an answered one`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, state) = delegate()
            delegate.onPendingAction(askBatch("a", "b", "c"))
            delegate.updateAskAnswerDraft("b", pick("red"))

            delegate.pickAskOption("a", pick("blue"), NextTab)
            afterTheBeat()

            assertThat(state.value.askActiveQuestionId).isEqualTo("b")
        }

    @Test
    fun `wide - a pick never submits, even one filling the last blank question`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, state) = delegate()
            delegate.onPendingAction(askBatch("a", "b"))
            delegate.updateAskAnswerDraft("a", pick("red"))
            delegate.selectAskQuestion("b")

            delegate.pickAskOption("b", pick("blue"), NextTab)
            afterTheBeat()

            assertThat(state.value.askActiveQuestionId).isEqualTo("b")
            assertThat(resumes).isEmpty()
        }

    // ── tool_approval ────────────────────────────────────────────────────

    private fun toolBatch(vararg ids: String) = PendingAction(
        actionId = "act-1",
        conversationId = "conv-1",
        payload = PendingActionPayload(
            type = PendingActionTypes.TOOL_APPROVAL,
            actionRequests = ids.map {
                ToolApprovalRequest(name = "tool", toolCallId = it, arguments = buildJsonObject { put("q", JsonPrimitive(it)) })
            },
        ),
    )

    private fun decide(decision: String, editedArguments: String = "", responseText: String = "") =
        ToolDecisionDraft(decision = decision, editedArguments = editedArguments, responseText = responseText)

    @Test
    fun `compact - approve and reject walk the batch, and the last decision submits it`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, state) = delegate()
            delegate.onPendingAction(toolBatch("call-1", "call-2"))

            delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.APPROVE), AdvanceOrSubmit)
            justBeforeTheBeat()
            assertThat(state.value.toolActiveCallId).isNull()
            afterTheBeat()
            assertThat(state.value.toolActiveCallId).isEqualTo("call-2")
            assertThat(resumes).isEmpty()

            delegate.pickToolDecision("call-2", decide(ToolApprovalDecisions.REJECT), AdvanceOrSubmit)
            afterTheBeat()

            assertThat(resumes.single().decisions?.map { it.decision })
                .containsExactly(ToolApprovalDecisions.APPROVE, ToolApprovalDecisions.REJECT).inOrder()
        }

    @Test
    fun `edit and respond never move on`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(toolBatch("call-1", "call-2"))

        for (advance in PausePanelAutoAdvance.entries) {
            // Both complete — valid arguments, non-blank text — and still neither moves on.
            delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.EDIT, editedArguments = "{}"), advance)
            afterTheBeat()
            delegate.updateToolDecisionDraft("call-1", ToolDecisionDraft())
            delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.RESPOND, responseText = "no"), advance)
            afterTheBeat()
            delegate.updateToolDecisionDraft("call-1", ToolDecisionDraft())
        }

        assertThat(state.value.toolActiveCallId).isNull()
        assertThat(resumes).isEmpty()
    }

    @Test
    fun `a call already decided never moves on, in either layout`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(toolBatch("call-1", "call-2"))

        for (advance in PausePanelAutoAdvance.entries) {
            delegate.updateToolDecisionDraft("call-1", decide(ToolApprovalDecisions.APPROVE))
            delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.REJECT), advance)
            afterTheBeat()
            // An Edit opens on the model's own arguments, which are valid, so it is already decided.
            delegate.updateToolDecisionDraft("call-1", decide(ToolApprovalDecisions.EDIT, editedArguments = "{}"))
            delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.APPROVE, editedArguments = "{}"), advance)
            afterTheBeat()
        }

        assertThat(state.value.toolActiveCallId).isNull()
        assertThat(resumes).isEmpty()
    }

    @Test
    fun `an approve over an incomplete edit moves on`() = runTest(UnconfinedTestDispatcher()) {
        // "Decided" means a decision the batch could submit; an edit with invalid JSON is not one.
        val (delegate, state) = delegate()
        delegate.onPendingAction(toolBatch("call-1", "call-2"))
        delegate.updateToolDecisionDraft("call-1", decide(ToolApprovalDecisions.EDIT, editedArguments = "{"))

        delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.APPROVE, editedArguments = "{"), AdvanceOrSubmit)
        afterTheBeat()

        assertThat(state.value.toolActiveCallId).isEqualTo("call-2")
    }

    @Test
    fun `a different decision inside the beat moves nothing on`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(toolBatch("call-1", "call-2"))

        delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.APPROVE), AdvanceOrSubmit)
        delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.REJECT), AdvanceOrSubmit)
        afterTheBeat()

        assertThat(state.value.toolActiveCallId).isNull()
        assertThat(state.value.toolDecisionDrafts["call-1"]?.decision).isEqualTo(ToolApprovalDecisions.REJECT)
    }

    @Test
    fun `a decision recorded some other way inside the beat moves nothing on`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, state) = delegate()
            delegate.onPendingAction(toolBatch("call-1", "call-2"))

            delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.APPROVE), AdvanceOrSubmit)
            delegate.updateToolDecisionDraft("call-1", decide(ToolApprovalDecisions.REJECT))
            afterTheBeat()

            assertThat(state.value.toolActiveCallId).isNull()
        }

    @Test
    fun `no tool call moves on while a resolve is in flight`() = runTest(UnconfinedTestDispatcher()) {
        val (delegate, state) = delegate()
        delegate.onPendingAction(toolBatch("call-1"))

        delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.APPROVE), AdvanceOrSubmit)
        state.value = state.value.copy(content = state.value.content.copy(isResolvingPendingAction = true))
        afterTheBeat()

        assertThat(resumes).isEmpty()
    }

    @Test
    fun `wide - a decision steps to the next tab, stops at the last, and never submits`() =
        runTest(UnconfinedTestDispatcher()) {
            val (delegate, state) = delegate()
            delegate.onPendingAction(toolBatch("call-1", "call-2"))

            delegate.pickToolDecision("call-1", decide(ToolApprovalDecisions.APPROVE), NextTab)
            afterTheBeat()
            assertThat(state.value.toolActiveCallId).isEqualTo("call-2")

            delegate.pickToolDecision("call-2", decide(ToolApprovalDecisions.APPROVE), NextTab)
            afterTheBeat()

            assertThat(state.value.toolActiveCallId).isEqualTo("call-2")
            assertThat(resumes).isEmpty()
        }
}

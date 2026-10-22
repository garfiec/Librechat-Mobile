package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.data.repository.ChatPayloadBuilder
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.ResumePinStore
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.PendingActionTypes
import com.garfiec.librechat.core.model.request.ChatResumeRequest
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.ModelSelectionState
import com.garfiec.librechat.feature.chat.viewmodel.PendingActionHandle
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The resume route fingerprints `ephemeralAgent` and 403s a body that differs from the one the
 * paused run was started with. A non-agent turn that carries manual skills sends the skills flag
 * even when the composer toggle is off, so the pin has to carry it too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingActionSkillsPinTest {

    private val chatRepository = mockk<ChatRepository>()

    private val state = ChatUiState(
        conversation = ConversationMetaState(conversationId = "conv-1"),
        selection = ModelSelectionState(choice = ModelChoice.Raw("openAI", "gpt-4o")),
    )

    private fun delegateWith(scope: TestScope): PendingActionDelegate {
        val flow = MutableStateFlow(state)
        return PendingActionDelegate(
            handle = PendingActionHandle(ChatStateHandle(flow, scope)),
            chatRepository = chatRepository,
            requestBuilder = ChatRequestBuilder { flow.value },
            resumeFailureMessage = { it ?: "failed" },
            fingerprintRejectedMessage = { "rejected" },
            restoreAnswer = {},
            resumePinStore = ResumePinStore(),
            pauseExpiredMessage = { "expired" },
            nowMillis = { 0L },
        )
    }

    private val toolApproval = PendingAction(
        actionId = "act-1",
        conversationId = "conv-1",
        payload = PendingActionPayload(type = PendingActionTypes.TOOL_APPROVAL),
    )

    private fun sentEphemeralAgent(endpoint: String, ephemeralAgent: EphemeralAgent?, manualSkills: List<String>?) =
        ChatPayloadBuilder.build(
            text = "hi",
            conversationId = "conv-1",
            endpoint = endpoint,
            model = "gpt-4o",
            ephemeralAgent = ephemeralAgent,
            manualSkills = manualSkills,
        ).ephemeralAgent

    private fun resumeRequest(scope: TestScope, start: PendingActionDelegate.() -> Unit): ChatResumeRequest {
        val delegate = delegateWith(scope)
        val request = slot<ChatResumeRequest>()
        coEvery { chatRepository.resumeChat(capture(request)) } returns Result.Success(ChatResumeResponse())
        delegate.start()
        delegate.onPendingAction(toolApproval)
        delegate.submitAnswer("yes")
        return request.captured
    }

    @Test
    fun `a spec with skill picks pins the flagged ephemeral agent the body carried`() =
        runTest(UnconfinedTestDispatcher()) {
            val webSearch = EphemeralAgent(webSearch = true)
            val spec = QueuedMessage(
                localId = "q-1",
                text = "hi",
                choice = ModelChoice.Raw("openAI", "gpt-4o"),
                ephemeralAgent = webSearch,
                manualSkills = listOf("pdf-tools"),
                dispatch = EndpointDispatch(endpointType = null, key = null, modelDisplayLabel = null),
            )

            val resumed = resumeRequest(this) { onTurnStarted(spec) }

            assertThat(resumed.ephemeralAgent).isEqualTo(sentEphemeralAgent("openAI", webSearch, listOf("pdf-tools")))
            assertThat(resumed.ephemeralAgent?.skills).isTrue()
        }

    @Test
    fun `a regenerate that replays picks pins the flag with the toggle off`() =
        runTest(UnconfinedTestDispatcher()) {
            val resumed = resumeRequest(this) { onTurnStarted(spec = null, manualSkills = listOf("pdf-tools")) }

            assertThat(resumed.ephemeralAgent).isEqualTo(sentEphemeralAgent("openAI", null, listOf("pdf-tools")))
            assertThat(resumed.ephemeralAgent).isEqualTo(EphemeralAgent(skills = true))
        }

    @Test
    fun `a turn without picks pins no flag`() = runTest(UnconfinedTestDispatcher()) {
        val resumed = resumeRequest(this) { onTurnStarted(spec = null) }

        assertThat(resumed.ephemeralAgent).isNull()
    }
}

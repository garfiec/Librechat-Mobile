package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.response.ChatStartResponse
import com.garfiec.librechat.core.network.api.ChatApi
import com.garfiec.librechat.core.network.sse.SseClient
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Test

/**
 * A new chat filed into a project changes that project, but no project call made the change: the
 * screens listing projects learn of it from the revision bump once its first reply is saved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryFiledChatTest {

    private val chatApi = mockk<ChatApi>()
    private val sseClient = mockk<SseClient>()
    private val signal = ProjectChangeSignal()

    private fun repository(dispatcher: TestDispatcher) = ChatRepositoryImpl(
        chatApi = chatApi,
        sseClient = sseClient,
        connectivityObserver = mockk<ConnectivityObserver>(relaxed = true),
        dispatcher = dispatcher,
        json = Json,
        projectChanges = signal,
    )

    private fun stubStream() {
        coEvery { chatApi.startChat(any(), any<JsonObject>()) } returns ChatStartResponse(conversationId = "c1")
        every { sseClient.connect(any(), any(), any()) } returns flowOf(StreamEvent.Final())
    }

    @Test
    fun `a filed chat's first reply bumps the revision`() = runTest {
        stubStream()
        repository(UnconfinedTestDispatcher(testScheduler))
            .startChat(text = "hi", conversationId = null, endpoint = "agents", model = "m", chatProjectId = "p1")
            .toList()

        assertThat(signal.revision.value).isEqualTo(1)
    }

    @Test
    fun `a reply that finishes on the resumed stream still counts`() = runTest {
        stubStream()
        // The reply is still running when the landing that sent it stops collecting at Created; the
        // chat screen resumes the stream and sees it finish.
        every { sseClient.connect(any(), false, any()) } returns flow { awaitCancellation() }
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        repo.startChat(text = "hi", conversationId = null, endpoint = "agents", model = "m", chatProjectId = "p1")
            .take(1)
            .toList()
        assertThat(signal.revision.value).isEqualTo(0)
        repo.resumeStream("c1").toList()

        assertThat(signal.revision.value).isEqualTo(1)
    }

    @Test
    fun `an unfiled chat and later turns change no project`() = runTest {
        stubStream()
        val repo = repository(UnconfinedTestDispatcher(testScheduler))

        repo.startChat(text = "hi", conversationId = null, endpoint = "agents", model = "m").toList()
        repo.resumeStream("c1").toList()

        assertThat(signal.revision.value).isEqualTo(0)
    }
}

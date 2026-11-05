package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.PullRequestEntry
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequest
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.PullRequestHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PullRequestDelegateTest {

    private val running =
        ConversationPullRequest(number = 482, title = "Fix crash", state = "open", mergeable = "clean", checks = "running")
    private val merged = running.copy(state = "merged", checks = "passing")

    private inner class FakeRepository(var answer: () -> ConversationPullRequest?) : PullRequestRepository {
        override val entries = MutableStateFlow<Map<String, PullRequestEntry>>(emptyMap())
        val asked = mutableListOf<String>()
        var fail = false
        lateinit var clock: Clock

        override suspend fun refresh(conversationId: String) {
            asked += conversationId
            val previous = entries.value[conversationId]
            entries.update {
                it + (
                    conversationId to if (fail) {
                        PullRequestEntry(previous?.pullRequest, previous?.answered == true, previous?.answered == true, clock.now())
                    } else {
                        PullRequestEntry(answer(), answered = true, refreshFailed = false, fetchedAt = clock.now())
                    }
                    )
            }
        }

        override suspend fun refreshBatch(conversationIds: List<String>, maxConcurrent: Int?) = Unit

        override val revision = MutableStateFlow(0L)

        override fun invalidate() {
            entries.update { all -> all.mapValues { it.value.copy(fetchedAt = Instant.DISTANT_PAST) } }
            revision.update { it + 1 }
        }
    }

    private class Harness(
        val scope: TestScope,
        val state: MutableStateFlow<ChatUiState>,
        val delegate: PullRequestDelegate,
    )

    private fun harness(
        repository: FakeRepository,
        conversation: Conversation? = Conversation(conversationId = "c1", codeEnvironmentMode = "attached"),
        config: StartupConfig = StartupConfig(pullRequestsEnabled = true),
        version: String = "0.8.9-rc1",
        initial: ChatUiState = ChatUiState(conversation = ConversationMetaState(conversationId = "c1")),
    ): Harness {
        val scope = TestScope(StandardTestDispatcher())
        val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(scope.testScheduler.currentTime)
        }
        repository.clock = clock
        val state = MutableStateFlow(initial)
        val configRepository = mockk<ConfigRepository> {
            every { startupConfig } returns MutableStateFlow(config)
            every { detectedBackendVersion } returns MutableStateFlow(version)
        }
        val conversations = mockk<ConversationRepository> {
            every { observeConversation(any()) } returns flowOf(conversation)
        }
        val delegate = PullRequestDelegate(
            PullRequestHandle(ChatStateHandle(state, scope)),
            configRepository,
            conversations,
            repository,
            clock,
        )
        delegate.start()
        scope.runCurrent()
        return Harness(scope, state, delegate)
    }

    @Test
    fun `a code chat is looked up and its pull request shows`() {
        val repository = FakeRepository { running }
        val h = harness(repository)
        try {
            assertThat(repository.asked).containsExactly("c1")
            assertThat(h.state.value.pullRequest.pullRequest).isEqualTo(running)
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun `an ordinary chat, an unknown one and a server without the feature are never asked`() {
        for ((conversation, config) in listOf(
            Conversation(conversationId = "c1", codeDecisionListed = true) to StartupConfig(pullRequestsEnabled = true),
            Conversation(conversationId = "c1") to StartupConfig(pullRequestsEnabled = true),
            Conversation(conversationId = "c1", codeEnvironmentMode = "attached") to StartupConfig(),
        )) {
            val repository = FakeRepository { running }
            val h = harness(repository, conversation = conversation, config = config)
            try {
                assertThat(repository.asked).isEmpty()
                assertThat(h.state.value.pullRequest.pullRequest).isNull()
            } finally {
                h.scope.cancel()
            }
        }
    }

    @Test
    fun `an older server is never asked`() {
        val repository = FakeRepository { running }
        val h = harness(repository, version = "0.8.8")
        try {
            assertThat(repository.asked).isEmpty()
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun `a first turn still streaming is not asked`() {
        val repository = FakeRepository { running }
        val streaming = ChatUiState(
            conversation = ConversationMetaState(conversationId = "c1"),
            content = MessagesState(isStreaming = true, messages = listOf(Message(messageId = "m1", conversationId = "c1"))),
        )
        val h = harness(repository, initial = streaming)
        try {
            assertThat(repository.asked).isEmpty()
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun `running checks poll every 20 seconds, only while in front, and stop once merged`() {
        var answer = running
        val repository = FakeRepository { answer }
        val h = harness(repository)
        try {
            h.scope.advanceTimeBy(20_001)
            assertThat(repository.asked).hasSize(2)

            h.delegate.onForeground(false)
            h.scope.advanceTimeBy(60_000)
            assertThat(repository.asked).hasSize(2)

            // Back after a minute: the answer is stale, so it is asked at once.
            h.delegate.onForeground(true)
            h.scope.runCurrent()
            assertThat(repository.asked).hasSize(3)

            answer = merged
            h.scope.advanceTimeBy(20_001)
            assertThat(repository.asked).hasSize(4)
            h.scope.advanceTimeBy(120_000)
            assertThat(repository.asked).hasSize(4)
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun `a change of credential asks again at once instead of waiting out the interval`() {
        val repository = FakeRepository { running.copy(checks = "passing") }
        val h = harness(repository)
        try {
            assertThat(repository.asked).hasSize(1)
            // Passing checks on a clean open PR poll every minute; a connect must not wait that long.
            h.scope.advanceTimeBy(5_000)
            repository.invalidate()
            h.scope.runCurrent()
            assertThat(repository.asked).hasSize(2)
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun `the composer chip spells a state out until it is seen, and again when the checks change`() {
        var answer = running
        val repository = FakeRepository { answer }
        val h = harness(repository)
        try {
            assertThat(h.state.value.pullRequest.unseen).isTrue()
            h.delegate.markSeen()
            h.scope.runCurrent()
            assertThat(h.state.value.pullRequest.unseen).isFalse()

            answer = running.copy(checks = "failing")
            h.scope.advanceTimeBy(20_001)
            assertThat(h.state.value.pullRequest.unseen).isTrue()
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun `opening the sheet counts as seeing it, and a chat switch closes it`() {
        val repository = FakeRepository { running }
        val h = harness(repository)
        try {
            h.delegate.openSheet()
            h.scope.runCurrent()
            assertThat(h.state.value.pullRequest.sheetOpen).isTrue()
            assertThat(h.state.value.pullRequest.unseen).isFalse()

            h.state.update { it.copy(conversation = ConversationMetaState(conversationId = "c2")) }
            h.scope.runCurrent()
            assertThat(h.state.value.pullRequest.sheetOpen).isFalse()
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun `nothing to open while no pull request is known`() {
        val repository = FakeRepository { null }
        val h = harness(repository)
        try {
            h.delegate.openSheet()
            h.scope.runCurrent()
            assertThat(h.state.value.pullRequest.sheetOpen).isFalse()
            assertThat(h.state.value.pullRequest.unseen).isFalse()
        } finally {
            h.scope.cancel()
        }
    }

    @Test
    fun `a refresh that fails keeps the value and says so`() {
        val repository = FakeRepository { running }
        val h = harness(repository)
        try {
            repository.fail = true
            h.scope.advanceTimeBy(20_001)
            assertThat(h.state.value.pullRequest.pullRequest).isEqualTo(running)
            assertThat(h.state.value.pullRequest.refreshFailed).isTrue()
        } finally {
            h.scope.cancel()
        }
    }
}

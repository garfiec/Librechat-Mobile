package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PullRequestEntry
import com.garfiec.librechat.core.data.repository.PullRequestRepository
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.pullrequest.PULL_REQUEST_BATCH_MAX
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerPullRequestsTest {

    private class RecordingRepository : PullRequestRepository {
        val batches = mutableListOf<List<String>>()
        override val entries: Flow<Map<String, PullRequestEntry>> = MutableStateFlow(emptyMap())
        override suspend fun refresh(conversationId: String) = Unit
        override suspend fun refreshBatch(conversationIds: List<String>, maxConcurrent: Int?) {
            batches += conversationIds
        }
    }

    @Test
    fun everyEligibleRowIsAskedEvenPastOneBatch() {
        val scope = TestScope(StandardTestDispatcher())
        val config = mockk<ConfigRepository> {
            every { startupConfig } returns MutableStateFlow(
                StartupConfig(pullRequestsEnabled = true, pullRequestsBatchVersion = 1),
            )
            every { detectedBackendVersion } returns MutableStateFlow("0.8.9-rc1")
        }
        val repository = RecordingRepository()
        val rows = (1..PULL_REQUEST_BATCH_MAX + 20).map { Conversation(conversationId = "c$it") }

        DrawerPullRequests(config, repository, scope).start(
            visible = MutableStateFlow(true),
            shown = MutableStateFlow(rows),
            running = MutableStateFlow(emptySet()),
        )
        scope.runCurrent()

        // Rows past the first chunk are handed over too; the repository chunks them.
        assertThat(repository.batches.flatten()).containsExactlyElementsIn(rows.map { it.conversationId })
        scope.cancel()
    }
}

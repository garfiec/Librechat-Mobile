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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DrawerPullRequestsTest {

    private class RecordingRepository(private val clock: Clock) : PullRequestRepository {
        val batches = mutableListOf<List<String>>()
        var hold: CompletableDeferred<Unit>? = null
        var cancelled = 0
        override val entries = MutableStateFlow<Map<String, PullRequestEntry>>(emptyMap())
        override val revision = MutableStateFlow(0L)
        override suspend fun refresh(conversationId: String) = Unit
        override suspend fun refreshBatch(conversationIds: List<String>, maxConcurrent: Int?) {
            batches += conversationIds
            try {
                hold?.await()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
            entries.update { all ->
                all + conversationIds.associateWith {
                    PullRequestEntry(null, answered = true, refreshFailed = false, fetchedAt = clock.now())
                }
            }
        }

        override fun invalidate() {
            entries.update { all -> all.mapValues { it.value.copy(fetchedAt = Instant.DISTANT_PAST) } }
            revision.update { it + 1 }
        }
    }

    private class Harness(
        val scope: TestScope,
        val repository: RecordingRepository,
        val drawer: DrawerPullRequests,
        val visible: MutableStateFlow<Boolean>,
    )

    private fun harness(rows: List<Conversation>, visible: Boolean = true): Harness {
        val scope = TestScope(StandardTestDispatcher())
        val clock = object : Clock {
            override fun now() = Instant.fromEpochMilliseconds(1_000_000 + scope.testScheduler.currentTime)
        }
        val config = mockk<ConfigRepository> {
            every { startupConfig } returns MutableStateFlow(
                StartupConfig(pullRequestsEnabled = true, pullRequestsBatchVersion = 1),
            )
            every { detectedBackendVersion } returns MutableStateFlow("0.8.9-rc1")
        }
        val repository = RecordingRepository(clock)
        val open = MutableStateFlow(visible)
        val drawer = DrawerPullRequests(config, repository, scope, clock)
        drawer.start(visible = open, shown = MutableStateFlow(rows), running = MutableStateFlow(emptySet()))
        scope.runCurrent()
        return Harness(scope, repository, drawer, open)
    }

    private val two = listOf(Conversation(conversationId = "a"), Conversation(conversationId = "b"))

    @Test
    fun everyEligibleRowIsAskedEvenPastOneBatch() {
        val rows = (1..PULL_REQUEST_BATCH_MAX + 20).map { Conversation(conversationId = "c$it") }
        val h = harness(rows)
        // Rows past the first chunk are handed over too; the repository chunks them.
        assertThat(h.repository.batches.flatten()).containsExactlyElementsIn(rows.map { it.conversationId })
        h.scope.cancel()
    }

    @Test
    fun reopeningAsksOnlyRowsAMinuteOld() {
        val h = harness(two)
        assertThat(h.repository.batches).hasSize(1)

        h.visible.value = false
        h.scope.advanceTimeBy(30_000)
        h.visible.value = true
        h.scope.runCurrent()
        assertThat(h.repository.batches).hasSize(1)

        h.visible.value = false
        h.scope.advanceTimeBy(31_000)
        h.visible.value = true
        h.scope.runCurrent()
        assertThat(h.repository.batches).hasSize(2)
        h.scope.cancel()
    }

    @Test
    fun aPullAsksAgainPastAShortFloorAndRepeatedPullsCostNothing() {
        val h = harness(two)
        h.drawer.refreshNow()
        h.scope.runCurrent()
        // Just answered: under the floor.
        assertThat(h.repository.batches).hasSize(1)

        h.scope.advanceTimeBy(16_000)
        h.drawer.refreshNow()
        h.scope.runCurrent()
        assertThat(h.repository.batches).hasSize(2)

        h.drawer.refreshNow()
        h.scope.runCurrent()
        assertThat(h.repository.batches).hasSize(2)
        h.scope.cancel()
    }

    @Test
    fun aChangeOfCredentialAsksEveryShownRowAgain() {
        val h = harness(two)
        h.repository.invalidate()
        h.scope.runCurrent()
        assertThat(h.repository.batches).hasSize(2)
        assertThat(h.repository.batches.last()).containsExactly("a", "b")
        h.scope.cancel()
    }

    @Test
    fun aChangeOfCredentialCancelsTheLookupsStillOutAndAsksTheirRowsAgain() {
        val h = harness(two, visible = false)
        h.repository.hold = CompletableDeferred()
        h.visible.value = true
        h.scope.runCurrent()
        assertThat(h.repository.batches).hasSize(1)

        h.repository.invalidate()
        h.scope.runCurrent()
        assertThat(h.repository.cancelled).isEqualTo(1)
        // Not left out as still in flight: the new credential's lookup goes out at once.
        assertThat(h.repository.batches).hasSize(2)
        assertThat(h.repository.batches.last()).containsExactly("a", "b")
        h.scope.cancel()
    }

    @Test
    fun aClosedDrawerAsksNothingUntilItOpens() {
        val h = harness(two, visible = false)
        h.repository.invalidate()
        h.drawer.refreshNow()
        h.scope.runCurrent()
        assertThat(h.repository.batches).isEmpty()

        h.visible.value = true
        h.scope.runCurrent()
        assertThat(h.repository.batches).hasSize(1)
        h.scope.cancel()
    }

    @Test
    fun anAccountSwitchCancelsTheLookupsStillOut() {
        val g = harness(two, visible = false)
        g.repository.hold = CompletableDeferred()
        g.visible.value = true
        g.scope.runCurrent()
        assertThat(g.repository.batches).hasSize(1)

        g.drawer.onAccountSwitched()
        g.scope.runCurrent()
        assertThat(g.repository.cancelled).isEqualTo(1)
        g.scope.cancel()
    }
}

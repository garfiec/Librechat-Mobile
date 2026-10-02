package com.garfiec.librechat.core.data.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.common.lifecycle.ForegroundSignal
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateCheckCoordinatorTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private class FakeRepository(override val isSupported: Boolean = true) : AppUpdateRepository {
        val checks = Channel<Unit>(Channel.UNLIMITED)
        override val checkState: StateFlow<UpdateCheckState> = MutableStateFlow(UpdateCheckState.Idle)
        override val autoCheckEnabled: Flow<Boolean> = flowOf(false)
        override val pendingUpdate: Flow<PendingUpdate?> = flowOf(null)
        override suspend fun setAutoCheckEnabled(enabled: Boolean) = Unit
        override suspend fun check(): UpdateCheckState {
            checks.send(Unit)
            return UpdateCheckState.UpToDate
        }
        override suspend fun markNotified(tag: String) = Unit
    }

    private val now = 100 * UpdateCheckCoordinator.INTERVAL_MILLIS
    private val store = UpdateCheckStore(
        PreferenceDataStoreFactory.create(scope = scope) { File(tmpFolder.root, "c.preferences_pb") },
    )
    private val foreground = ForegroundSignal()

    private fun start(repo: AppUpdateRepository) =
        UpdateCheckCoordinator(repo, store, foreground, scope) { now }

    private suspend fun FakeRepository.awaitCheck() = withTimeout(5_000) { checks.receive() }

    // Long enough for DataStore's IO round trip; a check that was going to run has run by then.
    private suspend fun FakeRepository.assertNoCheck() {
        delay(500)
        assertThat(checks.tryReceive().isSuccess).isFalse()
    }

    @Test
    fun checksOnForegroundWhenEnabledAndNeverChecked() = runBlocking {
        val repo = FakeRepository()
        store.setAutoCheckEnabled(true)
        start(repo)
        repo.assertNoCheck()

        foreground.set(true)
        repo.awaitCheck()
    }

    @Test
    fun disabledNeverChecks() = runBlocking {
        val repo = FakeRepository()
        start(repo)
        foreground.set(true)
        repo.assertNoCheck()
    }

    @Test
    fun skipsWithinTheDailyWindow() = runBlocking {
        val repo = FakeRepository()
        store.setAutoCheckEnabled(true)
        store.setLastCheckedAt(now - UpdateCheckCoordinator.INTERVAL_MILLIS + 1)
        start(repo)
        foreground.set(true)
        repo.assertNoCheck()
    }

    @Test
    fun checksOnceTheWindowHasPassed() = runBlocking {
        val repo = FakeRepository()
        store.setAutoCheckEnabled(true)
        store.setLastCheckedAt(now - UpdateCheckCoordinator.INTERVAL_MILLIS)
        start(repo)
        foreground.set(true)
        repo.awaitCheck()
    }

    @Test
    fun turningTheToggleOnWhileForegroundedChecksImmediately() = runBlocking {
        val repo = FakeRepository()
        start(repo)
        foreground.set(true)
        repo.assertNoCheck()

        store.setAutoCheckEnabled(true)
        repo.awaitCheck()
    }

    @Test
    fun unsupportedPlatformNeverChecks() = runBlocking {
        val repo = FakeRepository(isSupported = false)
        store.setAutoCheckEnabled(true)
        start(repo)
        foreground.set(true)
        repo.assertNoCheck()
    }
}

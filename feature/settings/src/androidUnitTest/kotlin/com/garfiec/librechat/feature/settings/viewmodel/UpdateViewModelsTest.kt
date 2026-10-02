package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.data.update.AppInstallSource
import com.garfiec.librechat.core.data.update.AppUpdateRepository
import com.garfiec.librechat.core.data.update.InstallChannel
import com.garfiec.librechat.core.data.update.PendingUpdate
import com.garfiec.librechat.core.data.update.UpdateCheckState
import com.garfiec.librechat.core.model.AppRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateViewModelsTest {

    private class FakeRepository(
        initial: UpdateCheckState = UpdateCheckState.Idle,
        private val result: UpdateCheckState = UpdateCheckState.UpToDate,
    ) : AppUpdateRepository {
        val state = MutableStateFlow(initial)
        val notified = mutableListOf<String>()
        var checks = 0
        override val isSupported = true
        override val checkState: StateFlow<UpdateCheckState> = state
        override val autoCheckEnabled: Flow<Boolean> = MutableStateFlow(false)
        override val pendingUpdate: Flow<PendingUpdate?> = MutableStateFlow(null)
        override suspend fun setAutoCheckEnabled(enabled: Boolean) = Unit
        override suspend fun check(): UpdateCheckState {
            checks++
            state.value = result
            return result
        }
        override suspend fun markNotified(tag: String) {
            notified += tag
        }
    }

    private val installSource = object : AppInstallSource {
        override val channel = InstallChannel.OBTAINIUM
        override fun openInstaller() = true
    }

    private val appInfo = object : AppInfo {
        override val versionName = "2026.09.0"
        override val versionCode = 20260900L
        override val gitSha = "unknown"
    }

    private fun release(tag: String) = AppRelease(
        tag = tag,
        version = tag.removePrefix("v"),
        publishedAt = null,
        htmlUrl = "https://example.test/$tag",
        apkUrl = null,
        highlights = "notes",
        fullChangelog = null,
    )

    private val available = UpdateCheckState.Available(listOf(release("v2026.10.0"), release("v2026.09.1")))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun settingsMarksAnAvailableResultNotified() = runTest {
        val repo = FakeRepository(result = available)
        val viewModel = UpdateCheckViewModel(repo)
        viewModel.check()
        assertEquals(listOf("v2026.10.0"), repo.notified)
    }

    @Test
    fun settingsUpToDateNotifiesNothing() = runTest {
        val repo = FakeRepository(result = UpdateCheckState.UpToDate)
        UpdateCheckViewModel(repo).check()
        assertEquals(emptyList(), repo.notified)
    }

    @Test
    fun openingSettingsDoesNotConsumeADailyResult() = runTest {
        // A daily check already found the release; the chat banner is still owed to the user.
        val repo = FakeRepository(initial = available)
        val viewModel = UpdateCheckViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect {} }
        assertEquals(emptyList(), repo.notified)
    }

    @Test
    fun whatsNewFetchesWhenNothingIsLoaded() = runTest {
        // The chat banner after a restart: only the persisted tag, never the notes.
        val repo = FakeRepository(result = available)
        val viewModel = WhatsNewViewModel(repo, installSource, appInfo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect {} }

        assertEquals(1, repo.checks)
        assertEquals(WhatsNewUiState.Content(available.releases), viewModel.uiState.value)
        assertEquals(listOf("v2026.10.0"), repo.notified)
    }

    @Test
    fun whatsNewReusesAResultSettingsAlreadyFetched() = runTest {
        val repo = FakeRepository(initial = available)
        val viewModel = WhatsNewViewModel(repo, installSource, appInfo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect {} }

        assertEquals(0, repo.checks)
        assertEquals(WhatsNewUiState.Content(available.releases), viewModel.uiState.value)
        assertEquals(InstallChannel.OBTAINIUM, viewModel.installChannel)
    }

    @Test
    fun whatsNewFailureIsRetryable() = runTest {
        val repo = FakeRepository(result = UpdateCheckState.Failed(null))
        val viewModel = WhatsNewViewModel(repo, installSource, appInfo)
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.uiState.collect {} }
        assertEquals(WhatsNewUiState.Error, viewModel.uiState.value)

        viewModel.retry()
        assertEquals(2, repo.checks)
    }
}

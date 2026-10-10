package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.update.AppInstallSource
import com.garfiec.librechat.core.data.update.AppUpdateRepository
import com.garfiec.librechat.core.data.update.InstallChannel
import com.garfiec.librechat.core.data.update.PendingUpdate
import com.garfiec.librechat.core.data.update.ReleasePage
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
        override val installedTag: String? = "v2026.09.0",
        var installedResult: Result<AppRelease?> = Result.Success(null),
        val pages: MutableMap<Int, Result<ReleasePage>> =
        mutableMapOf(1 to Result.Success(ReleasePage(emptyList(), false))),
    ) : AppUpdateRepository {
        val state = MutableStateFlow(initial)
        val notified = mutableListOf<String>()
        var checks = 0
        var notesFetches = 0
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
        val pagesRequested = mutableListOf<Int>()
        override suspend fun installedRelease(): Result<AppRelease?> {
            notesFetches++
            return installedResult
        }
        override suspend fun olderReleases(page: Int): Result<ReleasePage> {
            pagesRequested += page
            return pages.getValue(page)
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

    @Test
    fun releaseNotesShowTheInstalledRelease() = runTest {
        val notes = release("v2026.09.0")
        val repo = FakeRepository(installedResult = Result.Success(notes))
        val viewModel = ReleaseNotesViewModel(repo, appInfo)

        assertEquals(InstalledNotes.Content(notes), viewModel.uiState.value.installed)
        assertEquals(0, repo.checks)
        assertEquals(emptyList(), repo.notified)
    }

    @Test
    fun releaseNotesForAnUnpublishedBuildAreNotFound() = runTest {
        val viewModel = ReleaseNotesViewModel(FakeRepository(), appInfo)
        assertEquals(InstalledNotes.NotFound, viewModel.uiState.value.installed)
    }

    @Test
    fun releaseNotesFailureIsRetryable() = runTest {
        val repo = FakeRepository(installedResult = Result.Error())
        val viewModel = ReleaseNotesViewModel(repo, appInfo)
        assertEquals(InstalledNotes.Error, viewModel.uiState.value.installed)

        val notes = release("v2026.09.0")
        repo.installedResult = Result.Success(notes)
        viewModel.retryInstalled()
        assertEquals(2, repo.notesFetches)
        assertEquals(InstalledNotes.Content(notes), viewModel.uiState.value.installed)
    }

    @Test
    fun olderReleasesPageUntilTheListEnds() = runTest {
        val repo = FakeRepository(
            pages = mutableMapOf(
                1 to Result.Success(ReleasePage(listOf(release("v2026.08.4")), hasMore = true)),
                2 to Result.Success(ReleasePage(listOf(release("v2026.08.3")), hasMore = false)),
            ),
        )
        val viewModel = ReleaseNotesViewModel(repo, appInfo)
        assertEquals(OlderStatus.MORE, viewModel.uiState.value.olderStatus)

        viewModel.loadOlder()
        assertEquals(listOf("v2026.08.4", "v2026.08.3"), viewModel.uiState.value.older.map { it.tag })
        assertEquals(OlderStatus.END, viewModel.uiState.value.olderStatus)

        viewModel.loadOlder()
        assertEquals(listOf(1, 2), repo.pagesRequested)
    }

    @Test
    fun aPageWithNothingOlderReadsOnToTheNext() = runTest {
        // The installed build is far behind: GitHub's first page is all newer releases.
        val repo = FakeRepository(
            pages = mutableMapOf(
                1 to Result.Success(ReleasePage(emptyList(), hasMore = true)),
                2 to Result.Success(ReleasePage(listOf(release("v2025.01.0")), hasMore = false)),
            ),
        )
        val viewModel = ReleaseNotesViewModel(repo, appInfo)
        assertEquals(listOf(1, 2), repo.pagesRequested)
        assertEquals(listOf("v2025.01.0"), viewModel.uiState.value.older.map { it.tag })
    }

    @Test
    fun aFailedOlderPageRetriesTheSamePage() = runTest {
        val repo = FakeRepository(pages = mutableMapOf(1 to Result.Error()))
        val viewModel = ReleaseNotesViewModel(repo, appInfo)
        assertEquals(OlderStatus.ERROR, viewModel.uiState.value.olderStatus)

        repo.pages[1] = Result.Success(ReleasePage(listOf(release("v2026.08.4")), hasMore = false))
        viewModel.loadOlder()
        assertEquals(listOf(1, 1), repo.pagesRequested)
        assertEquals(OlderStatus.END, viewModel.uiState.value.olderStatus)
    }

    @Test
    fun releaseNotesRowFollowsTheInstalledTagNotUpdateSupport() = runTest {
        assertEquals(true, UpdateCheckViewModel(FakeRepository()).releaseNotesAvailable)
        assertEquals(false, UpdateCheckViewModel(FakeRepository(installedTag = null)).releaseNotesAvailable)
    }
}

package com.garfiec.librechat.core.data.update

import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.AppVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.AppRelease
import com.garfiec.librechat.core.network.api.GitHubReleasesApi
import com.garfiec.librechat.core.network.api.dto.GitHubRelease
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

class AppUpdateRepositoryImpl(
    override val isSupported: Boolean,
    private val api: GitHubReleasesApi,
    private val store: UpdateCheckStore,
    appInfo: AppInfo,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AppUpdateRepository {

    private val installed: AppVersion? = AppVersion.parse(appInfo.versionName)
    private val checkMutex = Mutex()

    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    override val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    override val autoCheckEnabled: Flow<Boolean> = store.autoCheckEnabled

    override val pendingUpdate: Flow<PendingUpdate?> = combine(
        store.autoCheckEnabled,
        store.availableTag,
        store.notifiedTag,
    ) { enabled, available, notified ->
        if (!enabled || available == null || available == notified) return@combine null
        val version = AppVersion.parse(available) ?: return@combine null
        if (installed == null || version <= installed) return@combine null
        PendingUpdate(tag = available, version = version.toString())
    }.distinctUntilChanged()

    override suspend fun setAutoCheckEnabled(enabled: Boolean) = store.setAutoCheckEnabled(enabled)

    override suspend fun check(): UpdateCheckState {
        if (!isSupported) return UpdateCheckState.Idle
        return checkMutex.withLock {
            val previous = _checkState.value
            _checkState.value = UpdateCheckState.Checking
            try {
                val result = safeApiCall { api.listReleases() }
                // Stamped on failure too, so an outage costs one request a day rather than one per resume.
                store.setLastCheckedAt(now())
                val state = when (result) {
                    is Result.Success -> {
                        val newer = newerThanInstalled(result.data)
                        store.setAvailableTag(newer.firstOrNull()?.tag)
                        if (newer.isEmpty()) UpdateCheckState.UpToDate else UpdateCheckState.Available(newer)
                    }
                    is Result.Error -> UpdateCheckState.Failed(result.message)
                    Result.Loading -> UpdateCheckState.Idle
                }
                _checkState.value = state
                state
            } catch (e: CancellationException) {
                // Callers run this in their own viewModelScope; a stuck Checking would disable
                // the Settings row (and its re-check guard) until process death.
                _checkState.value = previous
                throw e
            }
        }
    }

    override suspend fun markNotified(tag: String) = store.setNotifiedTag(tag)

    private fun newerThanInstalled(releases: List<GitHubRelease>): List<AppRelease> {
        val current = installed ?: return emptyList()
        return releases
            .asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { release -> AppVersion.parse(release.tagName)?.let { it to release } }
            .filter { (version, _) -> version > current }
            .sortedByDescending { (version, _) -> version }
            .map { (version, release) -> release.toAppRelease(version) }
            .toList()
    }

    private fun GitHubRelease.toAppRelease(version: AppVersion): AppRelease {
        val notes = ReleaseNotesParser.parse(body)
        return AppRelease(
            tag = tagName,
            version = version.toString(),
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkUrl = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.browserDownloadUrl,
            highlights = notes.highlights,
            fullChangelog = notes.fullChangelog,
        )
    }
}

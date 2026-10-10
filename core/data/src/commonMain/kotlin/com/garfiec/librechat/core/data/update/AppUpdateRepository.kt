package com.garfiec.librechat.core.data.update

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.AppRelease
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState

    /** [releases] holds every stable release newer than the installed build, newest first. */
    data class Available(val releases: List<AppRelease>) : UpdateCheckState {
        val latest: AppRelease get() = releases.first()
    }

    data class Failed(val message: String?) : UpdateCheckState
}

/** One page of [AppUpdateRepository.olderReleases]. [hasMore] is false once GitHub's list ran out. */
data class ReleasePage(val releases: List<AppRelease>, val hasMore: Boolean)

/** The release the chat banner should announce. */
data class PendingUpdate(val tag: String, val version: String)

/**
 * Checks GitHub Releases for a newer build of the app, and reads the installed build's notes.
 *
 * The update check is Android only: the APK on GitHub, Obtainium and F-Droid is the same binary, so
 * it is a runtime capability rather than a flavor. iOS updates through the App Store, so
 * [isSupported] is false there and every update surface hides itself. [installedRelease] and
 * [olderReleases] are not an update check and work on both, since the IPA ships on the same release tags.
 */
interface AppUpdateRepository {
    val isSupported: Boolean

    /** The installed build's release tag, e.g. `v2026.10.1`; null when its version is not a release's. */
    val installedTag: String?

    val checkState: StateFlow<UpdateCheckState>

    val autoCheckEnabled: Flow<Boolean>

    /**
     * The release to announce in chat: the daily check is on, it found a release newer than the
     * installed build, and the user has not been told about that tag yet. Null otherwise — which
     * also clears it once the user installs the update.
     */
    val pendingUpdate: Flow<PendingUpdate?>

    suspend fun setAutoCheckEnabled(enabled: Boolean)

    /** Fetches the release list now, regardless of the daily throttle. */
    suspend fun check(): UpdateCheckState

    /** Records that the user has seen [tag], so the chat banner does not announce it again. */
    suspend fun markNotified(tag: String)

    /**
     * Fetches the notes of [installedTag]'s release. Success with null when GitHub has no such
     * release — a build from an untagged commit, or one whose release is not published yet.
     */
    suspend fun installedRelease(): Result<AppRelease?>

    /**
     * Stable releases older than the installed build, newest first, from GitHub's [page] of its
     * release list (counting from 1). A page can come back empty yet still have more after it.
     */
    suspend fun olderReleases(page: Int): Result<ReleasePage>
}

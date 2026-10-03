package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.update.AppUpdateRepository
import com.garfiec.librechat.core.model.AppRelease
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InstalledNotes {
    data object Loading : InstalledNotes
    data object NotFound : InstalledNotes
    data object Error : InstalledNotes
    data class Content(val release: AppRelease) : InstalledNotes
}

enum class OlderStatus { LOADING, MORE, END, ERROR }

data class ReleaseNotesUiState(
    val installed: InstalledNotes = InstalledNotes.Loading,
    /** Releases before the installed build, newest first. */
    val older: List<AppRelease> = emptyList(),
    val olderStatus: OlderStatus = OlderStatus.LOADING,
)

/**
 * Release notes for the installed build and the releases before it. Fetched only while the screen
 * is open, never in the background.
 */
class ReleaseNotesViewModel(
    private val repository: AppUpdateRepository,
    appInfo: AppInfo,
) : ViewModel() {

    val installedVersion: String = appInfo.versionName

    private val _uiState = MutableStateFlow(ReleaseNotesUiState())
    val uiState: StateFlow<ReleaseNotesUiState> = _uiState.asStateFlow()

    private var installedJob: Job? = null
    private var olderJob: Job? = null

    /** The next GitHub list page to read. */
    private var nextPage = 1

    init {
        retryInstalled()
        loadOlder()
    }

    fun retryInstalled() {
        if (installedJob?.isActive == true) return
        _uiState.update { it.copy(installed = InstalledNotes.Loading) }
        installedJob = viewModelScope.launch {
            val installed = when (val result = repository.installedRelease()) {
                is Result.Success -> result.data?.let(InstalledNotes::Content) ?: InstalledNotes.NotFound
                is Result.Error, Result.Loading -> InstalledNotes.Error
            }
            _uiState.update { it.copy(installed = installed) }
        }
    }

    /** Reads the next page; also the retry after a failed one. */
    fun loadOlder() {
        if (olderJob?.isActive == true || _uiState.value.olderStatus == OlderStatus.END) return
        _uiState.update { it.copy(olderStatus = OlderStatus.LOADING) }
        olderJob = viewModelScope.launch {
            when (val result = repository.olderReleases(nextPage)) {
                is Result.Success -> {
                    nextPage++
                    val page = result.data
                    _uiState.update {
                        it.copy(
                            older = it.older + page.releases,
                            olderStatus = if (page.hasMore) OlderStatus.MORE else OlderStatus.END,
                        )
                    }
                    // A page with nothing older on it (the installed build is far behind) would
                    // otherwise leave a bare button that loads nothing visible.
                    if (page.hasMore && page.releases.isEmpty()) {
                        olderJob = null
                        loadOlder()
                    }
                }
                is Result.Error, Result.Loading -> _uiState.update { it.copy(olderStatus = OlderStatus.ERROR) }
            }
        }
    }
}

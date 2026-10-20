package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ProjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ProjectLandingState(val name: String, val deleted: Boolean = false)

/**
 * Keeps a project's new-chat screen in step with its project. The route carries the name the screen
 * was opened with, so a rename made elsewhere is picked up here; a project deleted elsewhere closes
 * the screen rather than leaving one that says "In <project>" and would file the chat into nothing.
 */
class ProjectLandingViewModel(
    private val projectId: String,
    initialName: String,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectLandingState(initialName))
    val state: StateFlow<ProjectLandingState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            projectRepository.revision.drop(1).collect { reload() }
        }
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = projectRepository.getProject(projectId)) {
                is Result.Success -> _state.update { it.copy(name = result.data.name) }
                is Result.Error ->
                    if ((result.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND) {
                        _state.update { it.copy(deleted = true) }
                    } else {
                        Logger.w(result.exception) { "Failed to reload project $projectId" }
                    }
                is Result.Loading -> Unit
            }
        }
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}

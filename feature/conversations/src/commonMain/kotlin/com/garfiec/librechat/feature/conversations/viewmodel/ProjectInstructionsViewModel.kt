package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ProjectContextFailure
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.toProjectContextFailure
import com.garfiec.librechat.core.model.config.ChatProjectLimits
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ProjectInstructionsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The text the server holds, as last loaded. Dirty is measured against it. */
    val saved: String = "",
    val draft: String = "",
    /** The `contextRevision` the next save is based on. */
    val revision: Int? = null,
    val maxLength: Int = ChatProjectLimits.DEFAULT_MAX_INSTRUCTIONS_LENGTH,
    val isSaving: Boolean = false,
    /** After a conflict: the other device's text, reloaded, for "View theirs". */
    val theirs: String? = null,
    val showTheirs: Boolean = false,
    val discardPrompt: Boolean = false,
    /** The screen should close: saved, discarded, left clean, or the project is gone. */
    val exitRequested: Boolean = false,
) {
    val isDirty: Boolean get() = !isLoading && !loadFailed && draft.trim() != saved.trim()

    /**
     * A save with nothing changed is refused even when it would succeed: on a stale revision the
     * server answers it with a conflict, for an edit that changes nothing.
     */
    val canSave: Boolean get() = isDirty && !isSaving
}

sealed interface ProjectInstructionsEvent {
    /** The project changed on another device; the draft is kept and the project reloaded behind it. */
    data object Conflict : ProjectInstructionsEvent

    data object SaveFailed : ProjectInstructionsEvent
}

/**
 * The project instructions editor (v0.8.9-rc1, `ConversationsRoute.ProjectInstructions`). Reads the
 * project fresh on open, which gives the revision the save is checked against.
 *
 * The draft lives here, not in the saved-state bundle: at the 200,000-character ceiling it would
 * come near Android's transaction limit. Process death loses it.
 */
class ProjectInstructionsViewModel(
    private val projectId: String,
    private val projectRepository: ProjectRepository,
    configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectInstructionsUiState())
    val uiState: StateFlow<ProjectInstructionsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProjectInstructionsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ProjectInstructionsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            configRepository.projectLimits(viewModelScope).collect { limits ->
                _uiState.update { it.copy(maxLength = limits.maxInstructionsLength) }
            }
        }
        load()
    }

    fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            when (val result = projectRepository.getProject(projectId)) {
                is Result.Success -> {
                    val text = result.data.instructions.orEmpty()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            saved = text,
                            draft = text,
                            revision = result.data.contextRevision,
                        )
                    }
                }
                is Result.Error -> {
                    if (result.toProjectContextFailure() == ProjectContextFailure.NotFound) {
                        _uiState.update { it.copy(isLoading = false, exitRequested = true) }
                    } else {
                        Logger.w(result.exception) { "Failed to load project $projectId for its instructions" }
                        _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun onDraftChange(text: String) {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.loadFailed) return
        _uiState.update { it.copy(draft = text.take(it.maxLength)) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val text = state.draft.trim()
            when (val result = projectRepository.updateInstructions(projectId, text, state.revision)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isSaving = false,
                        saved = result.data.instructions ?: text,
                        revision = result.data.contextRevision,
                        exitRequested = true,
                    )
                }
                is Result.Error -> onSaveFailed(result)
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun onSaveFailed(error: Result.Error) {
        when (error.toProjectContextFailure()) {
            ProjectContextFailure.Conflict -> {
                // Reload behind the kept draft, so the next Save carries the new revision and wins.
                when (val reloaded = projectRepository.getProject(projectId)) {
                    is Result.Success -> {
                        val theirs = reloaded.data.instructions.orEmpty()
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saved = theirs,
                                theirs = theirs,
                                revision = reloaded.data.contextRevision,
                            )
                        }
                        _events.emit(ProjectInstructionsEvent.Conflict)
                    }
                    is Result.Error -> {
                        if (reloaded.toProjectContextFailure() == ProjectContextFailure.NotFound) {
                            _uiState.update { it.copy(isSaving = false, exitRequested = true) }
                        } else {
                            _uiState.update { it.copy(isSaving = false) }
                            _events.emit(ProjectInstructionsEvent.SaveFailed)
                        }
                    }
                    is Result.Loading -> Unit
                }
            }
            // The project is gone: leave, and the project page closes itself on return.
            ProjectContextFailure.NotFound -> _uiState.update { it.copy(isSaving = false, exitRequested = true) }
            else -> {
                Logger.w(error.exception) { "Failed to save project $projectId instructions" }
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(ProjectInstructionsEvent.SaveFailed)
            }
        }
    }

    fun viewTheirs() {
        if (_uiState.value.theirs != null) _uiState.update { it.copy(showTheirs = true) }
    }

    /** Replaces the draft with the other device's text; the editor is clean again. */
    fun useTheirs() {
        val theirs = _uiState.value.theirs ?: return
        _uiState.update { it.copy(draft = theirs, saved = theirs, showTheirs = false) }
    }

    fun keepMine() = _uiState.update { it.copy(showTheirs = false) }

    /** Close and back: ignored while saving, ask first while dirty. */
    fun onBackRequested() {
        val state = _uiState.value
        when {
            state.isSaving -> Unit
            state.isDirty -> _uiState.update { it.copy(discardPrompt = true) }
            else -> _uiState.update { it.copy(exitRequested = true) }
        }
    }

    fun confirmDiscard() = _uiState.update { it.copy(discardPrompt = false, exitRequested = true) }

    fun dismissDiscard() = _uiState.update { it.copy(discardPrompt = false) }
}

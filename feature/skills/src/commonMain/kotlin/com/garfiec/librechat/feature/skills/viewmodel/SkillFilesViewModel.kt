package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillFileChanges
import com.garfiec.librechat.feature.skills.SkillFilePaths
import com.garfiec.librechat.feature.skills.components.PickedDocument
import com.garfiec.librechat.feature.skills.skillError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SkillFilesUiState(
    val files: List<SkillFile> = emptyList(),
    val isLoading: Boolean = false,
    /** A list fetch has succeeded at least once; until then an empty [files] means "unknown". */
    val loaded: Boolean = false,
    val isUploading: Boolean = false,
    /**
     * The last list fetch failed. Cleared when the next one starts. Upload and remove failures are
     * one-off and go out as [SkillFilesEvent.ActionFailed] instead.
     */
    val error: SkillError? = null,
    /**
     * Fail-CLOSED: whether the viewer may add, replace and remove this skill's files. Set from the
     * detail page's resolved access (`SkillAccess.canEditFiles`: inline skill and owner, admin or
     * the EDIT bit; the file routes don't check role CREATE). False until that resolves.
     */
    val canEditFiles: Boolean = false,
    /** The file the user asked to remove, waiting for them to confirm. */
    val pendingRemoval: SkillFile? = null,
    /**
     * The path of a picked file that would replace one the skill already has, waiting for the user
     * to confirm: the upload route upserts by path and deletes the old bytes, with no undo.
     */
    val pendingReplacement: String? = null,
    /**
     * Paths whose DELETE is in flight. Their rows wait for it: a second Remove would send a second
     * DELETE, which 404s and reports a failure for a file that was removed.
     */
    val removing: Set<String> = emptySet(),
) {
    /** The list couldn't be fetched and there's nothing to show: the section offers a retry instead. */
    val listFailed: Boolean get() = error != null && files.isEmpty()
}

/** One-shot failures the detail page shows as a snackbar. */
sealed interface SkillFilesEvent {
    /** An upload or a remove failed, or the picked file was refused before upload. */
    data class ActionFailed(val error: SkillError) : SkillFilesEvent
}

class SkillFilesViewModel(
    private val skillsRepository: SkillsRepository,
    fileChanges: SkillFileChanges,
    private val skillId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillFilesUiState())
    val uiState: StateFlow<SkillFilesUiState> = _uiState.asStateFlow()

    // Buffered, sent to without suspending: on Android the picker's result lands before the page
    // resumes and its collector restarts, and a failure sent then must wait for it.
    private val _events = Channel<SkillFilesEvent>(Channel.BUFFERED)
    val events: Flow<SkillFilesEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    /** The picked file behind [SkillFilesUiState.pendingReplacement]; kept here, out of the drawn state. */
    private var pendingDocument: PickedDocument? = null

    init {
        // A save on the file screen changes the file's size (and maybe its type): reload for it
        // while this page sits under that screen, not only when it resumes.
        viewModelScope.launch { fileChanges.changesTo(skillId).collect { load() } }
    }

    fun setCanEditFiles(canEditFiles: Boolean) {
        _uiState.value = _uiState.value.copy(canEditFiles = canEditFiles)
    }

    /**
     * The newest fetch wins: resumes, the reload after an upload or remove, saves on the file
     * screen and Retry all land here, and an older answer arriving last would put a removed file
     * back, or drop a new one, until the next resume.
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val showSpinner = _uiState.value.files.isEmpty()
            _uiState.value = _uiState.value.copy(isLoading = showSpinner, error = null)
            when (val result = skillsRepository.listSkillFiles(skillId)) {
                is Result.Success ->
                    _uiState.value = _uiState.value.copy(files = result.data, isLoading = false, loaded = true)
                is Result.Error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = skillError(SkillErrorKind.LOAD_FILES, result.message),
                    )
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * Adds the picked file. One whose path the skill already has waits for [confirmReplace]: the
     * server would overwrite that file in place (`upload.ts` upserts by path, then deletes the
     * replaced bytes), which "Add" must not do unasked.
     */
    fun upload(doc: PickedDocument) {
        // relativePath is the sanitized filename: flat, no folders.
        val relativePath = SkillFilePaths.sanitize(doc.filename)
        if (SkillFilePaths.isReserved(relativePath)) {
            // Upstream has no rename for it; the server would refuse it with a bare 400.
            _events.trySend(SkillFilesEvent.ActionFailed(SkillError(SkillErrorKind.SKILL_MD_UPLOAD)))
            return
        }
        if (_uiState.value.files.any { it.relativePath == relativePath }) {
            pendingDocument = doc
            _uiState.value = _uiState.value.copy(pendingReplacement = relativePath)
            return
        }
        send(doc, relativePath)
    }

    /** The user confirmed replacing the file [upload] held back. */
    fun confirmReplace() {
        val doc = pendingDocument ?: return
        val relativePath = _uiState.value.pendingReplacement ?: return
        dismissReplace()
        send(doc, relativePath)
    }

    fun dismissReplace() {
        pendingDocument = null
        _uiState.value = _uiState.value.copy(pendingReplacement = null)
    }

    private fun send(doc: PickedDocument, relativePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            when (
                val result = skillsRepository.uploadSkillFile(
                    skillId = skillId,
                    relativePath = relativePath,
                    bytes = doc.bytes,
                    filename = doc.filename,
                    mimeType = doc.mimeType,
                )
            ) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    load()
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(isUploading = false)
                    _events.trySend(SkillFilesEvent.ActionFailed(skillError(SkillErrorKind.UPLOAD_FILE, result.message)))
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /** Asks to remove [file]; nothing is deleted until [confirmRemove]. */
    fun requestRemove(file: SkillFile) {
        if (!_uiState.value.canEditFiles || file.relativePath in _uiState.value.removing) return
        _uiState.value = _uiState.value.copy(pendingRemoval = file)
    }

    fun dismissRemove() {
        _uiState.value = _uiState.value.copy(pendingRemoval = null)
    }

    fun confirmRemove() {
        val file = _uiState.value.pendingRemoval ?: return
        _uiState.value = _uiState.value.copy(pendingRemoval = null)
        delete(file)
    }

    /**
     * One DELETE per file at a time. The removed row goes at once, before the reload that confirms
     * it, so it can't be removed again in between.
     */
    fun delete(file: SkillFile) {
        val path = file.relativePath
        if (path in _uiState.value.removing) return
        _uiState.update { it.copy(removing = it.removing + path) }
        viewModelScope.launch {
            when (val result = skillsRepository.deleteSkillFile(skillId, path)) {
                is Result.Success -> {
                    _uiState.update { state ->
                        state.copy(files = state.files.filterNot { it.relativePath == path }, removing = state.removing - path)
                    }
                    load()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(removing = it.removing - path) }
                    _events.trySend(SkillFilesEvent.ActionFailed(skillError(SkillErrorKind.DELETE_FILE, result.message)))
                }
                is Result.Loading -> _uiState.update { it.copy(removing = it.removing - path) }
            }
        }
    }
}

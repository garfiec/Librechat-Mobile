package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillFileEditResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import com.garfiec.librechat.feature.skills.components.PickedDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class SkillFilesUiState(
    val files: List<SkillFile> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null,
    /** Fail-CLOSED: file mutations require SKILLS.CREATE (upstream uploads are ACL EDIT
     *  behind checkSkillCreate). Hidden unless explicitly granted. */
    val hasCreatePermission: Boolean = false,
    /**
     * Only an inline skill's files can change: from v0.8.8 the server answers 403 for an
     * externally managed (`source != "inline"`) skill. Unknown until the skill loads, and absent
     * `source` means inline, as upstream reads it.
     */
    val isInlineSkill: Boolean = false,
    /** The file open in the viewer/editor, or null. */
    val editor: SkillFileEditorState? = null,
) {
    val canEditFiles: Boolean get() = hasCreatePermission && isInlineSkill
}

@Immutable
data class SkillFileEditorState(
    val relativePath: String,
    val isLoading: Boolean = true,
    val content: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    /**
     * The revision the edit is conditional on (v0.8.8). Absent on older servers, which have no
     * conditional route, so the file then opens read-only.
     */
    val fileId: String? = null,
    val isBinary: Boolean = false,
    val isSaving: Boolean = false,
    /** Someone else replaced the file first; saving is blocked until the user reloads. */
    val conflict: Boolean = false,
    val error: String? = null,
) {
    fun isEditable(canEditFiles: Boolean): Boolean =
        canEditFiles && fileId != null && !isBinary && content != null && !conflict
}

class SkillFilesViewModel(
    private val skillsRepository: SkillsRepository,
    private val roleRepository: RoleRepository,
    private val skillId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillFilesUiState())
    val uiState: StateFlow<SkillFilesUiState> = _uiState.asStateFlow()

    init {
        observeEditPermission()
    }

    private fun observeEditPermission() {
        viewModelScope.launch {
            roleRepository.userPermissions.collect { role ->
                _uiState.value = _uiState.value.copy(
                    hasCreatePermission = role.hasAccessStrictOrDenied(PermissionType.SKILLS, Permission.CREATE),
                )
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            val showSpinner = _uiState.value.files.isEmpty()
            _uiState.value = _uiState.value.copy(isLoading = showSpinner, error = null)
            when (val result = skillsRepository.listSkillFiles(skillId)) {
                is Result.Success ->
                    _uiState.value = _uiState.value.copy(files = result.data, isLoading = false)
                is Result.Error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to load files",
                    )
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun upload(doc: PickedDocument) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            // relativePath is the (sanitized) filename — flat, no folders. The
            // server rejects SKILL.md, absolute paths, and traversal with 400,
            // surfaced via the repo's validation-message extraction.
            val relativePath = sanitizeRelativePath(doc.filename)
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
                is Result.Error ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = result.message ?: "Failed to upload file",
                    )
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun delete(file: SkillFile) {
        viewModelScope.launch {
            when (val result = skillsRepository.deleteSkillFile(skillId, file.relativePath)) {
                is Result.Success -> load()
                is Result.Error ->
                    _uiState.value = _uiState.value.copy(error = result.message ?: "Failed to delete file")
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun setSkillSource(source: String?) {
        _uiState.value = _uiState.value.copy(isInlineSkill = (source ?: INLINE_SOURCE) == INLINE_SOURCE)
    }

    fun openFile(file: SkillFile) {
        _uiState.value = _uiState.value.copy(editor = SkillFileEditorState(relativePath = file.relativePath))
        loadEditor(file.relativePath)
    }

    /** After a conflict: re-read the server's current copy (and revision), discarding the draft. */
    fun reloadEditor() {
        val path = _uiState.value.editor?.relativePath ?: return
        _uiState.value = _uiState.value.copy(editor = SkillFileEditorState(relativePath = path))
        loadEditor(path)
    }

    fun closeEditor() {
        _uiState.value = _uiState.value.copy(editor = null)
    }

    fun saveEditor(content: String) {
        val editor = _uiState.value.editor ?: return
        if (!editor.isEditable(_uiState.value.canEditFiles) || editor.isSaving) return
        val fileId = editor.fileId ?: return
        _uiState.value = _uiState.value.copy(editor = editor.copy(isSaving = true, error = null))
        viewModelScope.launch {
            val result = skillsRepository.editSkillFile(
                skillId = skillId,
                relativePath = editor.relativePath,
                expectedFileId = fileId,
                content = content,
                filename = editor.filename ?: editor.relativePath.substringAfterLast('/'),
                mimeType = editor.mimeType ?: DEFAULT_TEXT_MIME,
            )
            val current = _uiState.value.editor?.takeIf { it.relativePath == editor.relativePath } ?: return@launch
            when (result) {
                is SkillFileEditResult.Saved -> {
                    _uiState.value = _uiState.value.copy(editor = null)
                    load()
                }
                SkillFileEditResult.Conflict ->
                    _uiState.value = _uiState.value.copy(editor = current.copy(isSaving = false, conflict = true))
                is SkillFileEditResult.Failed ->
                    _uiState.value = _uiState.value.copy(
                        editor = current.copy(isSaving = false, error = result.message ?: "Failed to save file"),
                    )
            }
        }
    }

    private fun loadEditor(relativePath: String) {
        viewModelScope.launch {
            val result = skillsRepository.getSkillFileContent(skillId, relativePath)
            val current = _uiState.value.editor?.takeIf { it.relativePath == relativePath } ?: return@launch
            _uiState.value = _uiState.value.copy(
                editor = when (result) {
                    is Result.Success -> current.copy(
                        isLoading = false,
                        content = result.data.content,
                        filename = result.data.filename,
                        mimeType = result.data.mimeType,
                        fileId = result.data.fileId,
                        isBinary = result.data.isBinary,
                    )
                    is Result.Error -> current.copy(isLoading = false, error = result.message ?: "Failed to load file")
                    is Result.Loading -> current
                },
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val INLINE_SOURCE = "inline"
        const val DEFAULT_TEXT_MIME = "text/plain"
    }

    private fun sanitizeRelativePath(filename: String): String {
        // Keep only the basename, strip leading dots/slashes, allow the server's
        // [a-zA-Z0-9._-/] charset (we never produce folders, so replace others).
        val base = filename.substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
        return base.map { c -> if (c.isLetterOrDigit() || c in "._-") c else '_' }.joinToString("")
            .trimStart('.')
            .ifBlank { "file" }
    }
}

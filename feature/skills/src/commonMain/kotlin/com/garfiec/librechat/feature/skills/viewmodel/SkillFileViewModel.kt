package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SkillFileEditResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.skills.resolveSkillAccess
import com.garfiec.librechat.core.data.skills.toSkillViewer
import com.garfiec.librechat.core.model.response.SkillFileContentResponse
import com.garfiec.librechat.feature.skills.SkillFileChanges
import com.garfiec.librechat.feature.skills.isSkillFileInlineImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the file screen shows the file it opened. */
enum class SkillFileKind {
    LOADING,
    TEXT,
    IMAGE,

    /** Neither text nor a type the server serves inline as an image. */
    BINARY,
}

/** Why a text file opens read-only, for the note above it. */
enum class SkillFileReadOnlyReason {
    /** The viewer may not change this skill's files. */
    NO_ACCESS,

    /** Whether the viewer may change them couldn't be checked; a reload asks again. */
    ACCESS_UNKNOWN,

    /** `SKILL.md` is the skill's body, read through the file route without a revision. */
    SKILL_BODY,

    /** Over the JSON route's 1 MB cap, read raw. Shown, not edited. */
    TOO_LARGE,

    /** [TOO_LARGE], and past what the screen will show: only the start of the file is shown. */
    TRUNCATED,

    /** An older server sent no revision, so there's no conditional save to make. */
    NO_REVISION,
}

/** What the screen offers to discard unsaved edits for. */
enum class SkillFileDiscardTarget { EXIT, RELOAD }

/** A raw image's bytes. Identity equality, so state comparisons don't walk megabytes. */
class SkillFileImage(val bytes: ByteArray)

@Immutable
data class SkillFileUiState(
    val relativePath: String,
    val kind: SkillFileKind = SkillFileKind.LOADING,
    /** The server's copy of a text file; [draft] is compared against it. */
    val loadedText: String? = null,
    val draft: String = "",
    val image: SkillFileImage? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    /** The revision a save is conditional on (v0.8.8). Absent for `SKILL.md` and on older servers. */
    val fileId: String? = null,
    /** Text read raw because it was over the JSON route's cap. */
    val isLargeText: Boolean = false,
    /** [isLargeText] past [SkillFileViewModel.PREVIEW_MAX_BYTES]: [loadedText] is only its start. */
    val isTruncated: Boolean = false,
    /**
     * Fail-CLOSED: `SkillAccess.canEditFiles` for this skill (inline, and owner, admin or the EDIT
     * bit). False until the skill, the viewer and the bits have all answered.
     */
    val canEditFiles: Boolean = false,
    val accessResolved: Boolean = false,
    /** The last access check failed to fetch what it needs; [accessResolved] stays false. */
    val accessFailed: Boolean = false,
    val isSaving: Boolean = false,
    /** Someone else replaced the file first; saving is blocked until the user reloads. */
    val conflict: Boolean = false,
    val loadFailed: Boolean = false,
    /** The server's or transport's text for [loadFailed], when it has one. */
    val loadError: String? = null,
    val saveFailed: Boolean = false,
    val saveError: String? = null,
    /** Set when the user asked to leave or reload with unsaved edits. */
    val discardPrompt: SkillFileDiscardTarget? = null,
    /** A save landed; the screen pops. */
    val saved: Boolean = false,
    /** The user may leave; the screen pops. */
    val exitRequested: Boolean = false,
) {
    val isDirty: Boolean get() = kind == SkillFileKind.TEXT && loadedText != null && draft != loadedText

    val readOnlyReason: SkillFileReadOnlyReason?
        get() = when {
            kind != SkillFileKind.TEXT -> null
            fileId == null && relativePath == SKILL_BODY_PATH -> SkillFileReadOnlyReason.SKILL_BODY
            isTruncated -> SkillFileReadOnlyReason.TRUNCATED
            isLargeText -> SkillFileReadOnlyReason.TOO_LARGE
            // Not until access answers, so the note doesn't flash on an editable file.
            accessResolved && !canEditFiles -> SkillFileReadOnlyReason.NO_ACCESS
            accessFailed -> SkillFileReadOnlyReason.ACCESS_UNKNOWN
            fileId == null -> SkillFileReadOnlyReason.NO_REVISION
            else -> null
        }

    val isEditable: Boolean
        get() = kind == SkillFileKind.TEXT && canEditFiles && readOnlyReason == null && !conflict

    val canSave: Boolean get() = isEditable && isDirty && !isSaving && !saved

    companion object {
        /** The JSON route answers this path from the skill's body, with no `fileId`. */
        const val SKILL_BODY_PATH = "SKILL.md"
    }
}

/**
 * One skill file on its own screen: text in an editor (read-only where the viewer, the file or the
 * server rules it out), an image preview for the types the server serves inline, or a note for
 * anything else. Resolves the viewer's file access itself, so a restored screen never trusts a
 * stale grant. A save is conditional on the revision it read (409 → [SkillFileUiState.conflict],
 * cleared only by reloading the server's copy).
 */
class SkillFileViewModel(
    private val skillsRepository: SkillsRepository,
    private val userRepository: UserRepository,
    private val fileChanges: SkillFileChanges,
    private val skillId: String,
    relativePath: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillFileUiState(relativePath = relativePath))
    val uiState: StateFlow<SkillFileUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var accessJob: Job? = null

    init {
        load()
    }

    /**
     * `canEditFiles` needs no role check: the file routes run only the ACL EDIT check. A failed
     * fetch answers nothing (the file stays read-only, without the "you can't edit" note) and the
     * next [load] asks again, so a flaky moment doesn't lock an owner out of their own file.
     */
    private fun resolveAccess() {
        if (_uiState.value.accessResolved || accessJob?.isActive == true) return
        accessJob = viewModelScope.launch {
            val skill = async { skillsRepository.getSkill(skillId) }
            val user = async { userRepository.getUser() }
            val bits = async { skillsRepository.getSkillEffectivePermissions() }
            val skillResult = skill.await()
            val userResult = user.await()
            val bitsResult = bits.await()
            if (skillResult !is Result.Success || userResult !is Result.Success || bitsResult !is Result.Success) {
                _uiState.update { it.copy(accessFailed = true) }
                return@launch
            }
            val canEditFiles = resolveSkillAccess(
                author = skillResult.data.author,
                source = skillResult.data.source,
                viewer = userResult.data.toSkillViewer(),
                permissionBits = bitsResult.data[skillId] ?: 0,
                canCreate = false,
                canShareRole = false,
            ).canEditFiles
            _uiState.update { it.copy(canEditFiles = canEditFiles, accessResolved = true, accessFailed = false) }
        }
    }

    private fun load() {
        loadJob?.cancel()
        val path = _uiState.value.relativePath
        _uiState.value = SkillFileUiState(
            relativePath = path,
            canEditFiles = _uiState.value.canEditFiles,
            accessResolved = _uiState.value.accessResolved,
        )
        resolveAccess()
        loadJob = viewModelScope.launch {
            when (val result = skillsRepository.getSkillFileContent(skillId, path)) {
                is Result.Success -> show(result.data)
                is Result.Error -> _uiState.update { it.copy(loadFailed = true, loadError = result.message) }
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun show(file: SkillFileContentResponse) {
        _uiState.update {
            it.copy(filename = file.filename, mimeType = file.mimeType, sizeBytes = file.bytes, fileId = file.fileId)
        }
        val text = file.content
        when {
            text != null -> _uiState.update { it.copy(kind = SkillFileKind.TEXT, loadedText = text, draft = text) }
            isSkillFileInlineImage(file.mimeType) -> showRaw { state, bytes ->
                state.copy(kind = SkillFileKind.IMAGE, image = SkillFileImage(bytes))
            }
            // Text over the JSON cap comes back with no content; binary is said so.
            !file.isBinary -> showLargeText()
            else -> _uiState.update { it.copy(kind = SkillFileKind.BINARY) }
        }
    }

    /**
     * Text over the JSON cap, read raw and shown read-only. Only its first [PREVIEW_MAX_BYTES] are
     * fetched: the whole of a multi-megabyte log in a text field janks the screen or runs it out of
     * memory, for a file that can't be edited here anyway.
     */
    private suspend fun showLargeText() {
        val path = _uiState.value.relativePath
        // One byte past the cap says whether there is more without reading it.
        when (val result = skillsRepository.getSkillFileRawPrefix(skillId, path, PREVIEW_MAX_BYTES + 1L)) {
            is Result.Success -> _uiState.update { state ->
                val truncated = result.data.size > PREVIEW_MAX_BYTES
                val shown = if (truncated) result.data.copyOf(PREVIEW_MAX_BYTES) else result.data
                val decoded = decodeUtf8Prefix(shown, truncated)
                if (decoded != null) {
                    state.copy(
                        kind = SkillFileKind.TEXT,
                        loadedText = decoded,
                        draft = decoded,
                        isLargeText = true,
                        isTruncated = truncated,
                    )
                } else {
                    state.copy(kind = SkillFileKind.BINARY)
                }
            }
            is Result.Error -> _uiState.update { it.copy(loadFailed = true, loadError = result.message) }
            is Result.Loading -> Unit
        }
    }

    private suspend fun showRaw(apply: (SkillFileUiState, ByteArray) -> SkillFileUiState) {
        when (val result = skillsRepository.getSkillFileRaw(skillId, _uiState.value.relativePath)) {
            is Result.Success -> _uiState.update { apply(it, result.data) }
            is Result.Error -> _uiState.update { it.copy(loadFailed = true, loadError = result.message) }
            is Result.Loading -> Unit
        }
    }

    /** The image the server sent can't be decoded here (an old OS without AVIF, a truncated file). */
    fun onImageFailed() {
        _uiState.update { if (it.kind == SkillFileKind.IMAGE) it.copy(kind = SkillFileKind.BINARY, image = null) else it }
    }

    fun onDraftChange(value: String) {
        val state = _uiState.value
        if (!state.isEditable || state.isSaving) return
        _uiState.value = state.copy(draft = value, saveFailed = false, saveError = null)
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val fileId = state.fileId ?: return
        _uiState.value = state.copy(isSaving = true, saveFailed = false, saveError = null)
        viewModelScope.launch {
            val result = skillsRepository.editSkillFile(
                skillId = skillId,
                relativePath = state.relativePath,
                expectedFileId = fileId,
                content = state.draft,
                filename = state.filename ?: state.relativePath.substringAfterLast('/'),
                mimeType = state.mimeType ?: DEFAULT_TEXT_MIME,
            )
            when (result) {
                is SkillFileEditResult.Saved -> {
                    // Before anything else, so the list reloads even if this screen is gone by now.
                    fileChanges.post(skillId)
                    _uiState.update { it.copy(isSaving = false, saved = true) }
                }
                SkillFileEditResult.Conflict -> _uiState.update { it.copy(isSaving = false, conflict = true) }
                is SkillFileEditResult.Failed ->
                    _uiState.update { it.copy(isSaving = false, saveFailed = true, saveError = result.message) }
            }
        }
    }

    /**
     * The ⋮ Reload, and the retry after a failed load. Asks first when it would throw away edits,
     * except after a conflict: the banner says the edits go, and reloading is the only way on.
     */
    fun reload() {
        val state = _uiState.value
        if (state.isSaving || state.saved) return
        if (state.isDirty && !state.conflict) {
            _uiState.value = state.copy(discardPrompt = SkillFileDiscardTarget.RELOAD)
        } else {
            load()
        }
    }

    /** System back and the top bar's back, which iOS has no system back for. */
    fun onBackRequested() {
        val state = _uiState.value
        // A save pops the screen when it lands; leaving first would pop the page behind this one.
        if (state.isSaving || state.saved || state.exitRequested) return
        _uiState.value = if (state.isDirty) {
            state.copy(discardPrompt = SkillFileDiscardTarget.EXIT)
        } else {
            state.copy(exitRequested = true)
        }
    }

    fun confirmDiscard() {
        when (_uiState.value.discardPrompt) {
            SkillFileDiscardTarget.EXIT -> _uiState.update { it.copy(discardPrompt = null, exitRequested = true) }
            SkillFileDiscardTarget.RELOAD -> load()
            null -> Unit
        }
    }

    fun dismissDiscard() {
        _uiState.update { it.copy(discardPrompt = null) }
    }

    internal companion object {
        private const val DEFAULT_TEXT_MIME = "text/plain"

        /** How much of a read-only large text file the screen shows. The client's own limit. */
        const val PREVIEW_MAX_BYTES = 256 * 1024
    }
}

/**
 * [bytes] as UTF-8, or null when they aren't (the file is binary after all). When [truncated], a
 * character the cut split in two is dropped first, so the cut alone never reads as binary.
 */
internal fun decodeUtf8Prefix(bytes: ByteArray, truncated: Boolean): String? {
    val end = if (truncated) completeUtf8Length(bytes) else bytes.size
    return runCatching { bytes.decodeToString(0, end, throwOnInvalidSequence = true) }.getOrNull()
}

/** The length of [bytes] without a trailing multi-byte UTF-8 sequence that is missing its end. */
private fun completeUtf8Length(bytes: ByteArray): Int {
    // A sequence is at most four bytes, so its lead byte is within the last four.
    for (back in 1..minOf(UTF8_MAX_SEQUENCE, bytes.size)) {
        val index = bytes.size - back
        val b = bytes[index].toInt() and 0xFF
        if (b and 0xC0 == 0x80) continue // a continuation byte: keep looking for the lead
        val length = when {
            b < 0x80 -> 1
            b and 0xE0 == 0xC0 -> 2
            b and 0xF0 == 0xE0 -> 3
            b and 0xF8 == 0xF0 -> 4
            else -> return bytes.size // not UTF-8; the strict decode says so
        }
        return if (index + length > bytes.size) index else bytes.size
    }
    return bytes.size
}

private const val UTF8_MAX_SEQUENCE = 4

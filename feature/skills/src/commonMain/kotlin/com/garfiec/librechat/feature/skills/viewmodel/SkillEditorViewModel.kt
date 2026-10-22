package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.SkillCreateResult
import com.garfiec.librechat.core.data.repository.SkillUpdateResult
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.request.CreateSkillRequest
import com.garfiec.librechat.core.model.request.UpdateSkillRequest
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.feature.skills.SkillCategoriesLoader
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillNameIssue
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.garfiec.librechat.feature.skills.SkillValidation
import com.garfiec.librechat.feature.skills.skillError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Immutable
data class SkillEditorUiState(
    val isEditMode: Boolean = false,
    val skillId: String? = null,
    val name: String = "",
    val displayTitle: String = "",
    val description: String = "",
    val body: String = "",
    val category: String = "",
    /** When true, the skill auto-primes into every turn (mirrors the upstream
     *  `always-apply` frontmatter field; server default is false). */
    val alwaysApply: Boolean = false,
    /** Server category presets (`GET /api/categories`) for the category dropdown.
     *  Empty until loaded; the dropdown falls back to whatever [category] holds. */
    val availableCategories: List<Category> = emptyList(),
    val isLoading: Boolean = false,
    /**
     * The skill being edited hasn't loaded yet. Until it has, there is no form to fill and nothing
     * to save: a PATCH needs the version that load reads.
     */
    val loadPending: Boolean = false,
    val isSaving: Boolean = false,
    val error: SkillError? = null,
    /** The last save was refused because another skill has this name. Cleared by editing the name. */
    val nameTaken: Boolean = false,
    /** Shown when a save 409s: the skill changed on the server since the editor
     *  opened. The user's edits are PRESERVED (only [loadedVersion] is bumped to
     *  the server's current version); a re-save then overwrites. */
    val showConflictNotice: Boolean = false,
    /** Version loaded into the editor; sent as expectedVersion on PATCH. Bumped to
     *  the server's current version on a 409 so a re-save targets the right one. */
    val loadedVersion: Int = 0,
    /** The server's skill as a 409 conflict reported it. The user's edits are NOT replaced by it;
     *  the re-save reads it to know which optional fields the server now holds, so a field the
     *  user left blank is sent as "" (cleared) rather than omitted (kept). */
    val serverCurrent: Skill? = null,
    /** What Save would send for the skill as it was loaded (blank for create). A 409 keeps it, so
     *  the edits it preserves still count as unsaved. */
    val baseline: SkillFields = SkillFields(),
    val showDiscardConfirm: Boolean = false,
    /** The description has been typed in, so clearing it again is worth pointing out. */
    val descriptionTouched: Boolean = false,
) {
    /** Compared as Save would send them, so late reference data (the category presets) and the
     *  version bookkeeping are never mistaken for an edit. */
    val hasUnsavedChanges: Boolean get() = !isLoading && !loadPending && savedFields() != baseline

    /** The skill being edited failed to load: the page offers a retry instead of the form. */
    val loadFailed: Boolean get() = loadPending && !isLoading && error?.kind == SkillErrorKind.LOAD_SKILL

    internal fun savedFields() = SkillFields(
        name = name,
        displayTitle = displayTitle.ifBlank { null },
        description = description,
        body = body,
        category = category.ifBlank { null },
        alwaysApply = alwaysApply,
    )

    /** Why the typed name would be refused, shown under the Name field; null while empty or fine. */
    val nameIssue: SkillNameIssue? get() = SkillValidation.nameIssue(name)
    val descriptionTooLong: Boolean get() = description.length > SkillValidation.DESCRIPTION_MAX_LENGTH

    /** Save needs a description. */
    val descriptionMissing: Boolean get() = description.isBlank()

    /**
     * Whether the field says a description is needed: once it has been typed in and cleared, or once
     * a valid name leaves it as the only thing keeping Save greyed. Not on a form nobody has touched.
     */
    val showDescriptionRequired: Boolean
        get() = descriptionMissing && (descriptionTouched || SkillValidation.isNameValid(name))
    val bodyTooLong: Boolean get() = !SkillValidation.isBodyValid(body)
    val displayTitleTooLong: Boolean get() = !SkillValidation.isDisplayTitleValid(displayTitle)
    val canSave: Boolean
        get() = !isSaving &&
            !loadPending &&
            // An edit with nothing changed would only bump the version.
            (!isEditMode || hasUnsavedChanges) &&
            SkillValidation.isNameValid(name) &&
            SkillValidation.isDisplayTitleValid(displayTitle) &&
            SkillValidation.isDescriptionValid(description) &&
            SkillValidation.isBodyValid(body)
}

/** The fields a save writes, normalized the way the request carries them. */
@Immutable
data class SkillFields(
    val name: String = "",
    val displayTitle: String? = null,
    val description: String = "",
    val body: String = "",
    val category: String? = null,
    val alwaysApply: Boolean = false,
)

sealed interface SkillEditorEvent {
    /** The save landed. [warnings] are the server's non-blocking notes on it, already posted to
     *  [SkillSaveNotices] for the page the editor returns to. */
    data class Saved(val skillId: String, val warnings: List<String> = emptyList()) : SkillEditorEvent
    data object Exit : SkillEditorEvent

    /** The save was refused because the name is taken. The Name field may be scrolled off screen
     *  under a long body, so the page brings it into view and focuses it. */
    data object NameTaken : SkillEditorEvent

    /** The save failed for any other reason, or ran into a newer version. The reason is drawn at
     *  the top of the form, which a long body can scroll out of view; the page brings it back. */
    data object SaveFailed : SkillEditorEvent
}

class SkillEditorViewModel(
    private val skillsRepository: SkillsRepository,
    private val configRepository: ConfigRepository,
    private val saveNotices: SkillSaveNotices,
    initialSkillId: String? = null,
) : ViewModel() {

    private val editId: String? = initialSkillId

    private val _uiState = MutableStateFlow(
        SkillEditorUiState(
            isEditMode = editId != null,
            skillId = editId,
            isLoading = editId != null,
            loadPending = editId != null,
        ),
    )
    val uiState: StateFlow<SkillEditorUiState> = _uiState.asStateFlow()

    // A channel, not a SharedFlow: an activity recreation (rotation, unfold) cancels the screen's
    // collector while a save is in flight, and a Saved sent then must wait for the next collector
    // rather than be dropped and leave the editor open over a skill that already saved.
    private val _events = Channel<SkillEditorEvent>(Channel.BUFFERED)
    val events: Flow<SkillEditorEvent> = _events.receiveAsFlow()

    // Set once Saved or Exit is sent: the screen is leaving, so a back or save tap during its exit
    // animation must not send a second navigation (popping the page behind) or a second save.
    private var finished = false

    /** A failure is non-fatal: the dropdown shows no presets and keeps the loaded or typed value. */
    private val categories = SkillCategoriesLoader(configRepository, viewModelScope) {
        _uiState.value = _uiState.value.copy(availableCategories = it)
    }

    init {
        if (editId != null) load(editId)
        categories.load()
    }

    private fun load(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = skillsRepository.getSkill(id)) {
                is Result.Success -> _uiState.value = _uiState.value.applyLoaded(result.data)
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = skillError(SkillErrorKind.LOAD_SKILL, result.message),
                )
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun SkillEditorUiState.applyLoaded(skill: Skill) = copy(
        name = skill.name,
        displayTitle = skill.displayTitle ?: "",
        description = skill.description,
        body = skill.body,
        category = skill.category ?: "",
        alwaysApply = skill.alwaysApply ?: false,
        loadedVersion = skill.version,
        isLoading = false,
        loadPending = false,
        error = null,
    ).let { it.copy(baseline = it.savedFields()) }

    /** The Retry on a failed load. */
    fun retryLoad() {
        val state = _uiState.value
        if (editId == null || !state.loadPending || state.isLoading) return
        load(editId)
    }

    // The form holds still while a save is in flight (the fields are read-only too): the save sends
    // what was there when Save was tapped, its landing makes that the baseline and pops the editor,
    // so anything typed meanwhile would be in neither the request nor a discard prompt.
    private inline fun edit(change: SkillEditorUiState.() -> SkillEditorUiState) {
        val state = _uiState.value
        if (state.isSaving || finished) return
        _uiState.value = state.change()
    }

    fun onNameChanged(value: String) = edit { if (value == name) this else copy(name = value, nameTaken = false) }
    fun onDisplayTitleChanged(value: String) = edit { copy(displayTitle = value) }
    fun onDescriptionChanged(value: String) = edit {
        copy(description = value, descriptionTouched = descriptionTouched || value.isNotEmpty())
    }
    fun onBodyChanged(value: String) = edit { copy(body = value) }
    fun onCategoryChanged(value: String) = edit { copy(category = value) }
    fun onAlwaysApplyChanged(value: Boolean) = edit { copy(alwaysApply = value) }

    fun onBackRequested() {
        // A save navigates when it lands; leaving first would let it pop the screen behind this one.
        if (_uiState.value.isSaving || finished) return
        if (_uiState.value.hasUnsavedChanges) {
            _uiState.value = _uiState.value.copy(showDiscardConfirm = true)
        } else {
            exit()
        }
    }

    fun discardChanges() {
        _uiState.value = _uiState.value.copy(showDiscardConfirm = false)
        if (finished) return
        exit()
    }

    private fun exit() {
        finished = true
        _events.trySend(SkillEditorEvent.Exit)
    }

    fun dismissDiscardConfirmation() { _uiState.value = _uiState.value.copy(showDiscardConfirm = false) }

    fun dismissConflictNotice() { _uiState.value = _uiState.value.copy(showConflictNotice = false) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave || finished) return
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null, showConflictNotice = false, nameTaken = false)
            if (state.isEditMode && state.skillId != null) {
                saveUpdate(state)
            } else {
                saveCreate(state)
            }
        }
    }

    private suspend fun onNameTaken() {
        _uiState.value = _uiState.value.copy(isSaving = false, nameTaken = true)
        _events.send(SkillEditorEvent.NameTaken)
    }

    /**
     * Hands the save's warnings to the page the editor returns to, then reports the save. The saved
     * fields become the baseline first, so the form no longer counts as unsaved even before the
     * screen pops it.
     */
    private suspend fun emitSaved(saved: SkillEditorUiState, skill: Skill) {
        _uiState.value = _uiState.value.copy(
            isSaving = false,
            baseline = saved.savedFields(),
            loadedVersion = if (saved.isEditMode) skill.version else _uiState.value.loadedVersion,
        )
        val warnings = skill.warnings.orEmpty().map { it.message.trim() }.filter { it.isNotEmpty() }
        saveNotices.post(skill.id, warnings)
        finished = true
        _events.send(SkillEditorEvent.Saved(skill.id, warnings))
    }

    private suspend fun saveCreate(state: SkillEditorUiState) {
        val request = CreateSkillRequest(
            name = state.name,
            description = state.description,
            body = state.body,
            displayTitle = state.displayTitle.ifBlank { null },
            category = state.category.ifBlank { null },
            // Send the explicit boolean (not null) so the value round-trips and a
            // later edit can toggle it back off (server $set-merges on update).
            alwaysApply = state.alwaysApply,
        )
        when (val result = skillsRepository.createSkill(request)) {
            is SkillCreateResult.Success -> emitSaved(state, result.skill)
            SkillCreateResult.NameTaken -> onNameTaken()
            is SkillCreateResult.Error -> onSaveFailed(skillError(SkillErrorKind.CREATE_SKILL, result.message))
        }
    }

    private suspend fun saveUpdate(state: SkillEditorUiState) {
        val onServer = state.serverCurrent?.let {
            SkillFields(displayTitle = it.displayTitle?.ifBlank { null }, category = it.category?.ifBlank { null })
        } ?: state.baseline
        val request = UpdateSkillRequest(
            expectedVersion = state.loadedVersion,
            name = state.name,
            description = state.description,
            body = state.body,
            // The wire Json omits nulls, so a cleared field must go out as "" or PATCH keeps the
            // old value. Blank fields the server doesn't hold stay omitted. After a 409 the server
            // holds the other writer's values, and the re-save overwrites them.
            displayTitle = state.displayTitle.ifBlank { "".takeIf { onServer.displayTitle != null } },
            category = state.category.ifBlank { "".takeIf { onServer.category != null } },
            // Explicit boolean so toggling OFF on edit persists (server $set-merge
            // would otherwise keep a prior `true` if the field were omitted).
            alwaysApply = state.alwaysApply,
        )
        when (val result = skillsRepository.updateSkill(state.skillId!!, request)) {
            is SkillUpdateResult.Success -> emitSaved(state, result.skill)
            is SkillUpdateResult.Conflict -> onConflict(result.current)
            // The 409 didn't say what the server holds now, so read it: without its version every
            // re-save would send the same stale expectedVersion and 409 again. A failed read leaves
            // the error up, and the next Save reads again.
            SkillUpdateResult.ConflictWithoutCurrent -> when (val current = skillsRepository.getSkill(state.skillId)) {
                is Result.Success -> onConflict(current.data)
                is Result.Error, is Result.Loading -> onSaveFailed(SkillError(SkillErrorKind.CHANGED_ON_SERVER))
            }
            SkillUpdateResult.NameTaken -> onNameTaken()
            is SkillUpdateResult.Error -> onSaveFailed(skillError(SkillErrorKind.SAVE_SKILL, result.message))
        }
    }

    private suspend fun onConflict(current: Skill) {
        // KEEP the user's unsaved field edits — only adopt the server's new
        // version so a re-save targets the right expectedVersion. Never
        // clobber their work with server values. [serverCurrent] tells the
        // re-save which optional fields the server now holds (see saveUpdate).
        _uiState.value = _uiState.value.copy(
            isSaving = false,
            loadedVersion = current.version,
            serverCurrent = current,
            showConflictNotice = true,
        )
        _events.send(SkillEditorEvent.SaveFailed)
    }

    private suspend fun onSaveFailed(error: SkillError) {
        _uiState.value = _uiState.value.copy(isSaving = false, error = error)
        _events.send(SkillEditorEvent.SaveFailed)
    }
}

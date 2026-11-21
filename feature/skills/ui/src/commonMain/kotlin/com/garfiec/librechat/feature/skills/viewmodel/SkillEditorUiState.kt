package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillNameIssue
import com.garfiec.librechat.feature.skills.SkillValidation

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

    fun savedFields() = SkillFields(
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

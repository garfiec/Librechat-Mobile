package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.skills.SkillFilePaths

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
    /** The failed save was refused because the skill is being deleted. */
    val skillDeleting: Boolean = false,
    /** Set when the user asked to leave or reload with unsaved edits. */
    val discardPrompt: SkillFileDiscardTarget? = null,
    /** A save landed; the screen pops. */
    val saved: Boolean = false,
    /** The user may leave; the screen pops. */
    val exitRequested: Boolean = false,
) {
    val displayName: String get() = SkillFilePaths.displayName(filename, relativePath)

    val displayPath: String get() = SkillFilePaths.displayPath(filename, relativePath)

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

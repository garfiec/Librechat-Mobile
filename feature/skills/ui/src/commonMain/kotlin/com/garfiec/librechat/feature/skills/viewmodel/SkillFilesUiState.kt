package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.displayName

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

    /** What the replace prompt calls the file [pendingReplacement] would overwrite. */
    val pendingReplacementName: String?
        get() = pendingReplacement?.let { path -> files.firstOrNull { it.relativePath == path }?.displayName ?: path }
}

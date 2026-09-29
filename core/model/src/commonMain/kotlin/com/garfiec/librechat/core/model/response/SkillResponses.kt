package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.SkillWarning
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Body of a `409 skill_version_conflict` from `PATCH /api/skills/:id` (upstream
 * `TSkillConflictResponse`). [current] is the authoritative server state the
 * editor should rebase onto before retrying — never blindly overwrite.
 */
@Serializable
data class SkillConflictResponse(
    val error: String,
    val current: Skill,
)

/** Response of `DELETE /api/skills/:id` (upstream `{ id, deleted: true }`). */
@Serializable
data class DeleteSkillResponse(
    val id: String,
    val deleted: Boolean = false,
)

/**
 * Body of a `400 Validation failed` from create/update (upstream
 * `{ error, issues: ValidationIssue[] }`). [issues] carries field-level
 * messages — e.g. reserved skill-name prefix/word violations the client's
 * kebab/length checks don't catch. `ValidationIssue` is shape-compatible with
 * [SkillWarning] (field/code/message/severity).
 */
@Serializable
data class SkillValidationErrorResponse(
    val error: String? = null,
    val issues: List<SkillWarning> = emptyList(),
)

/**
 * Body of a failed archive import from `POST /api/skills/import` (v0.8.8-rc4, upstream
 * `TSkillImportFailedResponse`).
 *
 * rc4 made the import atomic: a bundled file that cannot be persisted fails the whole import
 * instead of answering 201 with a silently incomplete skill. [error] says what was left behind —
 * [INCOMPLETE] (422) rolled everything back; [ROLLBACK_FAILED] (500) could not remove the partial
 * skill, so it may still be listed and needs deleting by hand ([skillId]); [CLEANUP_INCOMPLETE]
 * (500) removed the skill row but not all of its dependent data. The list has to be reloaded on
 * all three, because the first two can each leave the server's list different from the client's.
 */
@Serializable
data class SkillImportFailedResponse(
    val error: String,
    val message: String? = null,
    val failedFiles: List<SkillImportFailedFile> = emptyList(),
    val skillId: String? = null,
) {
    companion object {
        const val INCOMPLETE = "skill_import_incomplete"
        const val ROLLBACK_FAILED = "skill_import_rollback_failed"
        const val CLEANUP_INCOMPLETE = "skill_import_cleanup_incomplete"

        private val CODES = setOf(INCOMPLETE, ROLLBACK_FAILED, CLEANUP_INCOMPLETE)

        // `coerceInputValues` turns a null entry field into its default, so one malformed entry
        // renders generically instead of failing the whole report — which would also skip the
        // reload that surfaces a partial skill left behind by a failed rollback.
        private val parser = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        /**
         * The failure report in an import's error body, or null when the body is anything else —
         * a validation 400, a pre-rc4 server, a gateway page. Only the three import codes count:
         * the same `error` key carries unrelated sentences on other failures.
         */
        fun from(body: String?): SkillImportFailedResponse? {
            if (body.isNullOrBlank()) return null
            val parsed = runCatching { parser.decodeFromString(serializer(), body) }.getOrNull()
            return parsed?.takeIf { it.error in CODES }
        }
    }
}

/**
 * One archive entry the import could not persist. [reason] is a stable code the client localizes
 * (see [SkillImportFailureReason]); [limitMb] is set when the reason names a size limit. Both
 * strings default to empty, which renders as the generic reason, as upstream renders each entry on
 * its own rather than rejecting the report.
 */
@Serializable
data class SkillImportFailedFile(
    val path: String = "",
    val reason: String = "",
    val limitMb: Double? = null,
)

/** Upstream `SkillImportFailureReason`. An unknown reason must still render, generically. */
object SkillImportFailureReason {
    const val INVALID_PATH = "invalid_path"
    const val FILE_TOO_LARGE = "file_too_large"
    const val ARCHIVE_TOO_LARGE = "archive_too_large"
    const val ARCHIVE_ENTRY_CHANGED = "archive_entry_changed"
    const val PERSISTENCE_FAILED = "persistence_failed"
}

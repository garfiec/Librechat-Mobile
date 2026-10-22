package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.request.CreateSkillRequest
import com.garfiec.librechat.core.model.request.UpdateSkillRequest
import com.garfiec.librechat.core.model.response.DeleteSkillFileResponse
import com.garfiec.librechat.core.model.response.DeleteSkillResponse
import com.garfiec.librechat.core.model.response.SkillFileContentResponse
import com.garfiec.librechat.core.model.response.SkillListResponse

/** CRUD access to the Skills catalog. */
interface SkillsRepository {
    suspend fun listSkills(
        limit: Int = 100,
        search: String? = null,
        category: String? = null,
        cursor: String? = null,
    ): Result<SkillListResponse>

    suspend fun getSkill(id: String): Result<Skill>

    /** Creates a skill. A taken name comes back as [SkillCreateResult.NameTaken]. */
    suspend fun createSkill(request: CreateSkillRequest): SkillCreateResult

    /**
     * PATCHes a skill with optimistic concurrency. A 409 carries one of two bodies, told apart by
     * its `error`: `skill_version_conflict` is [SkillUpdateResult.Conflict] with the authoritative
     * current skill so the caller can rebase the editor onto it rather than clobbering; a rename
     * onto a taken name is [SkillUpdateResult.NameTaken].
     */
    suspend fun updateSkill(id: String, request: UpdateSkillRequest): SkillUpdateResult

    suspend fun deleteSkill(id: String): Result<DeleteSkillResponse>

    /** Per-user skill active/inactive overrides (`GET .../skills/active`). */
    suspend fun getSkillStates(): Result<Map<String, Boolean>>

    /**
     * Persists active/inactive overrides. The POST is a FULL replace, and the server prunes keys
     * for skills that are gone or no longer visible: adopt the returned map, not the one sent.
     */
    suspend fun updateSkillStates(states: Map<String, Boolean>): SkillStatesUpdateResult

    /** The caller's ACL bits per skill id (`GET /api/permissions/skill/effective/all`). Deployment
     *  skills are never in it. */
    suspend fun getSkillEffectivePermissions(): Result<Map<String, Int>>

    // --- Skill files (flat) ---

    suspend fun listSkillFiles(skillId: String): Result<List<SkillFile>>

    suspend fun uploadSkillFile(
        skillId: String,
        relativePath: String,
        bytes: ByteArray,
        filename: String,
        mimeType: String,
    ): Result<SkillFile>

    suspend fun deleteSkillFile(skillId: String, relativePath: String): Result<DeleteSkillFileResponse>

    /** One file's content and (v0.8.8) its revision `fileId`. */
    suspend fun getSkillFileContent(skillId: String, relativePath: String): Result<SkillFileContentResponse>

    /**
     * One file's stored bytes (`?raw=true`): for the images and the over-cap text the JSON route
     * sends without `content`.
     */
    suspend fun getSkillFileRaw(skillId: String, relativePath: String): Result<ByteArray>

    /** At most the first [maxBytes] of [getSkillFileRaw], read without holding the rest of the file. */
    suspend fun getSkillFileRawPrefix(skillId: String, relativePath: String, maxBytes: Long): Result<ByteArray>

    /** Conditional in-place replace (v0.8.8). See [SkillFileEditResult]. */
    suspend fun editSkillFile(
        skillId: String,
        relativePath: String,
        expectedFileId: String,
        content: String,
        filename: String,
        mimeType: String,
    ): SkillFileEditResult

    /** Imports a skill from a .md/.zip/.skill upload. Surfaces server `issues`
     *  validation messages in [Result.Error.message] like [createSkill]. */
    suspend fun importSkill(bytes: ByteArray, filename: String, mimeType: String): Result<Skill>
}

/** Outcome of a skill PATCH that distinguishes the version-conflict case. */
sealed interface SkillUpdateResult {
    data class Success(val skill: Skill) : SkillUpdateResult

    /** 409: the editor's [UpdateSkillRequest.expectedVersion] was stale. [current]
     *  is the authoritative server state to rebase onto. */
    data class Conflict(val current: Skill) : SkillUpdateResult

    /** 409 version conflict whose `current` didn't decode: the editor can't rebase, the user reloads. */
    data object ConflictWithoutCurrent : SkillUpdateResult

    /** 409: another skill already has the requested name. */
    data object NameTaken : SkillUpdateResult

    /** [message] is the server's or transport's text when there is one; the UI words the rest. */
    data class Error(val message: String?) : SkillUpdateResult
}

/** Outcome of an on/off overrides POST that distinguishes the server's cap on them. */
sealed interface SkillStatesUpdateResult {
    /** The pruned map the server stored. */
    data class Success(val states: Map<String, Boolean>) : SkillStatesUpdateResult

    /**
     * 400 `MAX_SKILL_STATES_EXCEEDED` or `SKILL_STATES_PAYLOAD_TOO_LARGE` (upstream
     * `SkillStatesController.js`, `skillStates.ts`): too many skills are set away from their
     * default for the server to store another.
     */
    data object TooManyOverrides : SkillStatesUpdateResult

    /** [message] is the server's or transport's text when there is one; the UI words the rest. */
    data class Error(val message: String?) : SkillStatesUpdateResult
}

/** Outcome of a skill create that distinguishes a taken name. */
sealed interface SkillCreateResult {
    data class Success(val skill: Skill) : SkillCreateResult

    /** 409: another skill already has this name. */
    data object NameTaken : SkillCreateResult

    data class Error(val message: String?) : SkillCreateResult
}

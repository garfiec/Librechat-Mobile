package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.apiCallCatching
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.common.result.toSafeError
import com.garfiec.librechat.core.model.Skill
import com.garfiec.librechat.core.model.SkillFile
import com.garfiec.librechat.core.model.request.CreateSkillRequest
import com.garfiec.librechat.core.model.request.UpdateSkillRequest
import com.garfiec.librechat.core.model.response.DeleteSkillFileResponse
import com.garfiec.librechat.core.model.response.DeleteSkillResponse
import com.garfiec.librechat.core.model.response.SkillConflictResponse
import com.garfiec.librechat.core.model.response.SkillFileContentResponse
import com.garfiec.librechat.core.model.response.SkillListResponse
import com.garfiec.librechat.core.model.response.SkillValidationErrorResponse
import com.garfiec.librechat.core.network.api.SkillsApi
import kotlinx.serialization.json.Json

/**
 * Create, update, upload, edit and import map their own failures — the server's `issues` array
 * carries validation detail (reserved name prefix, path traversal, a name conflict) that a generic
 * message would throw away — so they use `apiCallCatching` rather than `safeApiCall`. Anything that
 * isn't a validation or conflict answer still goes through `toSafeError`.
 */
class SkillsRepositoryImpl(
    private val skillsApi: SkillsApi,
    private val json: Json,
) : SkillsRepository {

    override suspend fun listSkills(
        limit: Int,
        search: String?,
        category: String?,
        cursor: String?,
    ): Result<SkillListResponse> =
        safeApiCall { skillsApi.listSkills(limit, search, category, cursor) }

    override suspend fun getSkill(id: String): Result<Skill> =
        safeApiCall { skillsApi.getSkill(id) }

    override suspend fun createSkill(request: CreateSkillRequest): Result<Skill> =
        apiCallCatching({ Result.Success(skillsApi.createSkill(request)) }, ::toValidationError)

    override suspend fun updateSkill(id: String, request: UpdateSkillRequest): SkillUpdateResult =
        apiCallCatching({ SkillUpdateResult.Success(skillsApi.updateSkill(id, request)) }) { e ->
            if (e is ApiException && e.statusCode == HTTP_CONFLICT) {
                parseConflict(e)
            } else {
                SkillUpdateResult.Error(displayMessage(e))
            }
        }

    override suspend fun deleteSkill(id: String): Result<DeleteSkillResponse> =
        safeApiCall { skillsApi.deleteSkill(id) }

    override suspend fun getSkillStates(): Result<Map<String, Boolean>> =
        safeApiCall { skillsApi.getSkillStates() }

    override suspend fun updateSkillStates(states: Map<String, Boolean>): Result<Map<String, Boolean>> =
        safeApiCall { skillsApi.updateSkillStates(states) }

    override suspend fun listSkillFiles(skillId: String): Result<List<SkillFile>> =
        safeApiCall { skillsApi.listSkillFiles(skillId).files }

    override suspend fun uploadSkillFile(
        skillId: String,
        relativePath: String,
        bytes: ByteArray,
        filename: String,
        mimeType: String,
    ): Result<SkillFile> = apiCallCatching(
        { Result.Success(skillsApi.uploadSkillFile(skillId, relativePath, bytes, filename, mimeType)) },
        ::toValidationError,
    )

    override suspend fun deleteSkillFile(skillId: String, relativePath: String): Result<DeleteSkillFileResponse> =
        safeApiCall { skillsApi.deleteSkillFile(skillId, relativePath) }

    override suspend fun getSkillFileContent(
        skillId: String,
        relativePath: String,
    ): Result<SkillFileContentResponse> = safeApiCall { skillsApi.getSkillFileContent(skillId, relativePath) }

    override suspend fun editSkillFile(
        skillId: String,
        relativePath: String,
        expectedFileId: String,
        content: String,
        filename: String,
        mimeType: String,
    ): SkillFileEditResult = apiCallCatching({
        SkillFileEditResult.Saved(
            skillsApi.editSkillFile(skillId, relativePath, expectedFileId, content.encodeToByteArray(), filename, mimeType),
        )
    }) { e ->
        if (e is ApiException && e.statusCode == HTTP_CONFLICT) {
            SkillFileEditResult.Conflict
        } else {
            SkillFileEditResult.Failed(displayMessage(e))
        }
    }

    override suspend fun importSkill(bytes: ByteArray, filename: String, mimeType: String): Result<Skill> =
        apiCallCatching({ Result.Success(skillsApi.importSkill(bytes, filename, mimeType)) }, ::toValidationError)

    private fun toValidationError(e: Exception): Result.Error =
        (e as? ApiException)?.let(::validationMessage)?.let { Result.Error(e, it) } ?: e.toSafeError()

    private fun displayMessage(e: Exception): String =
        (e as? ApiException)?.let(::validationMessage) ?: e.toSafeError().message.orEmpty()

    /**
     * Decodes the authoritative [Skill] from the 409 `skill_version_conflict`
     * body so the editor can rebase onto the server's current version without a
     * second round-trip. Falls back to a generic conflict if the body is absent
     * or unparseable (then the caller can't bump the version and the user retries).
     */
    private fun parseConflict(e: ApiException): SkillUpdateResult {
        val current = e.body?.let {
            runCatching { json.decodeFromString(SkillConflictResponse.serializer(), it).current }.getOrNull()
        }
        return if (current != null) {
            SkillUpdateResult.Conflict(current)
        } else {
            SkillUpdateResult.Error("This skill was changed on the server. Reload and try again.")
        }
    }

    /** Joins a 400 `{ error, issues }` body's issue messages into one line, or null. */
    private fun validationMessage(e: ApiException): String? {
        val body = e.body ?: return null
        val parsed = runCatching {
            json.decodeFromString(SkillValidationErrorResponse.serializer(), body)
        }.getOrNull() ?: return null
        val issueText = parsed.issues.mapNotNull { it.message.ifBlank { null } }
        return issueText.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}

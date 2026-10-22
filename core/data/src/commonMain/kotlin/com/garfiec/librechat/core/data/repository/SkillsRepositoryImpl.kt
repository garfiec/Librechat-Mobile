package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.apiCallCatching
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.common.result.toSafeError
import com.garfiec.librechat.core.model.ResourceType
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Create, update, upload, edit and import map their own failures — the server's `issues` array
 * carries validation detail (reserved name prefix, path traversal, a name conflict) that a generic
 * message would throw away — so they use `apiCallCatching` rather than `safeApiCall`. Anything that
 * isn't a validation or conflict answer still goes through `toSafeError`.
 */
class SkillsRepositoryImpl(
    private val skillsApi: SkillsApi,
    private val permissionsRepository: PermissionsRepository,
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

    override suspend fun createSkill(request: CreateSkillRequest): SkillCreateResult =
        apiCallCatching({ SkillCreateResult.Success(skillsApi.createSkill(request)) }) { e ->
            when (conflictOf(e)) {
                SkillConflict.NameTaken -> SkillCreateResult.NameTaken
                else -> SkillCreateResult.Error(toValidationError(e).message)
            }
        }

    override suspend fun updateSkill(id: String, request: UpdateSkillRequest): SkillUpdateResult =
        apiCallCatching({ SkillUpdateResult.Success(skillsApi.updateSkill(id, request)) }) { e ->
            when (val conflict = conflictOf(e)) {
                is SkillConflict.Version -> conflict.current?.let(SkillUpdateResult::Conflict)
                    ?: SkillUpdateResult.ConflictWithoutCurrent
                SkillConflict.NameTaken -> SkillUpdateResult.NameTaken
                SkillConflict.Other, null -> SkillUpdateResult.Error(displayMessage(e))
            }
        }

    override suspend fun deleteSkill(id: String): Result<DeleteSkillResponse> =
        safeApiCall { skillsApi.deleteSkill(id) }

    override suspend fun getSkillStates(): Result<Map<String, Boolean>> =
        safeApiCall { skillsApi.getSkillStates() }

    override suspend fun updateSkillStates(states: Map<String, Boolean>): SkillStatesUpdateResult =
        apiCallCatching({ SkillStatesUpdateResult.Success(skillsApi.updateSkillStates(states)) }) { e ->
            statesCapOf(e) ?: SkillStatesUpdateResult.Error(e.toSafeError().message)
        }

    override suspend fun getSkillEffectivePermissions(): Result<Map<String, Int>> =
        permissionsRepository.getAllEffectivePermissions(ResourceType.SKILL)

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

    override suspend fun getSkillFileRaw(skillId: String, relativePath: String): Result<ByteArray> =
        safeApiCall { skillsApi.getSkillFileRaw(skillId, relativePath) }

    override suspend fun getSkillFileRawPrefix(skillId: String, relativePath: String, maxBytes: Long): Result<ByteArray> =
        safeApiCall { skillsApi.getSkillFileRawPrefix(skillId, relativePath, maxBytes) }

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

    private fun displayMessage(e: Exception): String? =
        (e as? ApiException)?.let(::validationMessage) ?: e.toSafeError().message

    /** Which 409 [e] is, or null when it isn't a 409. */
    private fun conflictOf(e: Exception): SkillConflict? =
        (e as? ApiException)?.takeIf { it.statusCode == HTTP_CONFLICT }?.let(::parseConflict)

    /**
     * Tells the two 409 bodies apart by their `error` (upstream `handlers.ts`): a version conflict
     * carries the authoritative [Skill] so the editor can rebase without a second round-trip; a
     * duplicate name is a plain sentence. Anything else stays [SkillConflict.Other], so a future
     * 409 never lands on the Name field. A version conflict whose `current` doesn't decode keeps a
     * null current ([SkillUpdateResult.ConflictWithoutCurrent]: the user reloads).
     */
    private fun parseConflict(e: ApiException): SkillConflict {
        val body = e.body?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val error = (body?.get("error") as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when (error) {
            SkillConflictResponse.VERSION_CONFLICT -> SkillConflict.Version(
                runCatching { json.decodeFromString(SkillConflictResponse.serializer(), e.body.orEmpty()).current }
                    .getOrNull(),
            )
            SkillConflictResponse.NAME_TAKEN -> SkillConflict.NameTaken
            else -> SkillConflict.Other
        }
    }

    /** The states POST's 400 for too many overrides, by its `code`; null for any other failure. */
    private fun statesCapOf(e: Exception): SkillStatesUpdateResult.TooManyOverrides? {
        val api = (e as? ApiException)?.takeIf { it.statusCode == HTTP_BAD_REQUEST } ?: return null
        val body = api.body?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        val code = (body?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.content
        return SkillStatesUpdateResult.TooManyOverrides.takeIf { code in STATES_CAP_CODES }
    }

    private sealed interface SkillConflict {
        data class Version(val current: Skill?) : SkillConflict
        data object NameTaken : SkillConflict
        data object Other : SkillConflict
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
        const val HTTP_BAD_REQUEST = 400

        /** `code`s of the states POST's refusals for too many overrides (upstream `SkillStatesController.js`,
         *  `skillStates.ts`). */
        val STATES_CAP_CODES = setOf("MAX_SKILL_STATES_EXCEEDED", "SKILL_STATES_PAYLOAD_TOO_LARGE")
    }
}

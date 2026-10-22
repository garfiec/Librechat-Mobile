package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.request.CreateSkillRequest
import com.garfiec.librechat.core.model.request.UpdateSkillRequest
import com.garfiec.librechat.core.network.api.SkillsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * A 409 from the skills routes carries one of two bodies (upstream `handlers.ts`): a stale
 * `expectedVersion` and a taken name. Reading every 409 as a version conflict told a user who
 * picked a taken name that someone else had changed their skill.
 */
class SkillsRepositoryConflictTest {

    private val api = mockk<SkillsApi>()
    private val permissions = mockk<PermissionsRepository>()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val repository = SkillsRepositoryImpl(api, permissions, json)

    private val update = UpdateSkillRequest(expectedVersion = 2, name = "taken")

    private fun conflict(body: String) = ApiException(statusCode = 409, message = "Conflict", body = body)

    @Test
    fun `a version conflict rebases onto the current skill`() = runTest {
        coEvery { api.updateSkill("sk", update) } throws conflict(
            """{"error":"skill_version_conflict","current":{"_id":"sk","name":"n","description":"d","version":5}}""",
        )

        val result = repository.updateSkill("sk", update)

        assertThat(result).isInstanceOf(SkillUpdateResult.Conflict::class.java)
        assertThat((result as SkillUpdateResult.Conflict).current.version).isEqualTo(5)
    }

    @Test
    fun `a rename onto a taken name is not a version conflict`() = runTest {
        coEvery { api.updateSkill("sk", update) } throws
            conflict("""{"error":"A skill with this name already exists"}""")

        assertThat(repository.updateSkill("sk", update)).isEqualTo(SkillUpdateResult.NameTaken)
    }

    @Test
    fun `a create with a taken name says so`() = runTest {
        val create = CreateSkillRequest(name = "taken", description = "d")
        coEvery { api.createSkill(create) } throws conflict("""{"error":"A skill with this name already exists"}""")

        assertThat(repository.createSkill(create)).isEqualTo(SkillCreateResult.NameTaken)
    }

    @Test
    fun `an unknown 409 is a plain error, never the Name field's`() = runTest {
        coEvery { api.updateSkill("sk", update) } throws conflict("""{"error":"something_new"}""")

        assertThat(repository.updateSkill("sk", update)).isInstanceOf(SkillUpdateResult.Error::class.java)
    }

    @Test
    fun `a version conflict without a readable current says so without wording it`() = runTest {
        coEvery { api.updateSkill("sk", update) } throws conflict("""{"error":"skill_version_conflict"}""")

        assertThat(repository.updateSkill("sk", update)).isEqualTo(SkillUpdateResult.ConflictWithoutCurrent)
    }

    @Test
    fun `effective permissions are read for the skill resource type`() = runTest {
        coEvery { permissions.getAllEffectivePermissions(ResourceType.SKILL) } returns
            Result.Success(mapOf("sk" to 15))

        assertThat((repository.getSkillEffectivePermissions() as Result.Success).data).containsExactly("sk", 15)
    }

    private fun badRequest(body: String) = ApiException(
        statusCode = 400,
        message = "Maximum 200 skill state overrides allowed",
        body = body,
        serverAuthored = true,
    )

    @Test
    fun `a states POST over the overrides cap is told apart from other failures`() = runTest {
        coEvery { api.updateSkillStates(any()) } throws badRequest(
            """{"code":"MAX_SKILL_STATES_EXCEEDED","message":"Maximum 200 skill state overrides allowed","limit":200}""",
        )

        assertThat(repository.updateSkillStates(mapOf("a" to true)))
            .isEqualTo(SkillStatesUpdateResult.TooManyOverrides)
    }

    @Test
    fun `a states POST over the raw payload cap reads as the same refusal`() = runTest {
        coEvery { api.updateSkillStates(any()) } throws badRequest(
            """{"code":"SKILL_STATES_PAYLOAD_TOO_LARGE","message":"Payload exceeds 400 entries","limit":400}""",
        )

        assertThat(repository.updateSkillStates(mapOf("a" to true)))
            .isEqualTo(SkillStatesUpdateResult.TooManyOverrides)
    }

    @Test
    fun `any other states POST failure keeps the server's text`() = runTest {
        coEvery { api.updateSkillStates(any()) } throws ApiException(
            statusCode = 400,
            message = "skillStates must be a plain object",
            body = """{"message":"skillStates must be a plain object"}""",
            serverAuthored = true,
        )

        assertThat(repository.updateSkillStates(mapOf("a" to true)))
            .isEqualTo(SkillStatesUpdateResult.Error("skillStates must be a plain object"))
    }

    @Test
    fun `a states POST that lands returns the stored map`() = runTest {
        coEvery { api.updateSkillStates(mapOf("a" to true, "gone" to false)) } returns mapOf("a" to true)

        assertThat(repository.updateSkillStates(mapOf("a" to true, "gone" to false)))
            .isEqualTo(SkillStatesUpdateResult.Success(mapOf("a" to true)))
    }
}

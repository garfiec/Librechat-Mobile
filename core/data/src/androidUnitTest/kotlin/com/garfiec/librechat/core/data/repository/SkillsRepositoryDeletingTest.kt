package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.network.api.SkillsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/** A skill-file write during a durable skill delete answers 409 `{ error: "SKILL_DELETING" }` (v0.8.9-rc1). */
class SkillsRepositoryDeletingTest {

    private val api = mockk<SkillsApi>()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val repository = SkillsRepositoryImpl(api, mockk(), json)

    private val deleting = ApiException(statusCode = 409, message = "Conflict", body = """{"error":"SKILL_DELETING"}""")

    @Test
    fun `an upload to a skill being deleted says so`() = runTest {
        coEvery { api.uploadSkillFile("sk", "a.txt", any(), "a.txt", "text/plain") } throws deleting

        val result = repository.uploadSkillFile("sk", "a.txt", ByteArray(1), "a.txt", "text/plain")

        assertThat((result as Result.Error).exception).isInstanceOf(SkillDeletingException::class.java)
        assertThat(result.message).isNull()
    }

    @Test
    fun `a file delete on a skill being deleted says so`() = runTest {
        coEvery { api.deleteSkillFile("sk", "a.txt") } throws deleting

        val result = repository.deleteSkillFile("sk", "a.txt")

        assertThat((result as Result.Error).exception).isInstanceOf(SkillDeletingException::class.java)
    }

    @Test
    fun `an edit on a skill being deleted is not a revision conflict`() = runTest {
        coEvery { api.editSkillFile("sk", "a.txt", "f1", any(), "a.txt", "text/plain") } throws deleting

        assertThat(repository.editSkillFile("sk", "a.txt", "f1", "x", "a.txt", "text/plain"))
            .isEqualTo(SkillFileEditResult.SkillDeleting)
    }

    @Test
    fun `a revision conflict stays a conflict`() = runTest {
        coEvery { api.editSkillFile("sk", "a.txt", "f1", any(), "a.txt", "text/plain") } throws
            ApiException(statusCode = 409, message = "Conflict", body = """{"error":"SKILL_FILE_CONFLICT"}""")

        assertThat(repository.editSkillFile("sk", "a.txt", "f1", "x", "a.txt", "text/plain"))
            .isEqualTo(SkillFileEditResult.Conflict)
    }

    @Test
    fun `another upload failure is not read as a delete`() = runTest {
        coEvery { api.uploadSkillFile("sk", "a.txt", any(), "a.txt", "text/plain") } throws
            ApiException(statusCode = 409, message = "Conflict", body = """{"error":"SKILL_FILE_CONFLICT"}""")

        val result = repository.uploadSkillFile("sk", "a.txt", ByteArray(1), "a.txt", "text/plain")

        assertThat((result as Result.Error).exception).isNotInstanceOf(SkillDeletingException::class.java)
    }
}

package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectContextFailureTest {

    private fun error(status: Int, body: String?) =
        Result.Error(ApiException(statusCode = status, message = "x", body = body))

    @Test
    fun coded_409_on_save_is_a_conflict() {
        val body = """{"message":"Project was changed by another session","code":"CHAT_PROJECT_CONFLICT"}"""
        assertEquals(ProjectContextFailure.Conflict, error(409, body).toProjectContextFailure())
    }

    @Test
    fun uncoded_409_on_save_is_not_a_conflict() {
        // The prose under `error` must never be read as a code.
        val body = """{"error":"CHAT_PROJECT_CONFLICT"}"""
        assertEquals(ProjectContextFailure.Other, error(409, body).toProjectContextFailure())
    }

    @Test
    fun save_statuses_map() {
        assertEquals(ProjectContextFailure.NotFound, error(404, """{"error":"Project not found"}""").toProjectContextFailure())
        assertEquals(ProjectContextFailure.Invalid, error(400, null).toProjectContextFailure())
        assertEquals(ProjectContextFailure.Other, error(500, null).toProjectContextFailure())
        assertEquals(ProjectContextFailure.Other, Result.Error(IllegalStateException()).toProjectContextFailure())
    }

    @Test
    fun any_409_on_add_is_the_file_limit() {
        assertEquals(
            ProjectContextFailure.FileLimit,
            error(409, """{"error":"Project file limit reached"}""").toProjectFileAddFailure(),
        )
        assertEquals(ProjectContextFailure.Invalid, error(400, null).toProjectFileAddFailure())
        assertEquals(ProjectContextFailure.NotFound, error(404, null).toProjectFileAddFailure())
    }
}

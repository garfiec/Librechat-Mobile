package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.error.ServerErrorCode

/**
 * Why a project instructions save or a file add/remove failed (v0.8.9-rc1). The two 409s on these
 * routes are different things and are told apart by route, not by text: the PATCH answers
 * `{ message, code: "CHAT_PROJECT_CONFLICT" }` for a stale revision, while `POST /:id/files` answers a
 * bare `{ error: "Project file limit reached" }` at the cap.
 */
sealed interface ProjectContextFailure {
    /** The instructions changed on another device since the draft's revision. */
    data object Conflict : ProjectContextFailure

    /** The project is gone (404). The screens showing it close. */
    data object NotFound : ProjectContextFailure

    /** The project already holds as many reference files as the server allows. */
    data object FileLimit : ProjectContextFailure

    /** Refused as invalid (400): over a length limit, or a file that can't be a project file. */
    data object Invalid : ProjectContextFailure

    /** Anything else: offline, a 5xx, an unexpected status. */
    data object Other : ProjectContextFailure
}

/** Upstream's 409 code for an instructions save based on a stale `contextRevision`. */
const val CHAT_PROJECT_CONFLICT_CODE = "CHAT_PROJECT_CONFLICT"

/**
 * Reads a failed instructions save. Only a 409 that carries [CHAT_PROJECT_CONFLICT_CODE] is a
 * conflict; the code is read with `generationCodeOf`, which never falls back to the `error` prose.
 */
fun Result.Error.toProjectContextFailure(): ProjectContextFailure {
    val api = exception as? ApiException ?: return ProjectContextFailure.Other
    return when (api.statusCode) {
        HTTP_NOT_FOUND -> ProjectContextFailure.NotFound
        HTTP_BAD_REQUEST -> ProjectContextFailure.Invalid
        HTTP_CONFLICT ->
            if (ServerErrorCode.generationCodeOf(api.body) == CHAT_PROJECT_CONFLICT_CODE) {
                ProjectContextFailure.Conflict
            } else {
                ProjectContextFailure.Other
            }
        else -> ProjectContextFailure.Other
    }
}

/** Reads a failed file add: on that route a 409 is the file limit, whatever its text says. */
fun Result.Error.toProjectFileAddFailure(): ProjectContextFailure {
    val api = exception as? ApiException ?: return ProjectContextFailure.Other
    return when (api.statusCode) {
        HTTP_NOT_FOUND -> ProjectContextFailure.NotFound
        HTTP_BAD_REQUEST -> ProjectContextFailure.Invalid
        HTTP_CONFLICT -> ProjectContextFailure.FileLimit
        else -> ProjectContextFailure.Other
    }
}

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409

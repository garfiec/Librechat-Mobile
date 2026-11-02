package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Why `POST /api/convos/update` refused a rename (v0.8.9-rc1, upstream `conversations/rename.ts`). */
enum class RenameRefusal(val code: String) {
    /**
     * 409: during a rolling upgrade (`interface.runningChatRename: false`) the server takes a
     * rename only once the chat's title has been generated and no run is going. Retryable.
     */
    TITLE_NOT_READY("conversation_title_ownership_not_ready"),

    /** 404: the conversation is gone. */
    NOT_FOUND("conversation_not_found"),
}

class RenameRefusedException(val refusal: RenameRefusal, cause: Throwable? = null) : Exception(refusal.code, cause)

/** The reason a rename was refused, or null for any other failure. */
fun Result.Error.renameRefusal(): RenameRefusal? = (exception as? RenameRefusedException)?.refusal

private val renameJson = Json { ignoreUnknownKeys = true }

internal fun renameRefusalOf(e: Exception): RenameRefusal? {
    val api = e as? ApiException ?: return null
    val body = api.body?.let { runCatching { renameJson.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
    val code = (body["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return RenameRefusal.entries.firstOrNull { it.code == code }
}

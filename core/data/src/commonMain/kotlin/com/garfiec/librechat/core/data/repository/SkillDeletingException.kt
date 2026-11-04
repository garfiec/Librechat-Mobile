package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A skill-file write refused because the skill is being deleted (v0.8.9-rc1): the server's durable
 * delete answers a file upload, edit or delete with 409 `{ error: "SKILL_DELETING" }` while it
 * removes the skill. Carried as the [com.garfiec.librechat.core.common.result.Result.Error]
 * exception so a page can say so instead of showing the bare code.
 */
class SkillDeletingException(cause: Throwable? = null) : Exception(SKILL_DELETING, cause) {
    companion object {
        /** Upstream's `error` code (`packages/api/src/skills/upload.ts`, `handlers.ts`). */
        const val SKILL_DELETING = "SKILL_DELETING"
    }
}

/** Whether [e] is the 409 `{ error: "SKILL_DELETING" }` a skill-file write gets during a delete. */
internal fun isSkillDeleting(e: Exception, json: Json): Boolean {
    val api = e as? ApiException ?: return false
    if (api.statusCode != HTTP_CONFLICT) return false
    val body = api.body?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return false
    return (body["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content == SkillDeletingException.SKILL_DELETING
}

private const val HTTP_CONFLICT = 409

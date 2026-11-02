package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.acl.PermissionUpdateFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A permission update refused with one of the server's retryable codes. Carried as the
 * [Result.Error] exception of `PermissionsRepository.updateResourcePermissions`; read it through
 * [permissionUpdateFailure].
 */
class PermissionUpdateRefusedException(
    val failure: PermissionUpdateFailure,
    cause: Throwable? = null,
) : Exception(failure.code, cause)

/** The retryable reason a permission update was refused, or null for any other failure. */
fun Result.Error.permissionUpdateFailure(): PermissionUpdateFailure? =
    (exception as? PermissionUpdateRefusedException)?.failure

private val codeJson = Json { ignoreUnknownKeys = true }

/** The approved `code` on a failed update's body (`{ error, details, code }`), whatever its status. */
internal fun permissionFailureOf(e: Exception): PermissionUpdateFailure? {
    val body = (e as? ApiException)?.body ?: return null
    val obj = runCatching { codeJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
    val code = (obj["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return PermissionUpdateFailure.fromCode(code)
}

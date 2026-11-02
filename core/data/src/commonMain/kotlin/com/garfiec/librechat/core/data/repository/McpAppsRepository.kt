package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.apiCallCatching
import com.garfiec.librechat.core.network.api.McpAppsApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * The MCP App routes, run natively on an App's behalf (the App's WebView never holds a token).
 * Network-direct: nothing here is cached, and App documents are never persisted.
 */
interface McpAppsRepository {
    suspend fun validate(serverName: String, serverBinding: String): McpAppResult<Unit>
    suspend fun readResource(serverName: String, serverBinding: String, uri: String): McpAppResult<JsonObject>
    suspend fun listResources(serverName: String, serverBinding: String, cursor: String?): McpAppResult<JsonObject>
    suspend fun listResourceTemplates(
        serverName: String,
        serverBinding: String,
        cursor: String?,
    ): McpAppResult<JsonObject>
    suspend fun callTool(
        serverName: String,
        serverBinding: String,
        toolName: String,
        arguments: JsonObject,
    ): McpAppResult<JsonObject>
}

sealed interface McpAppResult<out T> {
    data class Success<T>(val data: T) : McpAppResult<T>
    data class Failure(val failure: McpAppFailure) : McpAppResult<Nothing>
}

/**
 * Why an App route failed, read from the status and the body's `error` code, never its message
 * (`packages/api/src/mcp/apps.ts`). A 403 is told apart by its body: Apps switched off, a rejected
 * bearer, or no MCP permission all share the status.
 */
enum class McpAppFailure {
    /** 400: a stale binding, a bad descriptor, or a tool the App may not call. Retrying won't help. */
    REJECTED,

    /** 403 `{error:"MCP Apps are disabled"}`: the server's policy turned Apps off. */
    APPS_DISABLED,

    /** 401 `{error:"invalid_token"}`: the MCP server's own sign-in needs renewing, not the session. */
    REAUTH_REQUIRED,

    /** Any other 403, typically no MCP permission. */
    FORBIDDEN,

    /** 429, 502, 503, 504: a limiter or the server's operation budget. Worth trying again later. */
    BUSY,

    /** No answer: offline, timed out, or not JSON. */
    UNAVAILABLE,

    /** Anything else (413, 500…). */
    OTHER,
    ;

    companion object {
        const val APPS_DISABLED_ERROR = "MCP Apps are disabled"
        const val INVALID_TOKEN_ERROR = "invalid_token"

        fun of(throwable: Throwable): McpAppFailure {
            val api = throwable as? ApiException ?: return UNAVAILABLE
            val code = errorCode(api.body)
            return when (api.statusCode) {
                400 -> REJECTED
                401 -> if (code == INVALID_TOKEN_ERROR) REAUTH_REQUIRED else OTHER
                403 -> if (code == APPS_DISABLED_ERROR) APPS_DISABLED else FORBIDDEN
                429, 502, 503, 504 -> BUSY
                else -> OTHER
            }
        }

        private fun errorCode(body: String?): String? {
            if (body.isNullOrBlank()) return null
            val obj = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            return (obj["error"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        }
    }
}

class McpAppsRepositoryImpl(private val api: McpAppsApi) : McpAppsRepository {

    override suspend fun validate(serverName: String, serverBinding: String): McpAppResult<Unit> =
        call {
            val answer = api.validate(serverName, serverBinding)
            // Anything but `valid: true` is a binding the App may not run under.
            if ((answer["valid"] as? JsonPrimitive)?.content != "true") throw ApiException(400, "invalid binding")
        }

    override suspend fun readResource(serverName: String, serverBinding: String, uri: String) =
        call { api.readResource(serverName, serverBinding, uri) }

    override suspend fun listResources(serverName: String, serverBinding: String, cursor: String?) =
        call { api.listResources(serverName, serverBinding, cursor) }

    override suspend fun listResourceTemplates(serverName: String, serverBinding: String, cursor: String?) =
        call { api.listResourceTemplates(serverName, serverBinding, cursor) }

    override suspend fun callTool(
        serverName: String,
        serverBinding: String,
        toolName: String,
        arguments: JsonObject,
    ) = call { api.callTool(serverName, serverBinding, toolName, arguments) }

    // Expected failures (a stale binding, a busy server) are reported to the App, not logged as errors.
    private suspend fun <T> call(block: suspend () -> T): McpAppResult<T> =
        apiCallCatching({ McpAppResult.Success(block()) }) { e ->
            val failure = McpAppFailure.of(e)
            Logger.w("McpApps") { "App route failed: $failure" }
            McpAppResult.Failure(failure)
        }
}

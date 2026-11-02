package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import kotlin.test.Test
import kotlin.test.assertEquals

/** The App routes' failures, read from status and `error` code (`packages/api/src/mcp/apps.ts`). */
class McpAppFailureTest {

    private fun failure(status: Int, body: String?) = McpAppFailure.of(ApiException(status, "x", body = body))

    @Test
    fun `a 403 is told apart by its body`() {
        assertEquals(McpAppFailure.APPS_DISABLED, failure(403, """{"error":"MCP Apps are disabled"}"""))
        assertEquals(McpAppFailure.FORBIDDEN, failure(403, """{"error":"Forbidden"}"""))
        assertEquals(McpAppFailure.FORBIDDEN, failure(403, "<html>blocked</html>"))
    }

    @Test
    fun `a 401 invalid_token asks for the MCP server's sign-in any other 401 does not`() {
        assertEquals(McpAppFailure.REAUTH_REQUIRED, failure(401, """{"error":"invalid_token"}"""))
        assertEquals(McpAppFailure.OTHER, failure(401, """{"message":"jwt expired"}"""))
    }

    @Test
    fun `a 400 is final whatever its message and limiter or budget answers are busy`() {
        assertEquals(McpAppFailure.REJECTED, failure(400, """{"error":"MCP App binding for server \"x\" is no longer valid."}"""))
        listOf(429, 502, 503, 504).forEach { assertEquals(McpAppFailure.BUSY, failure(it, """{"error":"busy"}""")) }
        assertEquals(McpAppFailure.OTHER, failure(500, """{"error":"Failed to execute tool"}"""))
    }

    @Test
    fun `no answer at all is unavailable`() {
        assertEquals(McpAppFailure.UNAVAILABLE, McpAppFailure.of(IllegalStateException("offline")))
    }
}

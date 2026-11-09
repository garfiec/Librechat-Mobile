package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpUserVar
import com.garfiec.librechat.core.network.api.McpApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class McpRepositoryImplTest {

    private val api = mockk<McpApi>(relaxUnitFun = true)
    private val repository = McpRepositoryImpl(api)

    @Test
    fun `a blank field is not sent, so it keeps the saved value`() = runTest {
        repository.saveUserVars("gh", mapOf("TOKEN" to "secret", "USER" to " "))

        coVerify { api.saveUserVars("gh", mapOf("TOKEN" to "secret")) }
    }

    @Test
    fun `a server is owed variables while any declared one is unset, and an unread one isn't counted`() = runTest {
        val vars = listOf(McpUserVar("TOKEN", "Token", ""), McpUserVar("USER", "User", ""))
        val mcp = mockk<McpRepository>()
        coEvery { mcp.getUserVarFlags("partial") } returns Result.Success(mapOf("TOKEN" to true, "USER" to false))
        coEvery { mcp.getUserVarFlags("done") } returns Result.Success(mapOf("TOKEN" to true, "USER" to true))
        coEvery { mcp.getUserVarFlags("unread") } returns Result.Error(message = "boom")

        val missing = mcp.serversMissingUserVars(
            listOf(
                McpServer("partial", customUserVars = vars),
                McpServer("done", customUserVars = vars),
                McpServer("unread", customUserVars = vars),
                McpServer("plain"),
            ),
        )

        assertThat(missing).containsExactly("partial")
    }
}

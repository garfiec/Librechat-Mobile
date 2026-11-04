package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class McpOAuthLauncherTest {

    private val mcp = mockk<McpRepository>()
    private val config = mockk<ConfigRepository>()

    private fun launcher(version: String?): McpOAuthLauncher {
        every { config.detectedBackendVersion } returns MutableStateFlow(version)
        every { config.serverUrl } returns flowOf("https://chat.example.com")
        return McpOAuthLauncher(mcp, config)
    }

    @Test
    fun `a 0_8_9-rc1 server binds and opens in app with the cookies`() = runTest {
        coEvery { mcp.bindOAuth("github") } returns Result.Success(listOf("oauth_csrf=a; Path=/api/mcp"))

        val launch = (launcher("0.8.9-rc1").prepare("github", "https://idp/authorize") as Result.Success).data

        assertThat(launch).isEqualTo(
            McpOAuthLaunch.InApp(
                serverName = "github",
                authorizationUrl = "https://idp/authorize",
                serverUrl = "https://chat.example.com",
                seedCookies = listOf("oauth_csrf=a; Path=/api/mcp"),
            ),
        )
    }

    @Test
    fun `an unknown version takes the bound path`() = runTest {
        coEvery { mcp.bindOAuth(any()) } returns Result.Success(listOf("oauth_session=s; Path=/api"))
        val launch = (launcher(null).prepare("github", "https://idp") as Result.Success).data
        assertThat(launch).isInstanceOf(McpOAuthLaunch.InApp::class.java)
    }

    @Test
    fun `an older server keeps the system browser and binds nothing`() = runTest {
        val launch = (launcher("0.8.8").prepare("github", "https://idp") as Result.Success).data

        assertThat(launch).isEqualTo(McpOAuthLaunch.External("github", "https://idp"))
        coVerify(exactly = 0) { mcp.bindOAuth(any()) }
    }

    @Test
    fun `a failed bind is reported, not opened`() = runTest {
        coEvery { mcp.bindOAuth(any()) } returns Result.Error(message = "nope")
        assertThat(launcher("0.8.9").prepare("github", "https://idp")).isInstanceOf(Result.Error::class.java)
    }

    @Test
    fun `a bind answered by the SPA catch-all with no binding cookie falls back to the system browser`() = runTest {
        // Before v0.8.3-rc1 an unmatched POST /api/mcp/... gets index.html with a 200 and no Set-Cookie.
        coEvery { mcp.bindOAuth(any()) } returns Result.Success(emptyList())

        val launch = (launcher(null).prepare("github", "https://idp") as Result.Success).data

        assertThat(launch).isEqualTo(McpOAuthLaunch.External("github", "https://idp"))
    }

    @Test
    fun `an unknown version whose server lacks the bind route falls back to the system browser`() = runTest {
        coEvery { mcp.bindOAuth(any()) } returns
            Result.Error(exception = ApiException(statusCode = 404, message = "Not Found"))

        val launch = (launcher(null).prepare("github", "https://idp") as Result.Success).data

        assertThat(launch).isEqualTo(McpOAuthLaunch.External("github", "https://idp"))
    }

    @Test
    fun `a bind 404 on a server reporting rc1 still falls back rather than failing`() = runTest {
        coEvery { mcp.bindOAuth(any()) } returns
            Result.Error(exception = ApiException(statusCode = 404, message = "Not Found"))

        val launch = (launcher("0.8.9-rc1").prepare("github", "https://idp") as Result.Success).data

        assertThat(launch).isEqualTo(McpOAuthLaunch.External("github", "https://idp"))
    }

    @Test
    fun `a bind failure other than 404 is still reported`() = runTest {
        coEvery { mcp.bindOAuth(any()) } returns
            Result.Error(exception = ApiException(statusCode = 500, message = "Failed to bind OAuth flow"))

        assertThat(launcher(null).prepare("github", "https://idp")).isInstanceOf(Result.Error::class.java)
    }
}

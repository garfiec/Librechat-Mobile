package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class McpServerRowsTest {

    private val servers = listOf(
        McpServer(name = "github", title = "GitHub"),
        McpServer(name = "linear", title = "Linear"),
        McpServer(name = "ha", title = "Home Assistant"),
        McpServer(name = "amazon"),
    )
    private val status = mapOf(
        "github" to McpServerStatus(connectionState = "connected"),
        "linear" to McpServerStatus(requiresOAuth = true),
        "ha" to McpServerStatus(connectionState = "error", error = "ECONNREFUSED"),
    )

    private fun rows() = mcpServerRows(servers, status, mapOf("github" to 41), emptySet(), permissions = null, baseUrl = BASE)

    @Test
    fun `servers needing the user sort first, then by title`() {
        assertThat(rows().map { it.title }).containsExactly("Home Assistant", "Linear", "amazon", "GitHub").inOrder()
    }

    @Test
    fun `a row carries its status, error and tool count`() {
        val byName = rows().associateBy { it.name }
        assertThat(byName.getValue("linear").status).isEqualTo(McpDisplayStatus.NEEDS_SIGN_IN)
        assertThat(byName.getValue("ha").error).isEqualTo("ECONNREFUSED")
        assertThat(byName.getValue("github").toolCount).isEqualTo(41)
        assertThat(byName.getValue("amazon").title).isEqualTo("amazon")
    }

    @Test
    fun `a reconnect in flight shows as connecting`() {
        val row = mcpServerRows(servers, status, emptyMap(), setOf("ha"), null, BASE).first { it.name == "ha" }
        assertThat(row.status).isEqualTo(McpDisplayStatus.CONNECTING)
    }

    @Test
    fun `search matches the title or the stored name`() {
        assertThat(filterMcpServerRows(rows(), "home", false).map { it.name }).containsExactly("ha")
        assertThat(filterMcpServerRows(rows(), " HA ", false).map { it.name }).containsExactly("ha")
    }

    @Test
    fun `the attention filter keeps only servers the user can fix`() {
        assertThat(filterMcpServerRows(rows(), "", true).map { it.name }).containsExactly("ha", "linear")
    }

    /** Upstream renders `<img src={iconPath}>` off the web origin, so a stored icon is a relative path. */
    @Test
    fun `a stored icon path is joined to the server, an absolute one kept`() {
        val withIcons = listOf(
            McpServer(name = "a", iconPath = "/images/abc/icon.png"),
            McpServer(name = "b", iconPath = "https://cdn.example.test/b.png"),
            McpServer(name = "c"),
        )
        val icons = mcpServerRows(withIcons, emptyMap(), emptyMap(), emptySet(), null, BASE).associate { it.name to it.iconUrl }
        assertThat(icons).containsExactly(
            "a", "$BASE/images/abc/icon.png",
            "b", "https://cdn.example.test/b.png",
            "c", null,
        )
    }

    private companion object {
        const val BASE = "https://chat.example.test"
    }
}

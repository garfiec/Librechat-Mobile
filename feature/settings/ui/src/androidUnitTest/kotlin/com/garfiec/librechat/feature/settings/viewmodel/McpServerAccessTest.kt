package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.mcp.McpServer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class McpServerAccessTest {

    private val stored = McpServer(name = "docs", dbId = "6650f1")
    private val full = McpServerAccess(canEdit = true, canDelete = true)

    @Test
    fun `a yaml server is read-only whatever the permissions say`() {
        val yaml = McpServer(name = "filesystem")
        assertThat(mcpServerAccess(yaml, null)).isEqualTo(McpServerAccess.READ_ONLY)
        assertThat(mcpServerAccess(yaml, mapOf("x" to 15))).isEqualTo(McpServerAccess.READ_ONLY)
    }

    @Test
    fun `a server reached only through an agent is read-only`() {
        assertThat(mcpServerAccess(stored.copy(consumeOnly = true), mapOf("6650f1" to 15)))
            .isEqualTo(McpServerAccess.READ_ONLY)
    }

    @Test
    fun `the bits decide edit and delete separately`() {
        assertThat(mcpServerAccess(stored, mapOf("6650f1" to PermissionBits.VIEW)))
            .isEqualTo(McpServerAccess.READ_ONLY)
        assertThat(mcpServerAccess(stored, mapOf("6650f1" to (PermissionBits.VIEW or PermissionBits.EDIT))))
            .isEqualTo(McpServerAccess(canEdit = true, canDelete = false))
        assertThat(mcpServerAccess(stored, mapOf("6650f1" to 15))).isEqualTo(full)
    }

    @Test
    fun `unknown permissions leave a stored server editable`() {
        assertThat(mcpServerAccess(stored, null)).isEqualTo(full)
    }

    /** Created after the permissions were fetched: the map can't have it yet. */
    @Test
    fun `a server missing from a loaded map stays editable`() {
        assertThat(mcpServerAccess(stored, mapOf("other" to 1))).isEqualTo(full)
    }
}

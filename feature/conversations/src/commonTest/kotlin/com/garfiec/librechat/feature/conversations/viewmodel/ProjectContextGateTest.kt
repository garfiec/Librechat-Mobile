package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ChatProjectsConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectContextGateTest {

    private val rc1 = DetectedBackend("0.8.9-rc1", BackendBuildClass.RC)
    private val rag = StartupConfig(ragEnabled = true)

    @Test
    fun older_tagged_server_shows_nothing() {
        val gate = projectContextGate(DetectedBackend("0.8.8", BackendBuildClass.OFFICIAL), rag, emptyMap(), null)
        assertFalse(gate.enabled)
        assertFalse(gate.showFiles)
    }

    @Test
    fun unresolved_server_shows_nothing() {
        assertFalse(projectContextGate(null, rag, emptyMap(), null).enabled)
    }

    @Test
    fun dev_build_from_the_landing_day_is_held_back_and_the_next_day_passes() {
        val landingDay = DetectedBackend("0.8.8", BackendBuildClass.DEV, commitDate = "2026-10-01")
        val nextDay = DetectedBackend("0.8.8", BackendBuildClass.DEV, commitDate = "2026-10-02")
        assertFalse(projectContextGate(landingDay, rag, emptyMap(), null).enabled)
        assertTrue(projectContextGate(nextDay, rag, emptyMap(), null).enabled)
    }

    @Test
    fun files_need_a_rag_api() {
        val gate = projectContextGate(rc1, StartupConfig(), emptyMap(), null)
        assertTrue(gate.enabled)
        assertFalse(gate.showFiles)
        assertFalse(gate.canUploadFiles)
    }

    @Test
    fun upload_needs_the_file_search_capability_when_capabilities_are_stated() {
        val without = mapOf("agents" to EndpointConfig(capabilities = listOf("tools", "execute_code")))
        val with = mapOf("agents" to EndpointConfig(capabilities = listOf("file_search")))
        assertFalse(projectContextGate(rc1, rag, without, null).canUploadFiles)
        assertTrue(projectContextGate(rc1, rag, without, null).showFiles)
        assertTrue(projectContextGate(rc1, rag, with, null).canUploadFiles)
        // Not stated reads as the defaults, which include file search.
        assertTrue(projectContextGate(rc1, rag, emptyMap(), null).canUploadFiles)
    }

    @Test
    fun upload_needs_file_search_use() {
        val denied = UserRolePermissions(name = "USER", permissions = mapOf("FILE_SEARCH" to mapOf("USE" to false)))
        assertFalse(projectContextGate(rc1, rag, emptyMap(), denied).canUploadFiles)
    }

    @Test
    fun limits_come_from_the_projects_block() {
        val config = StartupConfig(ragEnabled = true, projects = ChatProjectsConfig(maxFiles = 3))
        val gate = projectContextGate(rc1, config, emptyMap(), null)
        assertEquals(3, gate.limits.maxFiles)
        assertEquals(16_000, gate.limits.maxInstructionsLength)
    }
}

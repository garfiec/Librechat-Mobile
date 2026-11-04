package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.model.ChatProject
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The v0.8.9-rc1 project context fields: absent reads as older-server defaults. */
class ProjectContextConfigTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun absent_projects_block_uses_upstream_defaults() {
        val config: StartupConfig = json.decodeFromString("{}")
        assertNull(config.ragEnabled)
        assertNull(config.projects)
        assertEquals(ChatProjectLimits(50, 16_000, 1_000), ChatProjectLimits.from(config.projects))
    }

    @Test
    fun configured_limits_decode_and_partial_blocks_fill_in() {
        val config: StartupConfig =
            json.decodeFromString("""{"ragEnabled":true,"projects":{"maxFiles":10,"maxInstructionsLength":24000}}""")
        assertTrue(config.ragEnabled == true)
        assertEquals(ChatProjectLimits(10, 24_000, 1_000), ChatProjectLimits.from(config.projects))
    }

    @Test
    fun project_detail_fields_decode() {
        val project: ChatProject = json.decodeFromString(
            """{"_id":"p","name":"n","instructions":"Be brief","contextRevision":3,"file_ids":["a"],
               "hasInstructions":true,"fileCount":1}""",
        )
        assertEquals("Be brief", project.instructions)
        assertEquals(3, project.contextRevision)
        assertEquals(listOf("a"), project.fileIds)
        assertEquals(true, project.hasInstructions)
        assertEquals(1, project.fileCount)
    }

    @Test
    fun older_project_has_no_context_fields() {
        val project: ChatProject = json.decodeFromString("""{"_id":"p","name":"n"}""")
        assertNull(project.instructions)
        assertNull(project.contextRevision)
        assertNull(project.fileIds)
    }
}

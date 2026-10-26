package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The tool keys `GET /api/config` serves on a spec (`sanitizeModelSpecs` keeps them). */
class ModelSpecToolsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun spec(fields: String): ModelSpec =
        json.decodeFromString(ModelSpec.serializer(), """{"name":"s"$fields}""")

    @Test
    fun theToolFlagsAndServersDecode() {
        val spec = spec(
            ""","hideBadgeRow":true,"webSearch":true,"fileSearch":false,"executeCode":true,""" +
                """"memory":true,"askUserQuestion":true,"mcpServers":["docs","jira"]""",
        )
        assertEquals(true, spec.hideBadgeRow)
        assertEquals(true, spec.webSearch)
        assertEquals(false, spec.fileSearch)
        assertEquals(true, spec.executeCode)
        assertEquals(true, spec.memory)
        assertEquals(true, spec.askUserQuestion)
        assertEquals(listOf("docs", "jira"), spec.mcpServers)
    }

    @Test
    fun absentKeysAreNull() {
        val spec = spec("")
        assertNull(spec.hideBadgeRow)
        assertNull(spec.webSearch)
        assertNull(spec.mcpServers)
    }
}

package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The display keys `GET /api/config` serves on a spec. */
class ModelSpecPresentationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun spec(fields: String): ModelSpec =
        json.decodeFromString(ModelSpec.serializer(), """{"name":"s"$fields}""")

    @Test
    fun theDisplayKeysDecode() {
        val spec = spec(
            ""","group":"Research","groupIcon":"https://x/g.png","showIconInMenu":false,""" +
                """"showIconInHeader":false,"showOnLanding":true""",
        )
        assertEquals("Research", spec.group)
        assertEquals("https://x/g.png", spec.groupIcon)
        assertEquals(false, spec.showIconInMenu)
        assertEquals(false, spec.showIconInHeader)
        assertEquals(true, spec.showOnLanding)
    }

    @Test
    fun absentKeysAreNull() {
        val spec = spec("")
        assertNull(spec.group)
        assertNull(spec.groupIcon)
        assertNull(spec.showIconInMenu)
        assertNull(spec.showIconInHeader)
        assertNull(spec.showOnLanding)
    }
}

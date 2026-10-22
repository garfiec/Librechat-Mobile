package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * How a spec's `skills` key decodes, for a config that carries it. v0.8.8's `GET /api/config`
 * strips the key from every spec (`sanitizeModelSpecs`), so this covers forward-compatible
 * decoding, not a key today's server sends.
 */
class ModelSpecSkillsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun spec(skills: String?): ModelSpec {
        val field = skills?.let { ""","skills":$it""" }.orEmpty()
        return json.decodeFromString(ModelSpec.serializer(), """{"name":"s"$field}""")
    }

    @Test
    fun anAbsentKeyLeavesSkillsToTheUser() {
        assertNull(spec(null).skillsSetting)
    }

    @Test
    fun trueIsTheWholeCatalog() {
        assertEquals(ModelSpecSkills.All, spec("true").skillsSetting)
    }

    @Test
    fun falseAndAnEmptyListTurnSkillsOff() {
        assertEquals(ModelSpecSkills.Off, spec("false").skillsSetting)
        assertEquals(ModelSpecSkills.Off, spec("[]").skillsSetting)
    }

    @Test
    fun aListLimitsSkillsToThoseNames() {
        assertEquals(ModelSpecSkills.Only(listOf("pdf-tools", "brand-voice")), spec("""["pdf-tools","brand-voice"]""").skillsSetting)
    }

    @Test
    fun anotherShapeIsIgnoredAsTheServerIgnoresIt() {
        assertNull(spec("\"true\"").skillsSetting)
        assertNull(spec("{}").skillsSetting)
    }
}

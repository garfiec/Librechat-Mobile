package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a spec's `skills` key decodes. `GET /api/config` sends it as a boolean from v0.8.9. */
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
    fun trueAndAListOnlyStartTheToggleOn() {
        assertEquals(ModelSpecSkills.On, spec("true").skillsSetting)
        assertEquals(ModelSpecSkills.On, spec("""["pdf-tools"]""").skillsSetting)
        assertEquals(ModelSpecSkills.On, spec("[]").skillsSetting)
    }

    @Test
    fun falseTurnsSkillsOff() {
        assertEquals(ModelSpecSkills.Off, spec("false").skillsSetting)
    }

    @Test
    fun anotherShapeIsIgnoredAsTheServerIgnoresIt() {
        assertNull(spec("\"true\"").skillsSetting)
        assertNull(spec("{}").skillsSetting)
    }
}

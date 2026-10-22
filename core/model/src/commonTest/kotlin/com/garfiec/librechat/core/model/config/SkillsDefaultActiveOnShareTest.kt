package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `interface.skills.defaultActiveOnShare`, read as upstream `resolveDefaultActiveOnShare` reads it. */
class SkillsDefaultActiveOnShareTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun config(body: String): InterfaceConfig = json.decodeFromString(body)

    @Test
    fun an_absent_section_is_false() {
        assertFalse(skillsDefaultActiveOnShare(config("{}").skills))
        assertFalse(skillsDefaultActiveOnShare(config("""{"skills": null}""").skills))
    }

    @Test
    fun the_boolean_form_is_false_whatever_its_value() {
        assertFalse(skillsDefaultActiveOnShare(config("""{"skills": true}""").skills))
        assertFalse(skillsDefaultActiveOnShare(config("""{"skills": false}""").skills))
    }

    @Test
    fun the_object_form_needs_an_explicit_true() {
        assertTrue(skillsDefaultActiveOnShare(config("""{"skills": {"use": true, "defaultActiveOnShare": true}}""").skills))
        assertFalse(skillsDefaultActiveOnShare(config("""{"skills": {"use": true}}""").skills))
        assertFalse(skillsDefaultActiveOnShare(config("""{"skills": {"defaultActiveOnShare": false}}""").skills))
        assertFalse(skillsDefaultActiveOnShare(config("""{"skills": {"defaultActiveOnShare": "true"}}""").skills))
    }
}

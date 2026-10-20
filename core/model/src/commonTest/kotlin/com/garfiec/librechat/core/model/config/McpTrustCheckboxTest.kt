package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpTrustCheckboxTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun a_plain_string_is_used_as_is() {
        assertEquals("I trust this server", resolveLocalizedConfigText(JsonPrimitive("I trust this server"), "de"))
    }

    @Test
    fun a_map_prefers_the_exact_tag_then_the_language_then_english() {
        val map = buildJsonObject {
            put("en", "Trust")
            put("de", "Vertrauen")
            put("pt-BR", "Confiar")
        }
        assertEquals("Confiar", resolveLocalizedConfigText(map, "pt-BR"))
        assertEquals("Vertrauen", resolveLocalizedConfigText(map, "de-AT"))
        assertEquals("Trust", resolveLocalizedConfigText(map, "ja"))
    }

    @Test
    fun an_underscored_locale_matches_its_tag_and_language() {
        val map = buildJsonObject {
            put("en", "Trust")
            put("de", "Vertrauen")
            put("pt-BR", "Confiar")
        }
        assertEquals("Confiar", resolveLocalizedConfigText(map, "pt_BR"))
        assertEquals("Vertrauen", resolveLocalizedConfigText(map, "de_DE"))
    }

    @Test
    fun a_map_without_english_falls_back_to_its_first_entry() {
        assertEquals("Vertrauen", resolveLocalizedConfigText(buildJsonObject { put("de", "Vertrauen") }, "ja"))
    }

    @Test
    fun markup_is_stripped_to_plain_text() {
        val label = JsonPrimitive("I accept the <a href=\"https://example.test/terms\">terms</a> &amp; risks")
        assertEquals("I accept the terms & risks", resolveLocalizedConfigText(label, "en"))
    }

    @Test
    fun a_line_or_block_break_separates_words() {
        assertEquals("One Two", resolveLocalizedConfigText(JsonPrimitive("One<br>Two"), "en"))
        assertEquals("One Two", resolveLocalizedConfigText(JsonPrimitive("<p>One</p><p>Two</p>"), "en"))
        assertEquals("Title body", resolveLocalizedConfigText(JsonPrimitive("<h3>Title</h3>body"), "en"))
        assertEquals("bold text", resolveLocalizedConfigText(JsonPrimitive("<b>bold</b> text"), "en"))
    }

    @Test
    fun blank_or_missing_text_resolves_to_null() {
        assertNull(resolveLocalizedConfigText(null, "en"))
        assertNull(resolveLocalizedConfigText(JsonPrimitive("  <br/> "), "en"))
    }

    @Test
    fun the_interface_config_exposes_the_configured_wording() {
        val config = json.decodeFromString<InterfaceConfig>(
            """{"mcpServers":{"create":true,"trustCheckbox":{"label":{"en":"Trust it"},"subLabel":"Be careful"}}}""",
        )
        val trust = config.mcpTrustCheckbox()
        assertEquals("Trust it", resolveLocalizedConfigText(trust?.label, "en"))
        assertEquals("Be careful", resolveLocalizedConfigText(trust?.subLabel, "en"))
    }

    @Test
    fun no_trust_config_means_the_client_wording() {
        assertNull(json.decodeFromString<InterfaceConfig>("""{"mcpServers":{"create":true}}""").mcpTrustCheckbox())
        assertNull(InterfaceConfig().mcpTrustCheckbox())
    }
}

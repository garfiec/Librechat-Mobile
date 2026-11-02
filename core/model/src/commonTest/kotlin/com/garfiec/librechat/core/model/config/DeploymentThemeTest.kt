package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeploymentThemeTest {

    private fun theme(raw: String): JsonElement = Json.parseToJsonElement(raw)

    private fun accentOf(raw: String): DeploymentAccent? =
        (readDeploymentAccent(theme(raw)) as? DeploymentAccentRead.Accent)?.accent

    @Test
    fun an_inline_theme_gives_its_light_accent() {
        val accent = accentOf(
            """{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-accent-primary":"16 163 127"}}}}""",
        )
        assertEquals("Acme", accent?.name)
        assertEquals(0xFF10A37F.toInt(), accent?.seedArgb)
    }

    @Test
    fun a_grey_light_accent_falls_back_to_the_dark_one() {
        // ClickHouse: near-black in light, yellow in dark.
        val accent = accentOf(
            """
            {"version":1,"name":"ClickHouse","modes":{
              "light":{"colors":{"rgb-accent-primary":"21 21 21"}},
              "dark":{"colors":{"rgb-accent-primary":"250 255 105"}}}}
            """.trimIndent(),
        )
        assertEquals(0xFFFAFF69.toInt(), accent?.seedArgb)
    }

    @Test
    fun both_grey_is_no_accent() {
        val read = readDeploymentAccent(
            theme(
                """
                {"version":1,"name":"Mono","modes":{
                  "light":{"colors":{"rgb-accent-primary":"21 21 21"}},
                  "dark":{"colors":{"rgb-accent-primary":"240 240 240"}}}}
                """.trimIndent(),
            ),
        )
        assertEquals(DeploymentAccentRead.None, read)
    }

    @Test
    fun a_bundled_name_is_no_accent() {
        assertEquals(DeploymentAccentRead.None, readDeploymentAccent(theme("\"librechat\"")))
        assertEquals(DeploymentAccentRead.None, readDeploymentAccent(theme("\"clickhouse\"")))
        assertEquals(DeploymentAccentRead.None, readDeploymentAccent(null))
    }

    @Test
    fun a_theme_without_the_accent_token_is_no_accent() {
        val raw = """{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-link":"1 2 3"}}}}"""
        val read = readDeploymentAccent(theme(raw))
        assertEquals(DeploymentAccentRead.None, read)
    }

    @Test
    fun malformed_values_are_invalid_not_a_crash() {
        val invalid = listOf(
            """{"version":2,"name":"Acme","modes":{}}""",
            """{"version":1,"modes":{}}""",
            """{"version":1,"name":"Acme"}""",
            """{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-accent-primary":"256 0 0"}}}}""",
            """{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-accent-primary":"#ff0000"}}}}""",
            """{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-accent-primary":"1  2 3"}}}}""",
            """{"version":1,"name":"Acme","modes":{"light":"dark"}}""",
            "42",
            "[]",
        )
        invalid.forEach { raw -> assertEquals(DeploymentAccentRead.Invalid, readDeploymentAccent(theme(raw)), raw) }
    }

    @Test
    fun rgb_triplets_parse_strictly() {
        assertEquals(0xFF000000.toInt(), parseRgbTriplet("0 0 0"))
        assertEquals(0xFFFFFFFF.toInt(), parseRgbTriplet("255 255 255"))
        assertNull(parseRgbTriplet("255 255"))
        assertNull(parseRgbTriplet("-1 0 0"))
        assertNull(parseRgbTriplet("0255 0 0"))
    }

    @Test
    fun chroma_separates_grey_from_colour() {
        assertTrue(labChroma(0xFF151515.toInt()) < MIN_ACCENT_CHROMA)
        assertTrue(labChroma(0xFFF0F0F0.toInt()) < MIN_ACCENT_CHROMA)
        assertTrue(labChroma(0xFFFAFF69.toInt()) > MIN_ACCENT_CHROMA)
        assertTrue(labChroma(0xFF10A37F.toInt()) > MIN_ACCENT_CHROMA)
    }

    @Test
    fun the_interface_config_decodes_theme_raw() {
        val config = Json { ignoreUnknownKeys = true }.decodeFromString(
            InterfaceConfig.serializer(),
            """{"theme":{"version":1,"name":"Acme","modes":{"light":{"colors":{"rgb-accent-primary":"16 163 127"}}}}}""",
        )
        assertEquals("Acme", accentOfElement(config.theme)?.name)
    }

    private fun accentOfElement(element: JsonElement?): DeploymentAccent? =
        (readDeploymentAccent(element) as? DeploymentAccentRead.Accent)?.accent
}

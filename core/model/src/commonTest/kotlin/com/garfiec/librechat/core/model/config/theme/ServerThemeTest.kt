package com.garfiec.librechat.core.model.config.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ServerThemeTest {
    private fun read(json: String) = readServerTheme(Json.parseToJsonElement(json))

    private fun theme(json: String) = assertIs<ServerThemeRead.Theme>(read(json)).definition

    private fun invalid(json: String) = assertIs<ServerThemeRead.Invalid>(read(json)).issues

    // Upstream's server fixtures (packages/api/src/app/__fixtures__/theme/*.yaml), as `interface.theme` JSON.

    @Test
    fun validFixture() {
        val definition = theme(
            """{"version":1,"name":"acme","modes":{"light":{"colors":{"rgb-surface-primary":"240 244 255"},
            "appearance":{"controlRadius":"0.25rem"}},"dark":{"colors":{"rgb-surface-primary":"12 16 32"}}},
            "brands":{"provider-openai":"#10a37f"}}""",
        )
        assertEquals("acme", definition.name)
        assertEquals(mapOf(ThemeRole.SURFACE_PRIMARY to 0xF0F4FF), definition.light)
        assertEquals(mapOf(ThemeRole.SURFACE_PRIMARY to 0x0C1020), definition.dark)
        assertEquals(mapOf(ThemeCorner.CONTROL to 4f), definition.lightAppearance.corners)
        assertEquals(ServerThemeAppearance.NONE, definition.darkAppearance)
    }

    @Test
    fun bundledFixture() {
        val definition = assertIs<ServerThemeRead.Theme>(readServerTheme(JsonPrimitive("clickhouse"))).definition
        assertEquals("ClickHouse", definition.name)
        assertEquals(ThemeRole.entries.toSet(), definition.light.keys)
        assertEquals(ThemeRole.entries.toSet(), definition.dark.keys)
        // Click UI's square corners and its sans stacks, in both modes.
        assertEquals(4f, definition.lightAppearance.corners[ThemeCorner.CONTROL])
        assertEquals(12f, definition.darkAppearance.corners[ThemeCorner.LARGE_SURFACE])
        assertEquals(GenericFontFamily.SANS, definition.lightAppearance.fonts[ThemeFont.DISPLAY])
    }

    @Test
    fun unknownNameFixture() {
        assertIs<ServerThemeRead.Invalid>(readServerTheme(JsonPrimitive("clickhous")))
    }

    @Test
    fun malformedFixture() {
        assertIs<ServerThemeRead.Invalid>(read("""["acme","clickhouse"]"""))
    }

    @Test
    fun badValueFixture() {
        val issues = invalid(
            """{"version":1,"name":"acme","modes":{"dark":{"colors":{"rgb-surface-primary":"300 16 32"},
            "appearance":{"controlRadius":"4 pixels"}}}}""",
        )
        assertEquals(listOf("Invalid RGB value for rgb-surface-primary: \"300 16 32\""), issues)
    }

    /** The server's schema drops this one, but the web client's own check reads it, and so do we. */
    @Test
    fun spacingFixture() {
        assertEquals(
            mapOf(ThemeRole.SURFACE_PRIMARY to 0xF0F4FF),
            theme("""{"version":1,"name":"acme","modes":{"light":{"colors":{"rgb-surface-primary":"240  244 255"}}}}""").light,
        )
    }

    @Test
    fun unknownAppearanceFixture() {
        theme("""{"version":1,"name":"acme","modes":{"light":{"appearance":{"controlRadius":"2px","futureSpacing":"3rem"}}}}""")
    }

    @Test
    fun unknownTokenFixtureKeepsTheKnownRoles() {
        assertEquals(
            mapOf(ThemeRole.SURFACE_PRIMARY to 0xF0F4FF),
            theme(
                """{"version":1,"name":"acme","modes":{"light":{"colors":{"rgb-surface-primary":"240 244 255",
                "rgb-surfce-secondary":"250 250 250","surface-tertiary":"245 245 245"}}}}""",
            ).light,
        )
    }

    @Test
    fun unknownTokenInvalidFixture() {
        val issues = invalid(
            """{"version":1,"name":"acme","modes":{"light":{"colors":{"rgb-surface-primary":"240 244 255",
            "rgb-future-role":"red"}},"dark":{"colors":{"Surface Tertiary":"245 245 245"}}}}""",
        )
        assertEquals(
            listOf("Invalid RGB value for rgb-future-role: \"red\"", "Unknown color token: Surface Tertiary"),
            issues,
        )
    }

    @Test
    fun unrelatedErrorFixtureStillReadsTheTheme() {
        assertEquals(
            emptyMap(),
            theme("""{"version":1,"name":"acme","modes":{"light":{"colors":{"rgb-surfce-primary":"240 244 255"}}}}""").light,
        )
    }

    // The rest of `collectThemeIssues`.

    @Test
    fun noThemeIsNone() {
        assertIs<ServerThemeRead.None>(readServerTheme(null))
    }

    @Test
    fun librechatIsTheStockPalette() {
        val definition = assertIs<ServerThemeRead.Theme>(readServerTheme(JsonPrimitive("librechat"))).definition
        assertEquals("LibreChat", definition.name)
        assertEquals(emptyMap(), definition.light)
        assertEquals(emptyMap(), definition.dark)
    }

    @Test
    fun rejectsAWrongVersionNameOrShape() {
        invalid("""{"version":2,"name":"acme","modes":{}}""")
        invalid("""{"version":"1","name":"acme","modes":{}}""")
        invalid("""{"version":1,"name":"  ","modes":{}}""")
        invalid("""{"version":1,"name":"acme"}""")
        invalid("""{"version":1,"name":"acme","modes":{},"extra":true}""")
        invalid("""{"version":1,"name":"acme","modes":{"sepia":{}}}""")
        invalid("""{"version":1,"name":"acme","modes":{"light":[]}}""")
        invalid("""{"version":1,"name":"acme","modes":{"light":{"palette":{}}}}""")
        invalid("""{"version":1,"name":"acme","modes":{"light":{"colors":[]}}}""")
        invalid("""{"version":1,"name":"acme","modes":{"light":{"appearance":"square"}}}""")
        invalid("""{"version":1,"name":"acme","modes":{},"brands":"#fff"}""")
    }

    @Test
    fun aModeMayBeLeftOut() {
        val definition = theme("""{"version":1.0,"name":"acme","modes":{"light":{"colors":{"rgb-link":"1 2 3"}}}}""")
        assertEquals(emptyMap(), definition.dark)
    }

    @Test
    fun aRenamedRoleCarriesToItsNewNameUnlessThatIsNamed() {
        val carried = theme("""{"version":1,"name":"a","modes":{"light":{"colors":{"rgb-category-icon":"1 2 3"}}}}""")
        assertEquals(mapOf(ThemeRole.CATEGORY_ICON_4 to 0x010203), carried.light)
        val named = theme(
            """{"version":1,"name":"a","modes":{"light":{"colors":{"rgb-category-icon":"1 2 3","rgb-category-icon-4":"4 5 6"}}}}""",
        )
        assertEquals(mapOf(ThemeRole.CATEGORY_ICON_4 to 0x040506), named.light)
    }

    @Test
    fun rgbFollowsIsThemeRgb() {
        assertEquals(0x0A0B0C, parseThemeRgb("10 11 12"))
        assertEquals(0x0A0B0C, parseThemeRgb("10\t11  12"))
        assertNull(parseThemeRgb("256 0 0"))
        assertNull(parseThemeRgb("0 0"))
        assertNull(parseThemeRgb(" 0 0 0"))
        assertNull(parseThemeRgb("0 0 0 0"))
        assertNull(parseThemeRgb("1000 0 0"))
        assertNull(parseThemeRgb("#ffffff"))
    }
}

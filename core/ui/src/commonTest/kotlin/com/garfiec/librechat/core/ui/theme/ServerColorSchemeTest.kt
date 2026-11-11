package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.garfiec.librechat.core.model.config.theme.ServerThemeDefinition
import com.garfiec.librechat.core.model.config.theme.ThemeRole
import com.garfiec.librechat.core.model.config.theme.resolve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerColorSchemeTest {
    private val stock = ServerThemeDefinition("librechat", emptyMap(), emptyMap())

    @Test
    fun everyRoleSlotPaintsItsServerRole() {
        for (dark in listOf(false, true)) {
            val colors = stock.resolve(dark)
            val scheme = serverColorScheme(colors, dark)
            for (slot in SchemeSlot.entries) {
                val source = slot.source as? ServerColor.Role ?: continue
                assertEquals(colors.color(source.role), scheme.slot(slot), "$slot dark=$dark")
            }
        }
    }

    @Test
    fun aPartialThemeKeepsTheStockCanvasLikeTheWeb() {
        // Sets the chat surface but not surface-primary-alt, which page-canvas follows.
        val navy = ServerThemeDefinition(
            "acme",
            light = emptyMap(),
            dark = mapOf(ThemeRole.SURFACE_CHAT to 0x12182C, ThemeRole.SURFACE_COMPOSER to 0x1C243E),
        )
        val scheme = serverColorScheme(navy.resolve(dark = true), dark = true)
        assertEquals(Color(0xFF1C243E), scheme.surfaceContainerHigh)
        assertEquals(Color(0xFF000000 or ThemeRole.PAGE_CANVAS.libreChatDark.toLong()), scheme.background)
        assertEquals(scheme.background, scheme.surface)
    }

    @Test
    fun containersTintTheCanvasWithTheAccent() {
        val colors = stock.resolve(dark = false)
        val scheme = serverColorScheme(colors, dark = false)
        val expected = colors.color(ThemeRole.ACCENT_PRIMARY).copy(alpha = 0.16f).compositeOver(colors.color(ThemeRole.PAGE_CANVAS))
        assertEquals(expected, scheme.primaryContainer)
    }

    @Test
    fun inkThatWouldNotReadOnItsFillTurnsBlackOrWhite() {
        // ClickHouse's dark yellow accent under a white inverted text.
        val yellow = ServerThemeDefinition(
            "y",
            light = emptyMap(),
            dark = mapOf(ThemeRole.ACCENT_PRIMARY to 0xFAFF69, ThemeRole.TEXT_INVERTED to 0xFFFFFF),
        )
        assertEquals(Color.Black, serverColorScheme(yellow.resolve(dark = true), dark = true).onPrimary)
        assertEquals(Color.White, serverColorScheme(stock.resolve(dark = false), dark = false).onPrimary)
    }

    @Test
    fun aLightAccentIsDarkenedJustEnoughForLabels() {
        val ember = ServerThemeDefinition(
            "ember",
            light = mapOf(ThemeRole.ACCENT_PRIMARY to 0xEA580C),
            dark = emptyMap(),
        )
        val colors = ember.resolve(dark = false)
        val primary = serverColorScheme(colors, dark = false).primary
        val surfaces = listOf(
            ThemeRole.PAGE_CANVAS,
            ThemeRole.SURFACE_SIDEBAR,
            ThemeRole.SURFACE_MENU,
            ThemeRole.SURFACE_COMPOSER,
            ThemeRole.SURFACE_TERTIARY,
        )
        val worst = surfaces.minOf { contrast(primary, colors.color(it)) }
        assertTrue(worst >= MIN_INK_CONTRAST, "worst $worst")
        assertTrue(worst < MIN_INK_CONTRAST + 0.2f, "moved further than needed: $worst")
        assertTrue(primary.red > primary.green && primary.green > primary.blue, "hue lost: $primary")
        // The fills that carry no label keep the accent as given.
        assertEquals(colors.color(ThemeRole.ACCENT_PRIMARY), serverColorScheme(colors, dark = false).inversePrimary)
    }

    @Test
    fun anAccentThatAlreadyReadsIsKept() {
        val ink = ServerThemeDefinition(
            "ink",
            light = mapOf(ThemeRole.ACCENT_PRIMARY to 0x151515),
            dark = mapOf(ThemeRole.ACCENT_PRIMARY to 0x00FFAA),
        )
        assertEquals(Color(0xFF151515), serverColorScheme(ink.resolve(dark = false), dark = false).primary)
        assertEquals(Color(0xFF00FFAA), serverColorScheme(ink.resolve(dark = true), dark = true).primary)
    }

    @Test
    fun aDarkAccentOnDarkSurfacesIsLightened() {
        val navy = ServerThemeDefinition("navy", light = emptyMap(), dark = mapOf(ThemeRole.ACCENT_PRIMARY to 0x1E3A8A))
        val colors = navy.resolve(dark = true)
        val primary = serverColorScheme(colors, dark = true).primary
        assertTrue(primary.luminance() > Color(0xFF1E3A8A).luminance())
        assertTrue(contrast(primary, colors.color(ThemeRole.PAGE_CANVAS)) >= MIN_INK_CONTRAST)
    }

    @Test
    fun everyInkReadsOnItsFill() {
        for (dark in listOf(false, true)) {
            val scheme = serverColorScheme(stock.resolve(dark), dark)
            for (slot in SchemeSlot.entries) {
                val ink = slot.source as? ServerColor.Ink ?: continue
                assertTrue(contrast(scheme.slot(slot), scheme.slot(ink.on)) >= MIN_INK_CONTRAST, "$slot dark=$dark")
            }
        }
    }

    @Test
    fun contrastIsTheWcagRatio() {
        assertEquals(21f, contrast(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrast(Color.Red, Color.Red), 0.0001f)
    }

    @Test
    fun theTestCoversEverySlot() {
        assertEquals(SchemeSlot.entries.toSet(), GETTERS.keys)
    }

    private fun ColorScheme.slot(slot: SchemeSlot): Color = GETTERS.getValue(slot)(this)

    private companion object {
        val GETTERS: Map<SchemeSlot, (ColorScheme) -> Color> = mapOf(
            SchemeSlot.PRIMARY to { it.primary },
            SchemeSlot.ON_PRIMARY to { it.onPrimary },
            SchemeSlot.PRIMARY_CONTAINER to { it.primaryContainer },
            SchemeSlot.ON_PRIMARY_CONTAINER to { it.onPrimaryContainer },
            SchemeSlot.INVERSE_PRIMARY to { it.inversePrimary },
            SchemeSlot.SECONDARY to { it.secondary },
            SchemeSlot.ON_SECONDARY to { it.onSecondary },
            SchemeSlot.SECONDARY_CONTAINER to { it.secondaryContainer },
            SchemeSlot.ON_SECONDARY_CONTAINER to { it.onSecondaryContainer },
            SchemeSlot.TERTIARY to { it.tertiary },
            SchemeSlot.ON_TERTIARY to { it.onTertiary },
            SchemeSlot.TERTIARY_CONTAINER to { it.tertiaryContainer },
            SchemeSlot.ON_TERTIARY_CONTAINER to { it.onTertiaryContainer },
            SchemeSlot.BACKGROUND to { it.background },
            SchemeSlot.ON_BACKGROUND to { it.onBackground },
            SchemeSlot.SURFACE to { it.surface },
            SchemeSlot.ON_SURFACE to { it.onSurface },
            SchemeSlot.SURFACE_VARIANT to { it.surfaceVariant },
            SchemeSlot.ON_SURFACE_VARIANT to { it.onSurfaceVariant },
            SchemeSlot.SURFACE_TINT to { it.surfaceTint },
            SchemeSlot.INVERSE_SURFACE to { it.inverseSurface },
            SchemeSlot.INVERSE_ON_SURFACE to { it.inverseOnSurface },
            SchemeSlot.ERROR to { it.error },
            SchemeSlot.ON_ERROR to { it.onError },
            SchemeSlot.ERROR_CONTAINER to { it.errorContainer },
            SchemeSlot.ON_ERROR_CONTAINER to { it.onErrorContainer },
            SchemeSlot.OUTLINE to { it.outline },
            SchemeSlot.OUTLINE_VARIANT to { it.outlineVariant },
            SchemeSlot.SURFACE_BRIGHT to { it.surfaceBright },
            SchemeSlot.SURFACE_DIM to { it.surfaceDim },
            SchemeSlot.SURFACE_CONTAINER_LOWEST to { it.surfaceContainerLowest },
            SchemeSlot.SURFACE_CONTAINER_LOW to { it.surfaceContainerLow },
            SchemeSlot.SURFACE_CONTAINER to { it.surfaceContainer },
            SchemeSlot.SURFACE_CONTAINER_HIGH to { it.surfaceContainerHigh },
            SchemeSlot.SURFACE_CONTAINER_HIGHEST to { it.surfaceContainerHighest },
        )
    }
}

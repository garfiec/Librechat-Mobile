package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.garfiec.librechat.core.model.config.theme.ThemeRole
import com.garfiec.librechat.core.model.config.theme.ThemeRole.ACCENT_PRIMARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BORDER_HEAVY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.BORDER_LIGHT
import com.garfiec.librechat.core.model.config.theme.ThemeRole.LINK
import com.garfiec.librechat.core.model.config.theme.ThemeRole.PAGE_CANVAS
import com.garfiec.librechat.core.model.config.theme.ThemeRole.STATUS_ERROR_SUBTLE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_COMPOSER
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_INVERTED
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_MENU
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_NAV_SELECTED
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_PRIMARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_SECONDARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_SIDEBAR
import com.garfiec.librechat.core.model.config.theme.ThemeRole.SURFACE_TERTIARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_DESTRUCTIVE
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_INVERTED
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_ON_STATUS
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_PRIMARY
import com.garfiec.librechat.core.model.config.theme.ThemeRole.TEXT_SECONDARY
import com.garfiec.librechat.core.ui.theme.ServerColor.Ink
import com.garfiec.librechat.core.ui.theme.ServerColor.Legible
import com.garfiec.librechat.core.ui.theme.ServerColor.Role
import com.garfiec.librechat.core.ui.theme.ServerColor.Tint

/** Where one [SchemeSlot] of a server-themed scheme takes its colour from. */
internal sealed interface ServerColor {
    data class Role(val role: ThemeRole) : ServerColor

    /** [role] at [alpha] over [base]: a container the web has no role for. */
    data class Tint(val role: ThemeRole, val base: ThemeRole, val alpha: Float) : ServerColor

    /** [role] as ink on [on], made readable by [readableInk]. */
    data class Ink(val role: ThemeRole, val on: SchemeSlot) : ServerColor

    /**
     * [role] moved toward black or white, hue kept, only as far as it takes to read at
     * [MIN_INK_CONTRAST] on each of [against]: an accent the app also writes labels in.
     */
    data class Legible(val role: ThemeRole, val against: List<ThemeRole>) : ServerColor
}

/**
 * The M3 roles a server theme fills, each with the server role it takes ([source]). `background`
 * and `surface` must stay equal: the chat canvas is `background` and the fades over it are
 * `surface`. The containers follow what the app paints with each: High is the composer, Low the
 * drawer and sheets, the plain one menus. `scrim` is left out on purpose: it stays M3's black.
 * An [ServerColor.Ink]'s fill is declared above it.
 *
 * The web fills with its accent but never writes running labels in it; the app does (section
 * headers, selected rows), so `primary` is the accent made [ServerColor.Legible] on the surfaces
 * those labels sit on, while the containers and `inversePrimary` keep it as given. `surfaceTint`
 * is the canvas itself: M3 washes an elevated `surface` (a sheet) in it, so a sheet keeps the
 * server's colour as the web paints it, and labels keep the contrast `primary` was moved to.
 */
internal enum class SchemeSlot(val source: ServerColor) {
    PRIMARY(
        Legible(ACCENT_PRIMARY, against = listOf(PAGE_CANVAS, SURFACE_SIDEBAR, SURFACE_MENU, SURFACE_COMPOSER, SURFACE_TERTIARY)),
    ),
    ON_PRIMARY(Ink(TEXT_INVERTED, on = PRIMARY)),
    PRIMARY_CONTAINER(Tint(ACCENT_PRIMARY, PAGE_CANVAS, CONTAINER_TINT)),
    ON_PRIMARY_CONTAINER(Ink(TEXT_PRIMARY, on = PRIMARY_CONTAINER)),
    INVERSE_PRIMARY(Role(ACCENT_PRIMARY)),
    SECONDARY(Role(TEXT_SECONDARY)),
    ON_SECONDARY(Ink(TEXT_INVERTED, on = SECONDARY)),
    SECONDARY_CONTAINER(Role(SURFACE_NAV_SELECTED)),
    ON_SECONDARY_CONTAINER(Ink(TEXT_PRIMARY, on = SECONDARY_CONTAINER)),
    TERTIARY(Role(LINK)),
    ON_TERTIARY(Ink(TEXT_INVERTED, on = TERTIARY)),
    TERTIARY_CONTAINER(Tint(LINK, PAGE_CANVAS, CONTAINER_TINT)),
    ON_TERTIARY_CONTAINER(Ink(TEXT_PRIMARY, on = TERTIARY_CONTAINER)),
    BACKGROUND(Role(PAGE_CANVAS)),
    ON_BACKGROUND(Role(TEXT_PRIMARY)),
    SURFACE(Role(PAGE_CANVAS)),
    ON_SURFACE(Role(TEXT_PRIMARY)),
    SURFACE_VARIANT(Role(SURFACE_TERTIARY)),
    ON_SURFACE_VARIANT(Role(TEXT_SECONDARY)),
    SURFACE_TINT(Role(PAGE_CANVAS)),
    INVERSE_SURFACE(Role(SURFACE_INVERTED)),
    INVERSE_ON_SURFACE(Role(TEXT_INVERTED)),
    ERROR(Role(TEXT_DESTRUCTIVE)),
    ON_ERROR(Ink(TEXT_ON_STATUS, on = ERROR)),
    ERROR_CONTAINER(Role(STATUS_ERROR_SUBTLE)),
    ON_ERROR_CONTAINER(Ink(TEXT_PRIMARY, on = ERROR_CONTAINER)),
    OUTLINE(Role(BORDER_HEAVY)),
    OUTLINE_VARIANT(Role(BORDER_LIGHT)),
    SURFACE_BRIGHT(Role(SURFACE_PRIMARY)),
    SURFACE_DIM(Role(SURFACE_SECONDARY)),
    SURFACE_CONTAINER_LOWEST(Role(SURFACE_PRIMARY)),
    SURFACE_CONTAINER_LOW(Role(SURFACE_SIDEBAR)),
    SURFACE_CONTAINER(Role(SURFACE_MENU)),
    SURFACE_CONTAINER_HIGH(Role(SURFACE_COMPOSER)),
    SURFACE_CONTAINER_HIGHEST(Role(SURFACE_TERTIARY)),
}

/** The M3 scheme for a server theme resolved for one mode ([colors] is 24-bit RGB for every role). */
fun serverColorScheme(colors: Map<ThemeRole, Int>, dark: Boolean): ColorScheme {
    val slots = mutableMapOf<SchemeSlot, Color>()
    fun color(slot: SchemeSlot): Color = slots.getOrPut(slot) {
        when (val source = slot.source) {
            is ServerColor.Role -> colors.color(source.role)
            is ServerColor.Tint -> colors.color(source.role).copy(alpha = source.alpha).compositeOver(colors.color(source.base))
            is ServerColor.Ink -> readableInk(colors.color(source.role), on = color(source.on))
            is ServerColor.Legible -> legible(colors.color(source.role), source.against.map(colors::color))
        }
    }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = color(SchemeSlot.PRIMARY),
        onPrimary = color(SchemeSlot.ON_PRIMARY),
        primaryContainer = color(SchemeSlot.PRIMARY_CONTAINER),
        onPrimaryContainer = color(SchemeSlot.ON_PRIMARY_CONTAINER),
        inversePrimary = color(SchemeSlot.INVERSE_PRIMARY),
        secondary = color(SchemeSlot.SECONDARY),
        onSecondary = color(SchemeSlot.ON_SECONDARY),
        secondaryContainer = color(SchemeSlot.SECONDARY_CONTAINER),
        onSecondaryContainer = color(SchemeSlot.ON_SECONDARY_CONTAINER),
        tertiary = color(SchemeSlot.TERTIARY),
        onTertiary = color(SchemeSlot.ON_TERTIARY),
        tertiaryContainer = color(SchemeSlot.TERTIARY_CONTAINER),
        onTertiaryContainer = color(SchemeSlot.ON_TERTIARY_CONTAINER),
        background = color(SchemeSlot.BACKGROUND),
        onBackground = color(SchemeSlot.ON_BACKGROUND),
        surface = color(SchemeSlot.SURFACE),
        onSurface = color(SchemeSlot.ON_SURFACE),
        surfaceVariant = color(SchemeSlot.SURFACE_VARIANT),
        onSurfaceVariant = color(SchemeSlot.ON_SURFACE_VARIANT),
        surfaceTint = color(SchemeSlot.SURFACE_TINT),
        inverseSurface = color(SchemeSlot.INVERSE_SURFACE),
        inverseOnSurface = color(SchemeSlot.INVERSE_ON_SURFACE),
        error = color(SchemeSlot.ERROR),
        onError = color(SchemeSlot.ON_ERROR),
        errorContainer = color(SchemeSlot.ERROR_CONTAINER),
        onErrorContainer = color(SchemeSlot.ON_ERROR_CONTAINER),
        outline = color(SchemeSlot.OUTLINE),
        outlineVariant = color(SchemeSlot.OUTLINE_VARIANT),
        surfaceBright = color(SchemeSlot.SURFACE_BRIGHT),
        surfaceDim = color(SchemeSlot.SURFACE_DIM),
        surfaceContainerLowest = color(SchemeSlot.SURFACE_CONTAINER_LOWEST),
        surfaceContainerLow = color(SchemeSlot.SURFACE_CONTAINER_LOW),
        surfaceContainer = color(SchemeSlot.SURFACE_CONTAINER),
        surfaceContainerHigh = color(SchemeSlot.SURFACE_CONTAINER_HIGH),
        surfaceContainerHighest = color(SchemeSlot.SURFACE_CONTAINER_HIGHEST),
    )
}

internal fun Map<ThemeRole, Int>.color(role: ThemeRole): Color = Color(OPAQUE or getValue(role))

/** [ink] if it reads at [MIN_INK_CONTRAST] on [on], else whichever of black and white reads better. */
internal fun readableInk(ink: Color, on: Color): Color = when {
    contrast(ink, on) >= MIN_INK_CONTRAST -> ink
    contrast(Color.Black, on) >= contrast(Color.White, on) -> Color.Black
    else -> Color.White
}

/**
 * [color] moved in Oklab toward black (white when the first of [surfaces] is dark) until it reads
 * at [MIN_INK_CONTRAST] on every one of [surfaces]; unchanged when it already does.
 */
internal fun legible(color: Color, surfaces: List<Color>): Color {
    fun reads(candidate: Color) = surfaces.all { contrast(candidate, it) >= MIN_INK_CONTRAST }
    if (reads(color)) return color
    val toward = if (surfaces.first().luminance() > HALF_LUMINANCE) Color.Black else Color.White
    var low = 0f
    var high = 1f
    repeat(LEGIBLE_STEPS) {
        val mid = (low + high) / 2
        if (reads(lerp(color, toward, mid))) high = mid else low = mid
    }
    return lerp(color, toward, high)
}

/** WCAG 2 contrast ratio. */
internal fun contrast(a: Color, b: Color): Float {
    val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (light + WCAG_OFFSET) / (dark + WCAG_OFFSET)
}

/** Body text on its fill, WCAG AA. */
internal const val MIN_INK_CONTRAST = 4.5f
private const val CONTAINER_TINT = 0.16f
private const val HALF_LUMINANCE = 0.5f
private const val LEGIBLE_STEPS = 12
private const val WCAG_OFFSET = 0.05f
private const val OPAQUE = -0x1000000

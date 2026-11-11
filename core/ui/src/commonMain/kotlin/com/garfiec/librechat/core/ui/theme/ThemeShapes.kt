package com.garfiec.librechat.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.config.theme.GenericFontFamily
import com.garfiec.librechat.core.model.config.theme.ServerThemeAppearance
import com.garfiec.librechat.core.model.config.theme.ThemeCorner
import com.garfiec.librechat.core.model.config.theme.ThemeFont

/**
 * The corners of the elements a server theme reshapes, each from the setting the LibreChat web
 * rounds that element with. A corner the theme doesn't name keeps Switchboard's, so a theme that
 * only sets colours changes no shape. Liquid Glass's own geometry (its pill controls, dialog and
 * sheet) is not here.
 */
@Immutable
data class ThemeShapes(
    /** Composer box: the web's `largeSurfaceRadius`. */
    val composer: CornerBasedShape,
    /** Send, stop and the composer's round actions: `composerActionRadius`. */
    val composerAction: CornerBasedShape,
    /** The user's bubble: `surfaceRadius`, with `controlRadius` at the bottom end. */
    val userBubble: CornerBasedShape,
    /** Any other message bubble (the web draws none): `surfaceRadius`. */
    val bubble: CornerBasedShape,
    /** Cards: `surfaceRadius`. */
    val card: CornerBasedShape,
    /** Code blocks: `rounded-xl`. */
    val codeBlock: CornerBasedShape,
    /** Markdown tables' outer corners: `controlRadius`. */
    val table: CornerBasedShape,
    /** A Mermaid block's frame: `rounded-lg`. */
    val diagram: CornerBasedShape,
    /** File and image chips, in the composer and in messages: `rounded-xl`. */
    val fileChip: CornerBasedShape,
    /** Material menus: `menuRadius`. */
    val menu: CornerBasedShape,
    /** Material alert dialogs: `rounded-2xl`. */
    val dialog: CornerBasedShape,
) {
    companion object {
        fun of(corners: Map<ThemeCorner, Float>): ThemeShapes {
            fun radius(corner: ThemeCorner, own: Dp): Dp = corners[corner]?.dp ?: own
            val surface = radius(ThemeCorner.SURFACE, 16.dp)
            return ThemeShapes(
                composer = RoundedCornerShape(radius(ThemeCorner.LARGE_SURFACE, 28.dp)),
                composerAction = corners[ThemeCorner.COMPOSER_ACTION]?.let { RoundedCornerShape(it.dp) } ?: CircleShape,
                userBubble = RoundedCornerShape(
                    topStart = surface,
                    topEnd = surface,
                    bottomEnd = radius(ThemeCorner.CONTROL, surface),
                    bottomStart = surface,
                ),
                bubble = RoundedCornerShape(surface),
                card = RoundedCornerShape(radius(ThemeCorner.SURFACE, 16.dp)),
                codeBlock = RoundedCornerShape(radius(ThemeCorner.RADIUS_XL, 8.dp)),
                table = RoundedCornerShape(radius(ThemeCorner.CONTROL, 8.dp)),
                diagram = RoundedCornerShape(radius(ThemeCorner.RADIUS_LG, 8.dp)),
                fileChip = RoundedCornerShape(radius(ThemeCorner.RADIUS_XL, 12.dp)),
                menu = RoundedCornerShape(radius(ThemeCorner.MENU, 16.dp)),
                dialog = RoundedCornerShape(radius(ThemeCorner.RADIUS_2XL, 28.dp)),
            )
        }

        val SWITCHBOARD = of(emptyMap())
    }
}

/**
 * [this] with a server theme's families: headings (the display and headline styles) in its display
 * family, every other style in its body family. A sans family keeps the app's own.
 */
internal fun Typography.withFonts(appearance: ServerThemeAppearance): Typography {
    val body = appearance.fonts[ThemeFont.BODY]?.toFontFamily()
    val display = appearance.fonts[ThemeFont.DISPLAY]?.toFontFamily()
    if (body == null && display == null) return this
    fun TextStyle.of(family: FontFamily?) = if (family == null) this else copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.of(display),
        displayMedium = displayMedium.of(display),
        displaySmall = displaySmall.of(display),
        headlineLarge = headlineLarge.of(display),
        headlineMedium = headlineMedium.of(display),
        headlineSmall = headlineSmall.of(display),
        titleLarge = titleLarge.of(body),
        titleMedium = titleMedium.of(body),
        titleSmall = titleSmall.of(body),
        bodyLarge = bodyLarge.of(body),
        bodyMedium = bodyMedium.of(body),
        bodySmall = bodySmall.of(body),
        labelLarge = labelLarge.of(body),
        labelMedium = labelMedium.of(body),
        labelSmall = labelSmall.of(body),
    )
}

private fun GenericFontFamily.toFontFamily(): FontFamily? = when (this) {
    GenericFontFamily.SANS -> null
    GenericFontFamily.SERIF -> FontFamily.Serif
    GenericFontFamily.MONO -> FontFamily.Monospace
}

@Composable
@ReadOnlyComposable
fun themeShapes(): ThemeShapes = LocalThemeTokens.current.shapes

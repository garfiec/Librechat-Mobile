package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.config.theme.ServerThemeDefinition
import com.garfiec.librechat.core.model.config.theme.ThemeRole
import com.garfiec.librechat.core.model.config.theme.resolve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatColorsTest {

    @Test
    fun switchboardPaintsWhatTheAppAlwaysHas() {
        val scheme = lightColorScheme(primary = Color(0xFF123456), error = Color(0xFFAA0000))
        val chat = ChatColors.switchboard(scheme, dark = false)
        assertEquals(scheme.background, chat.canvas)
        assertEquals(scheme.secondaryContainer, chat.userMessage)
        assertEquals(scheme.onSurface, chat.messageText)
        assertEquals(Color.Transparent, chat.submit)
        assertEquals(scheme.primary, chat.onSubmit)
        assertEquals(scheme.error, chat.onStop)
        assertEquals(scheme.surfaceContainerHighest, chat.codeBody)
        assertEquals(scheme.surfaceContainerHigh, chat.codeHeader)
        assertEquals(scheme.primary, chat.linkProse)
        assertEquals(scheme.onSurface, chat.headingText)
        assertEquals(scheme.onSurfaceVariant, chat.composerPlaceholder)
        assertEquals(scheme.outline, chat.composerBorderFocused)
        assertEquals(scheme.onSurfaceVariant.copy(alpha = 0.38f), chat.onSubmitDisabled)
        assertEquals(scheme.surfaceContainerHigh, chat.jumpButton)
        assertEquals(scheme.onSurface, chat.linkInline)
        assertEquals(scheme.outlineVariant, chat.rule)
        assertEquals(scheme.surfaceContainerHigh, chat.tableHeader)
        assertEquals(scheme.onSurface, chat.onTableHeader)
        assertEquals(scheme.outlineVariant, chat.tableBorder)
        assertEquals(scheme.surfaceContainerHigh, chat.inlineCode)
        assertEquals(scheme.onBackground, chat.syntax.text)
        assertEquals(
            DiagramColors(
                scheme.surfaceContainerHighest,
                scheme.surfaceContainerHigh,
                scheme.onSurfaceVariant,
                Color.Transparent,
                "default",
            ),
            chat.diagram,
        )
        assertEquals("dark", ChatColors.switchboard(darkColorScheme(), dark = true).diagram.mermaidTheme)
        assertEquals(Color(0xFF7C3AED), chat.temporary)
        assertEquals(Color(0xFFA78BFA), ChatColors.switchboard(darkColorScheme(), dark = true).temporary)
        assertEquals(
            PageColors(
                surfacePrimary = scheme.surface,
                surfaceSecondary = scheme.surfaceContainer,
                surfaceTertiary = scheme.surfaceContainerHigh,
                surfaceDestructive = scheme.errorContainer,
                textPrimary = scheme.onSurface,
                textSecondary = scheme.onSurfaceVariant,
                textTertiary = scheme.outline,
                textDestructive = scheme.error,
                textWarning = scheme.tertiary,
                borderMedium = scheme.outline,
                borderLight = scheme.outlineVariant,
                borderDestructive = scheme.error,
            ),
            PageColors.switchboard(scheme),
        )
        assertEquals(
            ChromeColors(
                scheme.surfaceContainerHigh,
                Color.Transparent,
                scheme.surfaceContainerHigh,
                scheme.onSurface,
                Color(0xFF7989FF),
                Color.White,
                scheme.secondaryContainer,
                Color.Transparent,
                scheme.onSecondaryContainer,
            ),
            ChromeColors.switchboard(scheme),
        )
    }

    @Test
    fun serverPaintsTheRolesTheWebDoes() {
        val colors = ServerThemeDefinition("acme", light = ACME, dark = emptyMap()).resolve(dark = false)
        val chat = ChatColors.server(colors, dark = false)
        assertEquals(Color(0xFFF0E8DA), chat.canvas)
        assertEquals(Color(0xFFD6ECE4), chat.userMessage)
        assertEquals(colors.color(ThemeRole.TEXT_SECONDARY), chat.messageText)
        assertEquals(colors.color(ThemeRole.SURFACE_INVERTED), chat.submit)
        assertEquals(colors.color(ThemeRole.TEXT_INVERTED), chat.onSubmit)
        assertEquals(chat.submit, chat.stop)
        assertEquals(colors.color(ThemeRole.SURFACE_CODE_BODY), chat.codeBody)
        assertEquals(colors.color(ThemeRole.LINK_INLINE), chat.linkInline)
        assertEquals(colors.color(ThemeRole.SURFACE_DIALOG), ChromeColors.server(colors).dialog)
        assertEquals(colors.color(ThemeRole.SERIES_6), chat.temporary)
        assertEquals(colors.color(ThemeRole.AVATAR_FILL), ChromeColors.server(colors).avatar)
        assertEquals(colors.color(ThemeRole.AVATAR_TEXT), ChromeColors.server(colors).onAvatar)
        with(ChromeColors.server(colors)) {
            assertEquals(colors.color(ThemeRole.SURFACE_PRIMARY), segment)
            assertEquals(colors.color(ThemeRole.BORDER_LIGHT), segmentEdge)
            assertEquals(colors.color(ThemeRole.TEXT_PRIMARY), onSegment)
        }
    }

    @Test
    fun serverDiagramsKeepMermaidsPaletteInsideTheWebsFrame() {
        val colors = ThemeRole.entries.associateWith { it.ordinal }
        assertEquals(
            DiagramColors(
                colors.color(ThemeRole.SURFACE_PRIMARY_ALT),
                colors.color(ThemeRole.SURFACE_SECONDARY),
                colors.color(ThemeRole.TEXT_SECONDARY),
                colors.color(ThemeRole.BORDER_LIGHT),
                "neutral",
            ),
            ChatColors.server(colors, dark = false).diagram,
        )
        assertEquals("dark", ChatColors.server(colors, dark = true).diagram.mermaidTheme)
    }

    @Test
    fun serverPagesGetTheRolesTheWebsAppBridgeReads() {
        val colors = ThemeRole.entries.associateWith { it.ordinal }
        val roles = with(PageColors.server(colors)) {
            listOf(
                surfacePrimary, surfaceSecondary, surfaceTertiary, surfaceDestructive,
                textPrimary, textSecondary, textTertiary, textDestructive, textWarning,
                borderMedium, borderLight, borderDestructive,
            )
        }
        val expected = listOf(
            ThemeRole.SURFACE_PRIMARY, ThemeRole.SURFACE_SECONDARY, ThemeRole.SURFACE_TERTIARY,
            ThemeRole.SURFACE_DESTRUCTIVE, ThemeRole.TEXT_PRIMARY, ThemeRole.TEXT_SECONDARY,
            ThemeRole.TEXT_TERTIARY, ThemeRole.TEXT_DESTRUCTIVE, ThemeRole.TEXT_WARNING,
            ThemeRole.BORDER_MEDIUM, ThemeRole.BORDER_LIGHT, ThemeRole.BORDER_DESTRUCTIVE,
        ).map(colors::color)
        assertEquals(expected, roles)
    }

    @Test
    fun serverSyntaxFollowsTheWebsHighlightClasses() {
        val colors = ThemeRole.entries.associateWith { it.ordinal }
        val syntax = SyntaxColors.server(colors)
        assertEquals(colors.color(ThemeRole.SYNTAX_ATTR), syntax.number)
        assertEquals(colors.color(ThemeRole.SYNTAX_ATTR), syntax.type)
        assertEquals(colors.color(ThemeRole.SYNTAX_TITLE), syntax.function)
        assertEquals(colors.color(ThemeRole.SYNTAX_META), syntax.annotation)
        assertEquals(colors.color(ThemeRole.SYNTAX_KEYWORD), syntax.constant)
        assertEquals(colors.color(ThemeRole.SYNTAX_TEXT), syntax.text)
        assertEquals(colors.color(ThemeRole.SYNTAX_COMMENT), syntax.comment)
        assertEquals(colors.color(ThemeRole.SYNTAX_STRING), syntax.string)
        assertEquals(colors.color(ThemeRole.SYNTAX_KEYWORD), syntax.keyword)
    }

    @Test
    fun switchboardSyntaxReadsOnItsCodeBlock() {
        val failures = listOf(false to LIGHT_BLOCK, true to DARK_BLOCK).flatMap { (dark, block) ->
            val scheme = if (dark) darkColorScheme() else lightColorScheme()
            val syntax = SyntaxColors.switchboard(scheme, dark)
            val kinds = with(syntax) { listOf(comment, string, keyword, number, type, function, annotation, constant) }
            kinds.mapNotNull { color ->
                contrast(color, block).takeIf { it < MIN_INK_CONTRAST }?.let { "dark=$dark $color $it:1" }
            }
        }
        assertTrue(failures.isEmpty(), "below $MIN_INK_CONTRAST:1 on the code block: $failures")
    }

    private companion object {
        // Switchboard's code block is `surfaceContainerHighest`: tone 90 (light) and 22 (dark) for every accent.
        val LIGHT_BLOCK = Color(0xFFE2E2E9)
        val DARK_BLOCK = Color(0xFF33353A)

        val ACME = mapOf(
            ThemeRole.SURFACE_CANVAS to 0xF0E8DA,
            ThemeRole.SURFACE_USER_MESSAGE to 0xD6ECE4,
        )
    }
}

package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.config.theme.ThemeRole

/**
 * The chat screen's colours where an M3 role is too coarse to carry a server theme: the canvas,
 * messages, the composer's send slot, prose and code. Switchboard's are the scheme roles the app has
 * always painted these with; a server's are the roles the LibreChat web paints them with.
 */
@Immutable
data class ChatColors(
    /** Behind the messages and the composer: the web's `surface-canvas`. */
    val canvas: Color,
    /** The user's message, where the layout fills it. */
    val userMessage: Color,
    /** Message body text. The web paints prose in `text-secondary` and headings in `text-primary`. */
    val messageText: Color,
    val headingText: Color,
    val composerPlaceholder: Color,
    val composerBorderFocused: Color,
    /** The composer's send slot: Send and the during-run sends. [Color.Transparent] means no fill. */
    val submit: Color,
    val onSubmit: Color,
    val stop: Color,
    val onStop: Color,
    val submitDisabled: Color,
    val onSubmitDisabled: Color,
    val jumpButton: Color,
    /** A link in running text, and one in a table cell (the web's `link-inline` and `link-prose`). */
    val linkInline: Color,
    val linkProse: Color,
    val rule: Color,
    val tableHeader: Color,
    val onTableHeader: Color,
    val tableBorder: Color,
    val codeHeader: Color,
    val codeBody: Color,
    val inlineCode: Color,
    val syntax: SyntaxColors,
) {
    companion object {
        fun switchboard(scheme: ColorScheme, dark: Boolean): ChatColors = ChatColors(
            canvas = scheme.background,
            userMessage = scheme.secondaryContainer,
            messageText = scheme.onSurface,
            headingText = scheme.onSurface,
            composerPlaceholder = scheme.onSurfaceVariant,
            composerBorderFocused = scheme.outline,
            submit = Color.Transparent,
            onSubmit = scheme.primary,
            stop = Color.Transparent,
            onStop = scheme.error,
            submitDisabled = Color.Transparent,
            onSubmitDisabled = scheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
            jumpButton = scheme.surfaceContainerHigh,
            linkInline = scheme.onSurface,
            linkProse = scheme.primary,
            rule = scheme.outlineVariant,
            tableHeader = scheme.surfaceContainerHigh,
            onTableHeader = scheme.onSurface,
            tableBorder = scheme.outlineVariant,
            codeHeader = scheme.surfaceContainerHigh,
            codeBody = scheme.surfaceContainerHighest,
            inlineCode = scheme.surfaceContainerHigh,
            syntax = SyntaxColors.switchboard(scheme, dark),
        )

        /** Upstream v0.8.9-rc1 `client/src` and `packages/client/src`: which role each element paints. */
        fun server(colors: Map<ThemeRole, Int>): ChatColors {
            val inverted = colors.color(ThemeRole.SURFACE_INVERTED)
            val onInverted = colors.color(ThemeRole.TEXT_INVERTED)
            return ChatColors(
                canvas = colors.color(ThemeRole.SURFACE_CANVAS),
                userMessage = colors.color(ThemeRole.SURFACE_USER_MESSAGE),
                messageText = colors.color(ThemeRole.TEXT_SECONDARY),
                headingText = colors.color(ThemeRole.TEXT_PRIMARY),
                composerPlaceholder = colors.color(ThemeRole.TEXT_TERTIARY),
                // The web's composer keeps its border on focus and deepens its shadow instead.
                composerBorderFocused = colors.color(ThemeRole.BORDER_LIGHT),
                submit = inverted,
                onSubmit = onInverted,
                stop = inverted,
                onStop = onInverted,
                // The web dims the whole disabled button to 10%, its icon in `text-secondary`.
                submitDisabled = inverted.copy(alpha = WEB_DISABLED_ALPHA),
                onSubmitDisabled = colors.color(ThemeRole.TEXT_SECONDARY).copy(alpha = WEB_DISABLED_ALPHA),
                jumpButton = colors.color(ThemeRole.SURFACE_CHAT),
                linkInline = colors.color(ThemeRole.LINK_INLINE),
                linkProse = colors.color(ThemeRole.LINK_PROSE),
                rule = colors.color(ThemeRole.BORDER_MEDIUM),
                tableHeader = colors.color(ThemeRole.TABLE_HEADER_FILL),
                onTableHeader = colors.color(ThemeRole.TABLE_HEADER_TEXT),
                tableBorder = colors.color(ThemeRole.BORDER_MEDIUM),
                codeHeader = colors.color(ThemeRole.SURFACE_CODE),
                codeBody = colors.color(ThemeRole.SURFACE_CODE_BODY),
                inlineCode = colors.color(ThemeRole.SURFACE_CODE_INLINE),
                syntax = SyntaxColors.server(colors),
            )
        }

        private const val DISABLED_ALPHA = 0.38f
        private const val WEB_DISABLED_ALPHA = 0.1f
    }
}

/**
 * Code highlighting, one colour per kind of token the app's highlighter finds. Switchboard's are
 * VS Code's Light+ and Dark+, tuned to read at 4.5:1 on the block's `surfaceContainerHighest`; a
 * server's come from its eight `syntax-*` roles, matched to the highlight.js classes the web gives
 * each (upstream `client/src/style.css`, `.hljs-*`).
 */
@Immutable
data class SyntaxColors(
    val text: Color,
    val comment: Color,
    val string: Color,
    val keyword: Color,
    val number: Color,
    val type: Color,
    val function: Color,
    val annotation: Color,
    val constant: Color,
) {
    companion object {
        fun switchboard(scheme: ColorScheme, dark: Boolean): SyntaxColors = if (dark) {
            SyntaxColors(
                text = scheme.onBackground,
                comment = Color(0xFF85B06F),
                string = Color(0xFFD69C83),
                keyword = Color(0xFF6AAEE8),
                number = Color(0xFFB5CEA8),
                type = Color(0xFF4EC9B0),
                function = Color(0xFFDCDCAA),
                annotation = Color(0xFFD7BA7D),
                constant = Color(0xFF4FC1FF),
            )
        } else {
            SyntaxColors(
                text = scheme.onBackground,
                comment = Color(0xFF006E00),
                string = Color(0xFFA31515),
                keyword = Color(0xFF0000FF),
                number = Color(0xFF076E48),
                type = Color(0xFF1F677C),
                function = Color(0xFF755B25),
                annotation = Color(0xFF811F3F),
                constant = Color(0xFF0061A7),
            )
        }

        fun server(colors: Map<ThemeRole, Int>): SyntaxColors = SyntaxColors(
            text = colors.color(ThemeRole.SYNTAX_TEXT),
            comment = colors.color(ThemeRole.SYNTAX_COMMENT),
            string = colors.color(ThemeRole.SYNTAX_STRING),
            keyword = colors.color(ThemeRole.SYNTAX_KEYWORD),
            // `.hljs-number` and `.hljs-type` take `syntax-attr`.
            number = colors.color(ThemeRole.SYNTAX_ATTR),
            type = colors.color(ThemeRole.SYNTAX_ATTR),
            // A function name is `.hljs-title`, which takes `syntax-title`.
            function = colors.color(ThemeRole.SYNTAX_TITLE),
            // Decorators and annotations are `.hljs-meta`.
            annotation = colors.color(ThemeRole.SYNTAX_META),
            // `true`, `null` and the like are `.hljs-literal`, which takes `syntax-keyword`.
            constant = colors.color(ThemeRole.SYNTAX_KEYWORD),
        )
    }
}

/**
 * Menus and dialogs, where a server theme names a surface the M3 roles don't carry. Switchboard's
 * are what `AdaptiveDropdownMenu` callers and `AdaptiveAlertDialog` have always used.
 */
@Immutable
data class ChromeColors(
    /** An action menu on a row or card: the web's `surface-popover`. */
    val popover: Color,
    /** The edge every web menu draws; [Color.Transparent] for none. */
    val menuBorder: Color,
    val dialog: Color,
    val dialogTitle: Color,
) {
    companion object {
        fun switchboard(scheme: ColorScheme): ChromeColors = ChromeColors(
            popover = scheme.surfaceContainerHigh,
            menuBorder = Color.Transparent,
            dialog = scheme.surfaceContainerHigh,
            dialogTitle = scheme.onSurface,
        )

        fun server(colors: Map<ThemeRole, Int>): ChromeColors = ChromeColors(
            popover = colors.color(ThemeRole.SURFACE_POPOVER),
            menuBorder = colors.color(ThemeRole.BORDER_MENU),
            dialog = colors.color(ThemeRole.SURFACE_DIALOG),
            dialogTitle = colors.color(ThemeRole.DIALOG_TITLE),
        )
    }
}

@Composable
@ReadOnlyComposable
fun chatColors(): ChatColors = LocalThemeTokens.current.chat

@Composable
@ReadOnlyComposable
fun chromeColors(): ChromeColors = LocalThemeTokens.current.chrome

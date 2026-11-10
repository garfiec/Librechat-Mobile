package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CodeBlockTest {

    private fun highlight(code: String, language: String?, palette: SyntaxPalette = SyntaxPalette.DARK) =
        highlightSyntax(code, language, palette)

    private fun AnnotatedString.spansOf(kind: TokenKind, palette: SyntaxPalette = SyntaxPalette.DARK): List<String> =
        spanStyles.filter { it.item == palette.styleOf(kind) }.map { text.substring(it.start, it.end) }

    private fun AnnotatedString.stringSpans(): List<String> = spansOf(TokenKind.STRING)

    // --- long strings must finish (see DOUBLE_QUOTED) ---

    @Test
    fun a_very_long_string_literal_highlights_in_every_language() {
        val body = "a".repeat(LONG)
        for (language in LANGUAGES) {
            val doubled = "x = \"$body\""
            assertEquals(doubled, highlight(doubled, language).text, language)
            val single = "x = '$body'"
            assertEquals(single, highlight(single, language).text, language)
        }
    }

    @Test
    fun an_unterminated_string_still_streaming_highlights() {
        val code = "x = \"" + "a".repeat(LONG)
        for (language in LANGUAGES) {
            assertEquals(code, highlight(code, language).text, language)
        }
    }

    @Test
    fun a_long_escaped_json_value_is_one_string() {
        val value = "\"" + "\\n".repeat(LONG / 2) + "\""
        val highlighted = highlight("{\"content\": $value}", "json")
        assertEquals(listOf(value), highlighted.stringSpans())
    }

    // --- ordinary code ---

    @Test
    fun strings_with_escapes_end_at_their_closing_quote() {
        val kotlin = "val s = \"say \\\"hi\\\"\" + 'c' // \"not a string\"\nval t = \"a\\\\\" + \"b\""
        assertEquals(
            listOf("\"say \\\"hi\\\"\"", "'c'", "\"a\\\\\"", "\"b\""),
            highlight(kotlin, "kotlin").stringSpans(),
        )

        val python = "x = 'it\\'s' + \"q\""
        assertEquals(listOf("'it\\'s'", "\"q\""), highlight(python, "python").stringSpans())

        val json = "{\"k\": \"v\\\"w\", \"n\": 1}"
        assertEquals(listOf("\"v\\\"w\""), highlight(json, "json").stringSpans())
    }

    @Test
    fun css_property_names_are_whole_runs() {
        val css = ".a { color: red; --my-var : 1px; margin-top:0 }"
        assertEquals(listOf("color", "--my-var", "margin-top"), highlight(css, "css").spansOf(TokenKind.CONSTANT))
    }

    // --- theme ---

    @Test
    fun every_token_colour_reads_on_its_themes_code_block() {
        val failures = SyntaxPalette.entries.flatMap { palette ->
            val block = if (palette == SyntaxPalette.LIGHT) LIGHT_BLOCK else DARK_BLOCK
            TokenKind.entries.mapNotNull { kind ->
                val ratio = contrast(palette.styleOf(kind).color, block)
                if (ratio < MIN_CONTRAST) "$palette $kind ${(ratio * 100).toInt() / 100.0}:1" else null
            }
        }
        assertTrue(failures.isEmpty(), "below $MIN_CONTRAST:1 on the code block: $failures")
    }

    @Test
    fun each_palette_colours_the_same_tokens_in_its_own_colours() {
        val code = "// hi\n@JvmStatic fun greet(n: Int = 3): String = \"a\"\n"
        val light = highlight(code, "kotlin", SyntaxPalette.LIGHT)
        val dark = highlight(code, "kotlin", SyntaxPalette.DARK)

        assertEquals(dark.spanStyles.map { it.start..it.end }, light.spanStyles.map { it.start..it.end })
        for (kind in TokenKind.entries.filter { it != TokenKind.CONSTANT }) {
            assertEquals(dark.spansOf(kind, SyntaxPalette.DARK), light.spansOf(kind, SyntaxPalette.LIGHT), "$kind")
            assertTrue(light.spansOf(kind, SyntaxPalette.LIGHT).isNotEmpty(), "$kind")
        }
        assertNotEquals(dark.spanStyles.map { it.item }, light.spanStyles.map { it.item })
    }

    private companion object {
        // Far past the K/N recursion limit of any thread stack the tests or the app run on.
        const val LONG = 100_000

        // WCAG AA for the block's 13sp text.
        const val MIN_CONTRAST = 4.5f

        // surfaceContainerHighest, the block's background: tone 90 (light) and 22 (dark) for every accent.
        val LIGHT_BLOCK = Color(0xFFE2E2E9)
        val DARK_BLOCK = Color(0xFF33353A)

        val LANGUAGES = listOf(
            "kotlin", "java", "python", "javascript", "typescript", "json", "bash", "xml", "css", "sql",
            "go", "rust", "c", "swift", "ruby", "yaml", "toml", "dockerfile", "plaintext",
        )

        fun contrast(a: Color, b: Color): Float {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05f) / (lo + 0.05f)
        }
    }
}

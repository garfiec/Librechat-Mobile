package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeBlockTest {

    private fun AnnotatedString.stringSpans(): List<String> =
        spanStyles.filter { it.item == stringStyle }.map { text.substring(it.start, it.end) }

    // --- long strings must finish (see DOUBLE_QUOTED) ---

    @Test
    fun a_very_long_string_literal_highlights_in_every_language() {
        val body = "a".repeat(LONG)
        for (language in LANGUAGES) {
            val doubled = "x = \"$body\""
            assertEquals(doubled, highlightSyntax(doubled, language).text, language)
            val single = "x = '$body'"
            assertEquals(single, highlightSyntax(single, language).text, language)
        }
    }

    @Test
    fun an_unterminated_string_still_streaming_highlights() {
        val code = "x = \"" + "a".repeat(LONG)
        for (language in LANGUAGES) {
            assertEquals(code, highlightSyntax(code, language).text, language)
        }
    }

    @Test
    fun a_long_escaped_json_value_is_one_string() {
        val value = "\"" + "\\n".repeat(LONG / 2) + "\""
        val highlighted = highlightSyntax("{\"content\": $value}", "json")
        assertEquals(listOf(value), highlighted.stringSpans())
    }

    // --- ordinary code ---

    @Test
    fun strings_with_escapes_end_at_their_closing_quote() {
        val kotlin = "val s = \"say \\\"hi\\\"\" + 'c' // \"not a string\"\nval t = \"a\\\\\" + \"b\""
        assertEquals(
            listOf("\"say \\\"hi\\\"\"", "'c'", "\"a\\\\\"", "\"b\""),
            highlightSyntax(kotlin, "kotlin").stringSpans(),
        )

        val python = "x = 'it\\'s' + \"q\""
        assertEquals(listOf("'it\\'s'", "\"q\""), highlightSyntax(python, "python").stringSpans())

        val json = "{\"k\": \"v\\\"w\", \"n\": 1}"
        assertEquals(listOf("\"v\\\"w\""), highlightSyntax(json, "json").stringSpans())
    }

    @Test
    fun css_property_names_are_whole_runs() {
        val css = ".a { color: red; --my-var : 1px; margin-top:0 }"
        val names = highlightSyntax(css, "css").spanStyles
            .filter { it.item == constantStyle }
            .map { css.substring(it.start, it.end) }
        assertEquals(listOf("color", "--my-var", "margin-top"), names)
    }

    private companion object {
        // Far past the K/N recursion limit of any thread stack the tests or the app run on.
        const val LONG = 100_000

        val LANGUAGES = listOf(
            "kotlin", "java", "python", "javascript", "typescript", "json", "bash", "xml", "css", "sql",
            "go", "rust", "c", "swift", "ruby", "yaml", "toml", "dockerfile", "plaintext",
        )
    }
}

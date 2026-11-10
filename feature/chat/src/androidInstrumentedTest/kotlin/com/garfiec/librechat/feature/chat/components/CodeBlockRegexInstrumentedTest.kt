package com.garfiec.librechat.feature.chat.components

import androidx.compose.material3.darkColorScheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.ui.theme.SyntaxColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android compiles regexes with ICU, not the JVM engine the host tests use, so a pattern the host
 * accepts can still throw here. Every highlighter rule set and the file-tool field pattern must
 * compile and match on the device.
 */
@RunWith(AndroidJUnit4::class)
class CodeBlockRegexInstrumentedTest {

    @Test
    fun every_string_rule_compiles_and_matches_on_icu() {
        val body = "a".repeat(LONG)
        val palette = SyntaxPalette(SyntaxColors.switchboard(darkColorScheme(), dark = true))
        val stringStyle = palette.styleOf(TokenKind.STRING)
        for (language in LANGUAGES) {
            val code = "x = \"say \\\"hi\\\"\" + '$body'"
            val highlighted = highlightSyntax(code, language, palette)
            assertEquals(language, code, highlighted.text)
            assertTrue(language, highlighted.spanStyles.any { it.item == stringStyle })
        }
        val json = "{\"k\": \"$body\"}"
        assertTrue(highlightSyntax(json, "json", palette).spanStyles.any { it.item == stringStyle })
    }

    @Test
    fun a_long_partial_file_field_parses_on_icu() {
        val plain = "x".repeat(LONG)
        assertEquals(plain, parseJsonField("{\"content\":\"$plain", "content"))
    }

    private companion object {
        const val LONG = 100_000

        val LANGUAGES = listOf(
            "kotlin", "java", "python", "javascript", "typescript", "bash", "css", "sql",
            "go", "rust", "c", "swift", "ruby", "yaml", "toml", "dockerfile", "plaintext",
        )
    }
}

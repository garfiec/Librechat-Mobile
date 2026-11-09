package com.garfiec.librechat.core.ui.text

import androidx.compose.ui.text.LinkAnnotation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The renderer's agent-description behaviour is pinned through its caller, `HtmlDescriptionTest` in :feature:agents. */
class ConfigHtmlTest {

    @Test
    fun `scheme-relative and unknown-scheme links stay text`() {
        assertThat(safeHref("href=\"//evil.example.com/x\"", "https://chat.example.com")).isNull()
        assertThat(safeHref("href=\"intent://scan\"", "https://chat.example.com")).isNull()
        assertThat(safeHref("href=\"  javascript:alert(1)\"", "https://chat.example.com")).isNull()
        assertThat(safeHref("href=\"\"", "https://chat.example.com")).isNull()
    }

    /** An MCP variable's description is HTML wherever its tags fall, as the web sanitises it. */
    @Test
    fun `a link mid-sentence becomes a link`() {
        val html = "A classic token. <a href='https://github.com/settings/tokens'>Create one</a>"

        val result = configHtmlToAnnotatedString(html)

        assertThat(result.text).isEqualTo("A classic token. Create one")
        val link = result.getLinkAnnotations(0, result.length).single().item as LinkAnnotation.Url
        assertThat(link.url).isEqualTo("https://github.com/settings/tokens")
    }
}

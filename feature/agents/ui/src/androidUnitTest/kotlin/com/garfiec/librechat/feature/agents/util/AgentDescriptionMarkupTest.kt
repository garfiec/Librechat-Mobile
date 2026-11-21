package com.garfiec.librechat.feature.agents.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A description is HTML only when it starts with a tag the web allows (upstream `isHtmlDescription`). */
class AgentDescriptionMarkupTest {

    @Test
    fun `a leading allowlisted tag makes it HTML`() {
        assertThat("<p>Hi</p>".isAgentDescriptionHtml()).isTrue()
        assertThat("  \n<BR/>Two".isAgentDescriptionHtml()).isTrue()
        assertThat("</b> stray close".isAgentDescriptionHtml()).isTrue()
        assertThat("< span >spaced</span>".isAgentDescriptionHtml()).isTrue()
    }

    @Test
    fun `a leading img makes it HTML, and the card drops the image`() {
        val description = "<img src=\"/images/logo.png\"> Helps with billing"
        assertThat(description.isAgentDescriptionHtml()).isTrue()
        assertThat(description.agentDescriptionPreview()).isEqualTo("Helps with billing")
    }

    @Test
    fun `text before the first tag keeps it plain`() {
        assertThat("Hello <b>there</b>".isAgentDescriptionHtml()).isFalse()
        assertThat("Tip: wrap snippets in <code> tags".isAgentDescriptionHtml()).isFalse()
        assertThat("One<BR/>Two".isAgentDescriptionHtml()).isFalse()
        assertThat("Ask me about <b>pricing</b>".agentDescriptionPreview()).isEqualTo("Ask me about <b>pricing</b>")
    }

    @Test
    fun `a leading comment keeps it plain`() {
        assertThat("<!-- note --><div>x</div>".isAgentDescriptionHtml()).isFalse()
    }

    @Test
    fun `a tag name must end at a word boundary`() {
        assertThat("<bold>x</bold>".isAgentDescriptionHtml()).isFalse()
        assertThat("<b_x>y".isAgentDescriptionHtml()).isFalse()
        assertThat("<b-x>y".isAgentDescriptionHtml()).isTrue()
    }

    @Test
    fun `a first tag off the allowlist, or no tag, is plain text`() {
        assertThat("Converts List<String> to <p>JSON</p>".isAgentDescriptionHtml()).isFalse()
        assertThat("Reply with <name>".isAgentDescriptionHtml()).isFalse()
        assertThat("<h1>Title</h1>".isAgentDescriptionHtml()).isFalse()
        assertThat("a < b and 3<4".isAgentDescriptionHtml()).isFalse()
        assertThat("Tom &amp; Jerry".isAgentDescriptionHtml()).isFalse()
    }

    @Test
    fun `a card preview turns block tags into spaces and collapses whitespace`() {
        assertThat("<p>A</p><p>B</p>".agentDescriptionPreview()).isEqualTo("A B")
        assertThat("<ul><li>Search</li><li>Summarise</li></ul>".agentDescriptionPreview()).isEqualTo("Search Summarise")
    }

    @Test
    fun `a plain card preview keeps its text, placeholders and entities included`() {
        assertThat("Converts List<String>\n\nto Map<K, V> &amp; back".agentDescriptionPreview())
            .isEqualTo("Converts List<String> to Map<K, V> &amp; back")
    }

    @Test
    fun `an empty preview is null`() {
        assertThat("<p> </p>".agentDescriptionPreview()).isNull()
        assertThat("   ".agentDescriptionPreview()).isNull()
    }
}

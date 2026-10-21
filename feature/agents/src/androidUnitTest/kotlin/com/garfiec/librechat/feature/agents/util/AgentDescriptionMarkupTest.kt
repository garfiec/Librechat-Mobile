package com.garfiec.librechat.feature.agents.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Decisions C7: a description is HTML only when its first tag is on the web's allowlist. */
class AgentDescriptionMarkupTest {

    @Test
    fun `a first allowlisted tag makes it HTML`() {
        assertThat("<p>Hi</p>".isAgentDescriptionHtml()).isTrue()
        assertThat("Hello <b>there</b>".isAgentDescriptionHtml()).isTrue()
        assertThat("One<BR/>Two".isAgentDescriptionHtml()).isTrue()
        assertThat("<!-- note --><div>x</div>".isAgentDescriptionHtml()).isTrue()
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

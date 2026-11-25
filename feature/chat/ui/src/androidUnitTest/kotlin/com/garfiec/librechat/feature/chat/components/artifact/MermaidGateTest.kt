package com.garfiec.librechat.feature.chat.components.artifact

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MermaidGateTest {

    @Test
    fun `flowchart prefix is cacheable`() {
        assertThat(isCacheableMermaid("flowchart TD\n    A --> B")).isTrue()
        assertThat(isCacheableMermaid("flowchart\n    A --> B")).isTrue()
    }

    @Test
    fun `graph prefix is cacheable`() {
        assertThat(isCacheableMermaid("graph LR\n    A --> B")).isTrue()
    }

    @Test
    fun `init directive preamble before flowchart is cacheable`() {
        val src = """
            %%{init: { 'theme': 'forest' }}%%
            flowchart TD
                A --> B
        """.trimIndent()
        assertThat(isCacheableMermaid(src)).isTrue()
    }

    @Test
    fun `leading blank lines do not block cacheability`() {
        val src = "\n\n   \nflowchart TD\n    A --> B"
        assertThat(isCacheableMermaid(src)).isTrue()
    }

    @Test
    fun `state diagram is not cacheable`() {
        assertThat(isCacheableMermaid("stateDiagram-v2\n    [*] --> Still")).isFalse()
    }

    @Test
    fun `gantt is not cacheable`() {
        assertThat(isCacheableMermaid("gantt\n    title A")).isFalse()
    }

    @Test
    fun `sequence diagram is not cacheable`() {
        assertThat(isCacheableMermaid("sequenceDiagram\n    A->>B: Hi")).isFalse()
    }

    @Test
    fun `er diagram is not cacheable`() {
        assertThat(isCacheableMermaid("erDiagram\n    USER ||--o{ ORDER : places")).isFalse()
    }

    @Test
    fun `pie chart is not cacheable`() {
        assertThat(isCacheableMermaid("pie title Pets\n    \"Dogs\" : 50")).isFalse()
    }

    @Test
    fun `class diagram is not cacheable`() {
        assertThat(isCacheableMermaid("classDiagram\n    class Foo")).isFalse()
    }

    @Test
    fun `empty content is not cacheable`() {
        assertThat(isCacheableMermaid("")).isFalse()
        assertThat(isCacheableMermaid("   \n\n")).isFalse()
    }

    @Test
    fun `comment-only content is not cacheable`() {
        assertThat(isCacheableMermaid("%%{init: { 'theme': 'forest' }}%%")).isFalse()
    }

    @Test
    fun `flowchart with tab-separated direction is cacheable`() {
        assertThat(isCacheableMermaid("flowchart\tTD\n    A --> B")).isTrue()
    }
}

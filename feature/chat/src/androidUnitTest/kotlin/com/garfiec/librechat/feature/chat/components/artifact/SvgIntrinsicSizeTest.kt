package com.garfiec.librechat.feature.chat.components.artifact

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SvgIntrinsicSizeTest {

    @Test
    fun `viewBox attribute gives aspect ratio`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 200"></svg>"""
        assertThat(parseSvgAspectRatio(svg)).isEqualTo(2.0f)
    }

    @Test
    fun `viewBox tolerates negative origin and decimal extents`() {
        val svg = """<svg viewBox="-10 -5 320.5 160.25" xmlns="http://www.w3.org/2000/svg"></svg>"""
        assertThat(parseSvgAspectRatio(svg)).isWithin(0.001f).of(320.5f / 160.25f)
    }

    @Test
    fun `width and height attributes are the fallback when viewBox is absent`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="600px" height="300px"></svg>"""
        assertThat(parseSvgAspectRatio(svg)).isEqualTo(2.0f)
    }

    @Test
    fun `returns null when neither viewBox nor width and height present`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg"><g/></svg>"""
        assertThat(parseSvgAspectRatio(svg)).isNull()
    }

    @Test
    fun `returns null when only one of width or height is present`() {
        val svg = """<svg width="400" xmlns="http://www.w3.org/2000/svg"></svg>"""
        assertThat(parseSvgAspectRatio(svg)).isNull()
    }

    @Test
    fun `returns null when viewBox has zero extent`() {
        val svg = """<svg viewBox="0 0 0 200"></svg>"""
        assertThat(parseSvgAspectRatio(svg)).isNull()
    }

    @Test
    fun `viewBox match is case-insensitive`() {
        val svg = """<SVG ViewBox="0 0 100 50"></SVG>"""
        assertThat(parseSvgAspectRatio(svg)).isEqualTo(2.0f)
    }

    @Suppress("MaxLineLength") // single-line SVG fixture copied from mermaid output
    @Test
    fun `mermaid-shaped svg output parses`() {
        val svg = """<svg aria-roledescription="flowchart-v2" role="graphics-document document" viewBox="0 0 312.5 354" style="max-width: 100%;" xmlns="http://www.w3.org/2000/svg" width="100%" id="rendered"><g></g></svg>"""
        assertThat(parseSvgAspectRatio(svg)).isWithin(0.001f).of(312.5f / 354f)
    }
}

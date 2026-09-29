package com.garfiec.librechat.feature.chat.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Upstream `ErrorWithDetail`: one line up to 240 characters reads in place; anything else collapses. */
class ErrorDetailLayoutTest {

    @Test
    fun `a single sentence reads in place`() {
        assertThat(isInlineErrorDetail("Gateway rejected the request: unknown field `foo`.")).isTrue()
        assertThat(isInlineErrorDetail("x".repeat(240))).isTrue()
    }

    @Test
    fun `a long detail collapses`() {
        assertThat(isInlineErrorDetail("x".repeat(241))).isFalse()
    }

    @Test
    fun `a multi-line detail collapses however short`() {
        assertThat(isInlineErrorDetail("line one\nline two")).isFalse()
        assertThat(isInlineErrorDetail("a\rb")).isFalse()
    }
}

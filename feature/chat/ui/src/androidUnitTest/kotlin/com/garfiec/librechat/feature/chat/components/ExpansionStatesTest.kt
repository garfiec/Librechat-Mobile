package com.garfiec.librechat.feature.chat.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExpansionStatesTest {

    @Test
    fun `the saved side takes the live side's state once`() {
        val store = ExpansionStates()
        store.record("codeexec:call_0", true)

        assertThat(store.take("codeexec:call_0")).isTrue()
        // A later card reusing the id (an older turn composing for the first time) starts fresh.
        assertThat(store.take("codeexec:call_0")).isNull()
    }

    @Test
    fun `the latest toggle is the one handed over`() {
        val store = ExpansionStates()
        store.record("workspace:msg-1", true)
        store.record("workspace:msg-1", false)

        assertThat(store.take("workspace:msg-1")).isFalse()
    }

    @Test
    fun `a reply's scope differs by pane and by the message it answers`() {
        val primary = expansionScope(ExpansionPane.PRIMARY, "user-1")

        assertThat(expansionScope(ExpansionPane.PRIMARY, "user-1")).isEqualTo(primary)
        assertThat(expansionScope(ExpansionPane.SECONDARY, "user-1")).isNotEqualTo(primary)
        assertThat(expansionScope(ExpansionPane.PRIMARY, "user-2")).isNotEqualTo(primary)
    }

    @Test
    fun `a reply with no parent has no scope, so it carries nothing`() {
        assertThat(expansionScope(ExpansionPane.PRIMARY, null)).isEmpty()
        assertThat(expansionScope(ExpansionPane.PRIMARY, "")).isEmpty()
    }

    @Test
    fun `clearing a pane drops what no saved card took, and only in that pane`() {
        val store = ExpansionStates()
        val primaryKey = "${expansionScope(ExpansionPane.PRIMARY, "user-1")}|codeexec:call_0"
        val secondaryKey = "${expansionScope(ExpansionPane.SECONDARY, "user-1")}|codeexec:call_0"
        store.record(primaryKey, true)
        store.record(secondaryKey, true)

        store.clear(ExpansionPane.PRIMARY)

        assertThat(store.take(primaryKey)).isNull()
        assertThat(store.take(secondaryKey)).isTrue()
    }
}

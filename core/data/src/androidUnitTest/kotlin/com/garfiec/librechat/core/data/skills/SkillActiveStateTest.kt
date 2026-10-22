package com.garfiec.librechat.core.data.skills

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The server's active rule (`resolveSkillActive`) and web's override-deletion on toggle. */
class SkillActiveStateTest {

    private val me = "65f0c0ffee0000000000abcd"
    private val someoneElse = "65f0c0ffee00000000002222"

    private fun active(
        author: String? = someoneElse,
        source: String? = "inline",
        states: Map<String, Boolean> = emptyMap(),
        userId: String? = me,
        defaultActiveOnShare: Boolean = false,
    ) = resolveSkillActive("sk", author, source, states, userId, defaultActiveOnShare)

    @Test
    fun `an override wins over every default`() {
        assertThat(active(author = me, states = mapOf("sk" to false))).isFalse()
        assertThat(active(source = "deployment", states = mapOf("sk" to false))).isFalse()
        assertThat(active(states = mapOf("sk" to true))).isTrue()
        assertThat(active(states = mapOf("sk" to true), userId = null)).isTrue()
    }

    @Test
    fun `an owned skill defaults on`() {
        assertThat(active(author = me)).isTrue()
    }

    @Test
    fun `a shared skill follows defaultActiveOnShare, off by default`() {
        assertThat(active()).isFalse()
        assertThat(active(defaultActiveOnShare = true)).isTrue()
    }

    @Test
    fun `a deployment skill defaults on for everyone`() {
        assertThat(active(source = "deployment")).isTrue()
        assertThat(active(source = "deployment", userId = null)).isTrue()
    }

    @Test
    fun `without a user only deployment skills default on`() {
        assertThat(active(author = me, userId = null)).isFalse()
        assertThat(active(userId = null, defaultActiveOnShare = true)).isFalse()
    }

    @Test
    fun `other overrides do not leak onto this skill`() {
        assertThat(active(author = me, states = mapOf("other" to false))).isTrue()
    }

    @Test
    fun `toggling back to the default deletes the override`() {
        val states = mapOf("sk" to false, "other" to true)
        assertThat(nextSkillStates(states, "sk", active = true, defaultActive = true))
            .containsExactly("other", true)
    }

    @Test
    fun `toggling away from the default stores the override and keeps the others`() {
        val states = mapOf("other" to true)
        assertThat(nextSkillStates(states, "sk", active = false, defaultActive = true))
            .containsExactly("other", true, "sk", false)
    }

    @Test
    fun `turning on a shared skill whose default is off stores true`() {
        assertThat(nextSkillStates(emptyMap(), "sk", active = true, defaultActive = false))
            .containsExactly("sk", true)
    }
}

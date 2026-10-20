package com.garfiec.librechat.feature.chat.screen

import com.garfiec.librechat.feature.chat.components.ComparisonLane
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompareSetupPickTest {

    @Test
    fun `starts on B with nothing staged`() {
        val pick = CompareSetupPick()

        assertThat(pick.target).isEqualTo(ComparisonLane.B)
        assertThat(pick.model).isNull()
        assertThat(pick.primaryModel).isNull()
    }

    @Test
    fun `picking for B stages the second model and leaves A alone`() {
        val pick = CompareSetupPick()

        pick.set("anthropic", "claude-opus-4-5")

        assertThat(pick.endpoint).isEqualTo("anthropic")
        assertThat(pick.model).isEqualTo("claude-opus-4-5")
        assertThat(pick.primaryModel).isNull()
    }

    @Test
    fun `picking for A while B is empty stages A and moves on to B`() {
        val pick = CompareSetupPick()
        pick.target = ComparisonLane.A

        pick.set("openAI", "gpt-5")

        assertThat(pick.primaryEndpoint).isEqualTo("openAI")
        assertThat(pick.primaryModel).isEqualTo("gpt-5")
        assertThat(pick.model).isNull()
        assertThat(pick.target).isEqualTo(ComparisonLane.B)
    }

    @Test
    fun `picking for A once B is chosen stays on A`() {
        val pick = CompareSetupPick()
        pick.set("anthropic", "claude-opus-4-5")
        pick.target = ComparisonLane.A

        pick.set("openAI", "gpt-5")

        assertThat(pick.target).isEqualTo(ComparisonLane.A)
        assertThat(pick.model).isEqualTo("claude-opus-4-5")
    }

    @Test
    fun `setSecond stages B even while the list is editing A`() {
        val pick = CompareSetupPick()
        pick.target = ComparisonLane.A

        pick.setSecond("anthropic", "claude-opus-4-5")

        assertThat(pick.model).isEqualTo("claude-opus-4-5")
        assertThat(pick.primaryModel).isNull()
    }

    @Test
    fun `start hands over a staged A with B`() {
        val calls = mutableListOf<String>()
        val actions = CompareSetupActions(
            onCancel = {},
            onStart = { e, m, primary -> calls += "start:$e/$m with ${primary?.first}/${primary?.second}" },
            onToggleAgentFavorite = {},
            onToggleModelFavorite = { _, _ -> },
        )
        val pick = CompareSetupPick()
        pick.target = ComparisonLane.A
        pick.set("openAI", "gpt-5")
        pick.set("anthropic", "claude-opus-4-5")

        pick.start(actions)

        assertThat(calls).containsExactly("start:anthropic/claude-opus-4-5 with openAI/gpt-5")
    }

    @Test
    fun `start does nothing until B is picked`() {
        var started = false
        val actions = CompareSetupActions({}, { _, _, _ -> started = true }, {}, { _, _ -> })

        CompareSetupPick().start(actions)

        assertThat(started).isFalse()
    }

    @Test
    fun `clear drops both lanes and returns to B`() {
        val pick = CompareSetupPick()
        pick.set("anthropic", "claude-opus-4-5")
        pick.target = ComparisonLane.A
        pick.set("openAI", "gpt-5")

        pick.clear()

        assertThat(pick.model).isNull()
        assertThat(pick.primaryModel).isNull()
        assertThat(pick.target).isEqualTo(ComparisonLane.B)
    }
}

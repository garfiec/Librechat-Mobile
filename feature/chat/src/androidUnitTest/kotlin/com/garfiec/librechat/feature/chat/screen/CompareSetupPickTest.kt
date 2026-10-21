package com.garfiec.librechat.feature.chat.screen

import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.feature.chat.components.ComparisonLane
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonPick
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
            onStart = { second, primary ->
                calls += "start:${second.endpoint}/${second.model} with ${primary?.endpoint}/${primary?.model}"
            },
            onToggleAgentFavorite = {},
            onToggleModelFavorite = { _, _ -> },
            onToggleSpecFavorite = {},
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
        val actions = CompareSetupActions({}, { _, _ -> started = true }, {}, { _, _ -> }, {})

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

    private val fast = ModelSpec(name = "fast", preset = Preset(endpoint = "openAI", model = "gpt-4.1"))
    private val writer = ModelSpec(name = "writer", preset = Preset(agentId = "agent_w"))

    @Test
    fun `a spec row stages its pair and name for either lane`() {
        val pick = CompareSetupPick()
        pick.target = ComparisonLane.A
        pick.setSpec(fast)
        pick.setSpec(writer)

        assertThat(pick.primarySpec).isEqualTo("fast")
        assertThat(pick.primaryModel).isEqualTo("gpt-4.1")
        assertThat(pick.spec).isEqualTo("writer")
        assertThat(pick.model).isEqualTo("agent_w")
    }

    @Test
    fun `a raw pick over a staged spec clears the name`() {
        val pick = CompareSetupPick()
        pick.setSpec(fast)

        pick.set("openAI", "gpt-4.1")

        assertThat(pick.spec).isNull()
    }

    @Test
    fun `start hands over both lanes' specs`() {
        var started: Pair<ComparisonPick, ComparisonPick?>? = null
        val actions = CompareSetupActions({}, { second, primary -> started = second to primary }, {}, { _, _ -> }, {})
        val pick = CompareSetupPick()
        pick.target = ComparisonLane.A
        pick.setSpec(fast)
        pick.setSecond("anthropic", "claude-opus-4-5")
        pick.target = ComparisonLane.B
        pick.setSpec(writer)

        pick.start(actions)

        assertThat(started).isEqualTo(
            ComparisonPick("agents", "agent_w", "writer") to ComparisonPick("openAI", "gpt-4.1", "fast"),
        )
    }
}

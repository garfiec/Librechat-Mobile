package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.PromptGroup
import com.garfiec.librechat.core.model.response.PromptRevisionChoice
import com.garfiec.librechat.core.model.response.PromptRevisionChoices
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** v0.8.9-rc1 agents can take their instructions from a linked prompt group (3c90cde7d). */
class AgentInstructionsPromptLinkTest {

    @Test
    fun `a linked agent opens with read-only instructions and saves without touching the link`() {
        val agent = Agent(
            id = "a",
            name = "n",
            instructions = "stale",
            instructionsPrompt = buildJsonObject { put("source", "native"); put("groupId", "g") },
        )
        val state = AgentEditorUiState().applyAgentData(agent)
        assertThat(state.instructionsLinkedToPrompt).isTrue()
        // The request carries no instructionsPrompt at all, so the update leaves the link alone,
        // and the stored text goes back unchanged.
        assertThat(state.toUpdateAgentRequest().instructions).isEqualTo("stale")
        assertThat(state.toUpdateAgentRequest().instructionsPrompt).isNull()
    }

    @Test
    fun `an absent or removed link keeps the field editable`() {
        val plain = Agent(id = "a", name = "n")
        assertThat(AgentEditorUiState().applyAgentData(plain).instructionsLinkedToPrompt).isFalse()
        val removed = plain.copy(instructionsPrompt = JsonNull)
        assertThat(AgentEditorUiState().applyAgentData(removed).instructionsLinkedToPrompt).isFalse()
    }

    // --- Editable links (v0.8.9, revisions route + the admin's Langfuse switch) ---

    private val gated = AgentEditorUiState(
        isEditMode = true,
        agentId = "a",
        isLinkedInstructionsVersionSupported = true,
        isLangfuseLinkingEnabled = true,
    )

    private fun link(source: String, groupId: String, selection: JsonObject) = buildJsonObject {
        put("source", source)
        put("groupId", groupId)
        put("selection", selection)
    }

    private val production = buildJsonObject { put("type", "production") }
    private fun exactVersion(version: Int) = buildJsonObject { put("type", "exact"); put("version", version) }

    private val langfuseAgent =
        Agent(id = "a", name = "n", instructionsPrompt = link("langfuse", "g-lf", exactVersion(3)))

    @Test
    fun `both source shapes parse, and a restricted stub or unknown source is not editable`() {
        assertThat(link("native", "g", production).toInstructionsPromptLink())
            .isEqualTo(InstructionsPromptLink("native", "g", InstructionsPromptSelection.Production))
        assertThat(langfuseAgent.instructionsPrompt.toInstructionsPromptLink())
            .isEqualTo(InstructionsPromptLink("langfuse", "g-lf", InstructionsPromptSelection.ExactVersion(3)))
        // A native link can't pin by version, nor a Langfuse one by prompt id.
        assertThat(link("native", "g", exactVersion(3)).toInstructionsPromptLink()).isNull()
        assertThat(link("other", "g", production).toInstructionsPromptLink()).isNull()

        val stub = buildJsonObject { put("source", "langfuse"); put("restricted", true) }
        val restricted = gated.applyAgentData(Agent(id = "a", name = "n", instructionsPrompt = stub))
        assertThat(restricted.isInstructionsLinkEditable).isFalse()
        assertThat(restricted.instructionsLinkedToPrompt).isTrue()
        assertThat(restricted.toUpdateAgentRequest().instructionsPrompt).isNull()
    }

    @Test
    fun `the revisions route opens the link, the Langfuse switch only governs Langfuse links`() {
        val native = Agent(id = "a", name = "n", instructionsPrompt = link("native", "g", production))
        val off = gated.copy(isLangfuseLinkingEnabled = false)
        assertThat(gated.applyAgentData(langfuseAgent).isInstructionsLinkEditable).isTrue()
        assertThat(off.applyAgentData(native).isInstructionsLinkEditable).isTrue()
        assertThat(off.applyAgentData(Agent(id = "a", name = "n")).isInstructionsLinkEditable).isTrue()
        assertThat(off.copy(isEditMode = false, agentId = null).isInstructionsLinkEditable).isTrue()
        // A saved Langfuse link stays read-only under the "turned off" note.
        assertThat(off.applyAgentData(langfuseAgent).isInstructionsLinkEditable).isFalse()
        assertThat(gated.copy(isLinkedInstructionsVersionSupported = false).applyAgentData(native).isInstructionsLinkEditable)
            .isFalse()
    }

    @Test
    fun `Langfuse groups and their badge are offered only while the switch is on`() {
        val native = PromptGroup(id = "g-n", name = "N", author = "u", authorName = "U")
        val lf = PromptGroup(id = "g-lf", name = "LF", author = "u", authorName = "U", source = "langfuse")
        val loaded = gated.copy(instructionsFromPrompt = true, promptGroups = PromptLinkLoad.Loaded(listOf(native, lf)))

        val on = loaded.instructionsPromptView()?.group as PromptGroupField.Picker
        assertThat(on.options).containsExactly(PromptGroupOption("g-n", "N", false), PromptGroupOption("g-lf", "LF", true))
        val off = loaded.copy(isLangfuseLinkingEnabled = false).instructionsPromptView()?.group as PromptGroupField.Picker
        assertThat(off.options).containsExactly(PromptGroupOption("g-n", "N", false))

        val onlyLangfuse = loaded.copy(isLangfuseLinkingEnabled = false, promptGroups = PromptLinkLoad.Loaded(listOf(lf)))
        assertThat(onlyLangfuse.instructionsPromptView()?.group).isEqualTo(PromptGroupField.Empty)
    }

    @Test
    fun `a saved Langfuse link reads as disabled while the switch is off`() {
        val off = gated.copy(isLangfuseLinkingEnabled = false).applyAgentData(langfuseAgent)
        assertThat(off.isLinkedInstructionsSourceDisabled).isTrue()
        assertThat(gated.applyAgentData(langfuseAgent).isLinkedInstructionsSourceDisabled).isFalse()
        val native = Agent(id = "a", name = "n", instructionsPrompt = link("native", "g", production))
        assertThat(gated.copy(isLangfuseLinkingEnabled = false).applyAgentData(native).isLinkedInstructionsSourceDisabled)
            .isFalse()
    }

    @Test
    fun `an untouched link is not sent, so the form is clean`() {
        val state = gated.applyAgentData(langfuseAgent)
        assertThat(state.instructionsFromPrompt).isTrue()
        assertThat(state.toUpdateAgentRequest().instructionsPrompt).isNull()
        assertThat(state.hasUnsavedChanges()).isFalse()
    }

    @Test
    fun `switching to Inline removes the link with an explicit null`() {
        val state = gated.applyAgentData(langfuseAgent).copy(instructionsFromPrompt = false)
        assertThat(state.toUpdateAgentRequest().instructionsPrompt).isEqualTo(JsonNull)
        assertThat(state.hasUnsavedChanges()).isTrue()
    }

    @Test
    fun `a changed link is sent in the shape web saves, on update and on create`() {
        val picked = InstructionsPromptLink("langfuse", "g-2", InstructionsPromptSelection.ExactVersion(7))
        val updated = gated.applyAgentData(langfuseAgent).copy(instructionsLink = picked)
        assertThat(updated.toUpdateAgentRequest().instructionsPrompt).isEqualTo(link("langfuse", "g-2", exactVersion(7)))

        val created = gated.copy(isEditMode = false, agentId = null, instructionsFromPrompt = true, instructionsLink = picked)
        assertThat(created.toCreateAgentRequest().instructionsPrompt).isEqualTo(picked.toJson())
        assertThat(gated.copy(isEditMode = false, agentId = null).toCreateAgentRequest().instructionsPrompt).isNull()
    }

    @Test
    fun `native versions label Production with its revision and list every revision`() {
        val choices = PromptRevisionChoices(
            source = "native",
            groupId = "g",
            revisions = listOf(
                PromptRevisionChoice("native", version = 2, promptId = "p2", isProduction = true),
                PromptRevisionChoice("native", version = 1, promptId = "p1"),
            ),
        )
        val options = promptVersionOptions(choices, InstructionsPromptSelection.ExactPrompt("p1"))
        assertThat(options.map { it.label }).containsExactly(
            PromptVersionLabel.Production(2),
            PromptVersionLabel.Version(2),
            PromptVersionLabel.Version(1),
        ).inOrder()
    }

    @Test
    fun `a pinned revision no longer listed is kept as no longer available`() {
        val choices = PromptRevisionChoices("langfuse", "g-lf", listOf(PromptRevisionChoice("langfuse", version = 4)))
        val options = promptVersionOptions(choices, InstructionsPromptSelection.ExactVersion(3))
        assertThat(options.last()).isEqualTo(
            PromptVersionOption(PromptVersionLabel.Missing(3), InstructionsPromptSelection.ExactVersion(3)),
        )
        assertThat(promptVersionOptions(choices, InstructionsPromptSelection.Production)).hasSize(2)
    }

    @Test
    fun `the view selects the saved revision and marks a linked group missing from the library`() {
        val choices = PromptRevisionChoices(
            "langfuse",
            "g-lf",
            listOf(PromptRevisionChoice("langfuse", version = 4), PromptRevisionChoice("langfuse", version = 3)),
        )
        val state = gated.applyAgentData(langfuseAgent).copy(
            promptGroups = PromptLinkLoad.Loaded(
                listOf(PromptGroup(id = "g-lf", name = "LF", author = "u", authorName = "U", source = "langfuse")),
            ),
            promptRevisions = PromptLinkLoad.Loaded(GroupRevisions("g-lf", choices)),
        )
        val view = state.instructionsPromptView()!!
        val picker = view.group as PromptGroupField.Picker
        assertThat(picker.selected).isEqualTo(PromptGroupOption("g-lf", "LF", isLangfuse = true))
        assertThat(view.linkedGroupMissing).isFalse()
        val versions = view.version as PromptVersionField.Picker
        assertThat(versions.options[versions.selectedIndex].label).isEqualTo(PromptVersionLabel.Version(3))

        // Revisions read for another group are not shown for this one.
        val stale = state.copy(promptRevisions = PromptLinkLoad.Loaded(GroupRevisions("other", choices)))
        assertThat(stale.instructionsPromptView()!!.version).isEqualTo(PromptVersionField.Loading)

        val missing = state.copy(promptGroups = PromptLinkLoad.Loaded(emptyList()))
        assertThat(missing.instructionsPromptView()!!.linkedGroupMissing).isTrue()
    }

    @Test
    fun `revision failures map to forbidden, source unavailable or a retryable error`() {
        fun failure(status: Int, body: String?) = ApiException(status, "x", body = body).promptRevisionsFailure()
        assertThat(failure(403, null)).isEqualTo(PromptLinkFailure.FORBIDDEN)
        assertThat(failure(404, """{"code":"not_available"}""")).isEqualTo(PromptLinkFailure.SOURCE_UNAVAILABLE)
        assertThat(failure(409, """{"code":"source_changed"}""")).isEqualTo(PromptLinkFailure.SOURCE_UNAVAILABLE)
        assertThat(failure(422, """{"code":"unsupported_type"}""")).isEqualTo(PromptLinkFailure.SOURCE_UNAVAILABLE)
        assertThat(failure(404, """{"code":"not_found"}""")).isEqualTo(PromptLinkFailure.GENERIC)
        assertThat(null.promptRevisionsFailure()).isEqualTo(PromptLinkFailure.GENERIC)
    }
}

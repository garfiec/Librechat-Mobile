package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentSubagentsConfig
import com.garfiec.librechat.core.model.SupportContact
import com.garfiec.librechat.core.model.request.CreateAgentRequest
import com.garfiec.librechat.core.model.request.UpdateAgentRequest
import com.garfiec.librechat.feature.agents.components.agentModelRemovedOptions
import com.garfiec.librechat.feature.agents.components.model.AgentVisibility
import com.garfiec.librechat.feature.agents.components.withoutModelRemovedValues
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Whether Save would change the agent — the current form against the same form reset to
 * [AgentEditorUiState.loadedAgent] (an empty agent when creating).
 *
 * Compared through the request, not field by field, so state that never reaches the server can't
 * make the form dirty: files and the avatar are written the moment they're picked, and reference
 * data (the MCP catalog resolving server names, the model list, config gates) lands after the
 * agent does without being anything the user changed.
 */
internal fun AgentEditorUiState.hasUnsavedChanges(): Boolean {
    val baseline = applyAgentData(loadedAgent ?: EMPTY_AGENT)
    return toUpdateAgentRequest() != baseline.toUpdateAgentRequest()
}

private val EMPTY_AGENT = Agent(id = "")

internal fun AgentEditorUiState.toUpdateAgentRequest(): UpdateAgentRequest {
    val fields = writeFields()
    return UpdateAgentRequest(
        name = name,
        description = description.ifBlank { null },
        instructions = instructions.ifBlank { null },
        model = model.ifBlank { null },
        provider = provider.ifBlank { null },
        modelParameters = fields.modelParameters,
        artifacts = fields.artifacts,
        recursionLimit = capabilities.recursionLimit,
        hideSequentialOutputs = capabilities.hideSequentialOutputs,
        endAfterTools = capabilities.endAfterTools,
        category = category.ifBlank { null },
        tools = fields.tools.ifEmpty { null },
        conversationStarters = conversationStarters.ifEmpty { null },
        isPublic = fields.isPublic,
        isCollaborative = fields.isCollaborative,
        supportContact = fields.supportContact,
        agentIds = fields.chainAgentIds,
        edges = fields.handoffEdges,
        toolOptions = fields.toolOptions,
        additionalInstructions = additionalInstructions,
        toolKwargs = toolKwargs,
        skills = fields.skills,
        skillsEnabled = fields.skillsEnabled,
        subagents = fields.subagents,
    )
}

internal fun AgentEditorUiState.toCreateAgentRequest(): CreateAgentRequest {
    val fields = writeFields()
    return CreateAgentRequest(
        name = name,
        description = description.ifBlank { null },
        instructions = instructions.ifBlank { null },
        model = model.ifBlank { null },
        provider = provider.ifBlank { null },
        modelParameters = fields.modelParameters,
        artifacts = fields.artifacts,
        recursionLimit = capabilities.recursionLimit,
        hideSequentialOutputs = capabilities.hideSequentialOutputs,
        endAfterTools = capabilities.endAfterTools,
        category = category.ifBlank { null },
        tools = fields.tools.ifEmpty { null },
        conversationStarters = conversationStarters.ifEmpty { null },
        isPublic = fields.isPublic,
        isCollaborative = fields.isCollaborative,
        supportContact = fields.supportContact,
        agentIds = fields.chainAgentIds,
        edges = fields.handoffEdges,
        toolOptions = fields.toolOptions,
        additionalInstructions = additionalInstructions,
        toolKwargs = toolKwargs,
        skills = fields.skills,
        skillsEnabled = fields.skillsEnabled,
        subagents = fields.subagents,
    )
}

/** The request fields that take more than a straight copy, shared by create and update. */
@Suppress("LongParameterList") // debt: constructor dependencies
private class AgentWriteFields(
    val isPublic: Boolean,
    val isCollaborative: Boolean?,
    val supportContact: SupportContact?,
    val tools: List<String>,
    val toolOptions: JsonObject?,
    val modelParameters: JsonObject?,
    val artifacts: String?,
    val chainAgentIds: List<String>?,
    val handoffEdges: List<JsonElement>?,
    val skillsEnabled: Boolean?,
    val skills: List<String>?,
    val subagents: AgentSubagentsConfig?,
)

private fun AgentEditorUiState.writeFields(): AgentWriteFields {
    val state = this
    val isPublic = state.sharingState.visibility == AgentVisibility.PUBLIC
    // On v0.8.5+ the server dropped `isCollaborative` / `projectIds` in favor
    // of ACL permissions. When the toggle is hidden we omit the field so the
    // server doesn't silently ignore it. See VERSION_GATES.md.
    val isCollaborative = if (state.showCollaborativeToggle) {
        state.sharingState.isCollaborative
    } else {
        null
    }

    val supportContact = if (state.supportContact.name.isNotBlank() ||
        state.supportContact.email.isNotBlank()
    ) {
        SupportContact(
            name = state.supportContact.name.ifBlank { null },
            email = state.supportContact.email.ifBlank { null },
        )
    } else {
        null
    }

    // Build the full tools list: user-selected tools + capability tools + MCP server markers
    val allTools = buildToolsList(state)

    // Prune `tool_options` to the keys still present in the agent's
    // current tool selection. Upstream keys this map by tool name
    // (MCP tool names appear without the `_mcp_serverName` suffix —
    // see `client/src/components/SidePanel/Agents/MCPToolItem.tsx`),
    // so we match against the bare names: `selectedMcpTools` for MCP
    // and `selectedTools` for regular tools. Without this prune, a
    // user who deselects an MCP tool whose options were configured
    // via the web client would still ship those tool_options on
    // save, producing zombie config that re-appears the next time
    // the tool is re-added.
    val keepableToolOptionKeys = state.selectedMcpTools.toSet() + state.selectedTools.toSet()
    val prunedToolOptions = state.toolOptions?.let { options ->
        val filtered = options.filterKeys { it in keepableToolOptionKeys }
        if (filtered.isEmpty()) null else JsonObject(filtered)
    }

    // Build model_parameters from advanced settings, minus any value a per-model rule took
    // away. Checked here and not only when the Advanced panel edits a control: a save that
    // touched nothing in it would otherwise write the loaded values back as-is. Not "absent
    // from the options": those also lack version-gated values (`max` while the version is
    // undetected) and values newer than this app, which this save would delete.
    val modelRemoved = agentModelRemovedOptions(
        provider = state.provider,
        model = state.model,
        dropParamsMap = state.dropParamsMap,
    )
    val modelParameters = buildModelParameters(state.advancedSettings.withoutModelRemovedValues(modelRemoved))

    // Artifacts: upstream `ArtifactModes` enum serialized as its wire string.
    // null means "off" (omitted from the request body via encodeDefaults=false).
    val artifacts = state.capabilities.artifactsMode?.wire

    // Chain (sequential agents) + handoffs (graph edges). For CREATE,
    // omit when empty (no prior state to clear). For UPDATE, always
    // send the current value — including empty lists — so removing
    // every chain target or every handoff edge actually clears the
    // server-side list. Coercing empty → null on update would let the
    // server's "missing field = no change" rule swallow the deletion.
    val isUpdate = state.isEditMode && state.agentId != null
    val chainAgentIds = if (isUpdate) state.chainAgentIds else state.chainAgentIds.ifEmpty { null }
    // Append any raw edges that failed to deserialize on load (forward-
    // compatibility for new upstream edge fields the mobile model
    // doesn't model yet). Without re-emitting these, a single decoder
    // mismatch would silently clear all server-side edges on save.
    val handoffEdges = if (isUpdate) {
        encodeHandoffEdgesAlways(state.handoffEdges) + state.unparsedHandoffEdges
    } else {
        val encoded = encodeHandoffEdges(state.handoffEdges).orEmpty() + state.unparsedHandoffEdges
        encoded.ifEmpty { null }
    }

    // Skills (v0.8.6). Write shape per the zod agentBaseSchema
    // (skills/skills_enabled both optional) + the server's $set merge:
    // when the toggle is off, send skills_enabled=false and drop the
    // allowlist. When on, send the toggle plus the current allowlist
    // (empty = "full catalog"; the server stores skills_enabled=true
    // and omits the allowlist). On UPDATE always send both fields so
    // turning skills off, or clearing the allowlist, is honored via the
    // $set merge; on CREATE omit when off (nothing to clear). On read
    // the server scrubs the allowlist to ids the caller can access, so
    // applyAgentData re-hydrates from the saved agent rather than
    // trusting this list.
    val skillsEnabled: Boolean?
    val skills: List<String>?
    when {
        !state.skillsEnabled -> {
            skillsEnabled = if (isUpdate) false else null
            skills = if (isUpdate) emptyList() else null
        }
        else -> {
            skillsEnabled = true
            skills = state.selectedSkillIds
        }
    }

    // Subagents config (v0.8.6). Same persist semantics as skills: when
    // off, send an explicit `{ enabled: false, ... }` on UPDATE (not
    // null) so the server's removeNullishValues doesn't strip it and the
    // $set merge actually clears it; omit on CREATE. When on, send
    // enabled + allowSelf + the agent_ids allowlist (self never included).
    val subagents: AgentSubagentsConfig? = when {
        !state.subagentsEnabled ->
            if (isUpdate) {
                AgentSubagentsConfig(
                    enabled = false,
                    allowSelf = state.subagentAllowSelf,
                    agentIds = state.selectedSubagentIds,
                    shareFiles = state.subagentShareFiles,
                    graphs = state.subagentGraphs,
                )
            } else {
                null
            }
        else -> AgentSubagentsConfig(
            enabled = true,
            allowSelf = state.subagentAllowSelf,
            agentIds = state.selectedSubagentIds,
            shareFiles = state.subagentShareFiles,
            graphs = state.subagentGraphs,
        )
    }

    return AgentWriteFields(
        isPublic = isPublic,
        isCollaborative = isCollaborative,
        supportContact = supportContact,
        tools = allTools,
        toolOptions = prunedToolOptions,
        modelParameters = modelParameters,
        artifacts = artifacts,
        chainAgentIds = chainAgentIds,
        handoffEdges = handoffEdges,
        skillsEnabled = skillsEnabled,
        skills = skills,
        subagents = subagents,
    )
}

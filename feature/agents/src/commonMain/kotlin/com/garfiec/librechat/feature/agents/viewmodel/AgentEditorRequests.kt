package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.ToolConstants
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
    val loadedCode = loadedAgent?.let(AgentCodeSettings::of) ?: AgentCodeSettings()
    val code = code.updateFields(loadedCode, codeInterpreterEnabled && isCodeInterpreterAvailable)
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
        statefulCodeSessions = code.statefulSessions,
        statefulCodeEnvironment = code.scope,
        codeEnvironmentId = code.environmentId,
        codeEnvironmentIds = code.extraEnvironmentIds,
        codeWorkspaceId = code.workspaceId,
        repositoryInstructions = code.repositoryInstructions,
        gitIdentity = code.gitIdentity,
    )
}

internal fun AgentEditorUiState.toCreateAgentRequest(): CreateAgentRequest {
    val fields = writeFields()
    val codeEnabled = codeInterpreterEnabled && isCodeInterpreterAvailable
    val code = code.createFields(codeEnabled)
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
        statefulCodeSessions = code.statefulSessions,
        statefulCodeEnvironment = code.scope,
        codeEnvironmentId = this.code.environmentId,
        codeEnvironmentIds = code.extraEnvironmentIds,
        codeWorkspaceId = code.workspaceId,
        repositoryInstructions = code.repositoryInstructions,
        gitIdentity = this.code.createGitIdentity(),
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

    val keptToolOptions = pruneToolOptions(state.toolOptions, allTools, state.selectedMcpTools)

    // Build model_parameters from advanced settings, minus any value a per-model rule took
    // away. Checked here and not only when the Advanced panel edits a control: a save that
    // touched nothing in it would otherwise write the loaded values back as-is. Not "absent
    // from the options": those also lack version-gated values (`max` while the version is
    // undetected) and values newer than this app, which this save would delete.
    val modelRemoved = agentModelRemovedOptions(
        provider = state.provider,
        model = state.model,
        dropParamsMap = state.dropParamsMap,
        parameterProvider = state.parameterProvider,
    )
    val modelParameters = buildModelParameters(state.advancedSettings.withoutModelRemovedValues(modelRemoved))
        ?.let { params ->
            if (!state.filtersInferenceParameters) return@let params
            val endpoint = state.inferenceEndpoints[state.provider]
            // A custom endpoint's declared extras are unknown until /api/endpoints has loaded;
            // filtering then would delete them, so the agent is saved as it is.
            if (endpoint == null && !isBuiltInInferenceProvider(state.provider)) return@let params
            JsonObject(keepAcceptedInferenceParameters(params, state.provider, state.model.ifBlank { null }, endpoint))
                .takeIf { it.isNotEmpty() }
        }

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
    // An omitted `tool_options` leaves the stored map as it was, so on UPDATE an agent that had
    // options and now has none sends `{}`: otherwise the last approval mode set back to inherit
    // (or the last configured tool removed) would never clear on the server.
    val prunedToolOptions = when {
        keptToolOptions != null -> keptToolOptions
        isUpdate && !state.loadedAgent?.toolOptions.isNullOrEmpty() -> JsonObject(emptyMap())
        else -> null
    }
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

/**
 * `tool_options` kept to the tools still on the agent.
 *
 * Upstream keys an MCP tool's options by its full id, `<tool>_mcp_<normalizedServer>`, the same
 * string as its `agent.tools` entry (`McpSection.tsx` `tool.tool_id`, `useMCPToolOptions.ts`), and
 * the update route stores the map it is sent as is. So a key survives when it is one of the
 * [savedTools] being written, or an MCP id under a whole-server marker being written. A bare key
 * still on [selectedMcpTools] survives too: older agents may carry one, and dropping it here would
 * be this client deleting what it doesn't own.
 *
 * Without the prune, a tool removed here would keep its options and they would come back the next
 * time the tool is added. `bash_tool` follows `execute_code`: kept while the code interpreter is
 * saved, dropped with it (web keeps both even with code off).
 */
internal fun pruneToolOptions(
    options: JsonObject?,
    savedTools: List<String>,
    selectedMcpTools: Set<String>,
): JsonObject? {
    if (options == null) return null
    val savedToolSet = savedTools.toSet()
    val wholeServerSuffixes = savedTools
        .filter { it.startsWith(MCP_SERVER_MARKER_PREFIX) }
        .map { MCP_DELIMITER + it.removePrefix(MCP_SERVER_MARKER_PREFIX) }
    val kept = options.filterKeys { key ->
        key in savedToolSet ||
            // The sandbox pair shares the code interpreter's switches (`withCodeBackground`), and
            // `bash_tool` is never an `agent.tools` entry.
            (key == ToolConstants.BASH_TOOL && ToolConstants.EXECUTE_CODE in savedToolSet) ||
            key in selectedMcpTools ||
            // A selected tool whose catalog entry hasn't loaded is saved under the wrong key
            // (a server marker), so its full-id options are kept rather than lost as well.
            key.substringBefore(MCP_DELIMITER, missingDelimiterValue = "") in selectedMcpTools ||
            wholeServerSuffixes.any { suffix -> key.endsWith(suffix) && key.length > suffix.length }
    }
    return if (kept.isEmpty()) null else JsonObject(kept)
}

private const val MCP_DELIMITER = "_mcp_"
private const val MCP_SERVER_MARKER_PREFIX = "sys__server__sys_mcp_"

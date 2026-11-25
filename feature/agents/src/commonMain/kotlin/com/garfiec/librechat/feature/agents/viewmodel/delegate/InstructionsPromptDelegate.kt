package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.PromptRepository
import com.garfiec.librechat.core.model.PROMPT_SOURCE_LANGFUSE
import com.garfiec.librechat.core.model.PROMPT_SOURCE_NATIVE
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.GroupRevisions
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptLink
import com.garfiec.librechat.feature.agents.viewmodel.InstructionsPromptSelection
import com.garfiec.librechat.feature.agents.viewmodel.PromptLinkFailure
import com.garfiec.librechat.feature.agents.viewmodel.PromptLinkLoad
import com.garfiec.librechat.feature.agents.viewmodel.offeredPromptGroups
import com.garfiec.librechat.feature.agents.viewmodel.promptGroupsFailure
import com.garfiec.librechat.feature.agents.viewmodel.promptRevisionsFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The editable linked-instructions section (upstream `InstructionsPromptFields`, v0.8.9): Inline or
 * Prompt, the prompt group, and its revision. The library is read once Prompt mode shows; a group's
 * revisions are read on every pick, uncached, since a Langfuse group's list changes live.
 */
class InstructionsPromptDelegate(
    private val stateHandle: AgentEditorStateHandle,
    private val configRepository: ConfigRepository,
    private val promptRepository: PromptRepository,
) {
    private val revisionsRetry = MutableStateFlow(0)

    fun start() {
        observeGates()
        observeGroups()
        observeRevisions()
    }

    /** See VERSION_GATES.md: the revisions route opens the section; the admin's switch adds Langfuse groups. */
    private fun observeGates() {
        stateHandle.scope.launch {
            combine(configRepository.detectedBackend, configRepository.endpointConfigs) { detected, configs ->
                BackendVersion.supportsFeature(detected, "0.8.9", "2026-10-30") to
                    (configs["agents"]?.linkedInstructions?.langfuse?.enabled == true)
            }.distinctUntilChanged().collect { (versionSupported, langfuseEnabled) ->
                stateHandle.update {
                    copy(isLinkedInstructionsVersionSupported = versionSupported, isLangfuseLinkingEnabled = langfuseEnabled)
                }
            }
        }
    }

    private fun observeGroups() {
        stateHandle.scope.launch {
            stateHandle.stateFlow
                .map { it.isInstructionsLinkEditable && it.instructionsFromPrompt }
                .distinctUntilChanged()
                .collect { shown -> if (shown && stateHandle.state.promptGroups == PromptLinkLoad.Idle) loadGroups() }
        }
    }

    private fun observeRevisions() {
        stateHandle.scope.launch {
            combine(
                stateHandle.stateFlow
                    .map { s -> s.instructionsLink?.groupId?.takeIf { s.isInstructionsLinkEditable && s.instructionsFromPrompt } }
                    .distinctUntilChanged(),
                revisionsRetry,
            ) { groupId, _ -> groupId }.collectLatest { groupId ->
                if (groupId == null) return@collectLatest
                stateHandle.update { copy(promptRevisions = PromptLinkLoad.Loading) }
                val result = promptRepository.getRevisionChoices(groupId)
                stateHandle.update {
                    copy(
                        promptRevisions = when (result) {
                            is Result.Success -> PromptLinkLoad.Loaded(GroupRevisions(groupId, result.data))
                            is Result.Error -> PromptLinkLoad.Failed(result.exception.promptRevisionsFailure())
                            is Result.Loading -> promptRevisions
                        },
                    )
                }
            }
        }
    }

    private fun loadGroups() {
        stateHandle.update { copy(promptGroups = PromptLinkLoad.Loading) }
        stateHandle.scope.launch {
            val result = promptRepository.getAllGroups()
            stateHandle.update {
                copy(
                    promptGroups = when (result) {
                        is Result.Success -> PromptLinkLoad.Loaded(result.data)
                        is Result.Error -> PromptLinkLoad.Failed(result.exception.promptGroupsFailure())
                        is Result.Loading -> promptGroups
                    },
                )
            }
        }
    }

    fun onSourceChanged(fromPrompt: Boolean) {
        stateHandle.update { copy(instructionsFromPrompt = fromPrompt, instructionsPromptError = false) }
    }

    /** A new group starts at Production; picking the linked group again keeps its pinned revision. */
    fun onGroupSelected(groupId: String) {
        stateHandle.update {
            val group = offeredPromptGroups()?.firstOrNull { it.id == groupId }
            if (group == null || instructionsLink?.groupId == groupId) return@update this
            val source = if (group.source == PROMPT_SOURCE_LANGFUSE) PROMPT_SOURCE_LANGFUSE else PROMPT_SOURCE_NATIVE
            copy(
                instructionsLink = InstructionsPromptLink(source, groupId, InstructionsPromptSelection.Production),
                instructionsPromptError = false,
            )
        }
    }

    /** Refuses a selection of the other source's shape: the server rejects it. */
    fun onVersionSelected(selection: InstructionsPromptSelection) {
        stateHandle.update {
            val link = instructionsLink ?: return@update this
            val wrongShape = when (link.source) {
                PROMPT_SOURCE_LANGFUSE -> selection is InstructionsPromptSelection.ExactPrompt
                else -> selection is InstructionsPromptSelection.ExactVersion
            }
            if (wrongShape) this else copy(instructionsLink = link.copy(selection = selection))
        }
    }

    fun retryGroups() {
        val load = stateHandle.state.promptGroups
        if (load is PromptLinkLoad.Failed && load.reason != PromptLinkFailure.FORBIDDEN) loadGroups()
    }

    fun retryRevisions() {
        val load = stateHandle.state.promptRevisions
        if (load is PromptLinkLoad.Failed && load.reason != PromptLinkFailure.FORBIDDEN) revisionsRetry.value += 1
    }
}

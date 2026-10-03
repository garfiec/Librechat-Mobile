package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.model.request.RevertAgentRequest
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorEvent
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.applyAgentData
import com.garfiec.librechat.feature.agents.viewmodel.toCreateAgentRequest
import com.garfiec.librechat.feature.agents.viewmodel.toUpdateAgentRequest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * Owns the editor's persistence mutations: form validation + create/update
 * ([save]), plus [duplicate], [delete], and [revertToVersion]. Sends the
 * create/update request body built from [AgentEditorUiState] (see
 * AgentEditorRequests) and emits the corresponding
 * [AgentEditorEvent] on success so the screen can navigate.
 */
class AgentSaveDelegate(
    private val stateHandle: AgentEditorStateHandle,
    private val agentRepository: AgentRepository,
    private val filesDelegate: AgentFilesDelegate,
    private val events: MutableSharedFlow<AgentEditorEvent>,
) {

    fun save() {
        val state = stateHandle.state

        // Validate -- mirror upstream zod schema constraints from
        // packages/data-provider/src/schemas.ts agentSchema.
        val nameError = when {
            state.name.isBlank() -> "Name is required"
            state.name.length > NAME_MAX -> "Name must be at most $NAME_MAX characters"
            else -> null
        }
        val descriptionError = when {
            state.description.length > DESCRIPTION_MAX ->
                "Description must be at most $DESCRIPTION_MAX characters"
            else -> null
        }
        val contactName = state.supportContact.name
        val contactEmail = state.supportContact.email
        val supportContactNameError = when {
            contactName.isNotBlank() && contactName.length < SUPPORT_NAME_MIN ->
                "Support contact name must be at least $SUPPORT_NAME_MIN characters"
            else -> null
        }
        val supportContactEmailError = when {
            contactEmail.isNotBlank() && !EMAIL_REGEX.matches(contactEmail) ->
                "Enter a valid email address"
            else -> null
        }
        val hasErrors = nameError != null || descriptionError != null ||
            supportContactNameError != null || supportContactEmailError != null
        if (hasErrors) {
            stateHandle.update {
                copy(
                    nameError = nameError,
                    descriptionError = descriptionError,
                    supportContactNameError = supportContactNameError,
                    supportContactEmailError = supportContactEmailError,
                )
            }
            return
        }

        stateHandle.scope.launch {
            stateHandle.update { copy(isSaving = true, error = null) }

            val result = if (state.isEditMode && state.agentId != null) {
                agentRepository.updateAgent(id = state.agentId, request = state.toUpdateAgentRequest())
            } else {
                agentRepository.createAgent(request = state.toCreateAgentRequest())
            }

            when (result) {
                is Result.Success -> {
                    stateHandle.update { copy(isSaving = false) }
                    events.emit(AgentEditorEvent.SaveSuccess(result.data.id))
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(isSaving = false, error = result.message ?: "Failed to save agent")
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun duplicate() {
        val agentId = stateHandle.state.agentId ?: return
        stateHandle.scope.launch {
            stateHandle.update { copy(isDuplicating = true, showDuplicateConfirm = false) }
            when (val result = agentRepository.duplicateAgent(agentId)) {
                is Result.Success -> {
                    stateHandle.update { copy(isDuplicating = false) }
                    events.emit(AgentEditorEvent.DuplicateSuccess(result.data.id))
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(isDuplicating = false, error = result.message ?: "Failed to duplicate agent")
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun delete() {
        val agentId = stateHandle.state.agentId ?: return
        stateHandle.scope.launch {
            stateHandle.update { copy(isDeleting = true, showDeleteConfirm = false) }
            when (val result = agentRepository.deleteAgent(agentId)) {
                is Result.Success -> {
                    stateHandle.update { copy(isDeleting = false) }
                    events.emit(AgentEditorEvent.DeleteSuccess)
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(isDeleting = false, error = result.message ?: "Failed to delete agent")
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun revertToVersion(version: Int) {
        val agentId = stateHandle.state.agentId ?: return
        stateHandle.scope.launch {
            stateHandle.update { copy(showVersionHistory = false, isLoading = true) }
            when (val result = agentRepository.revertAgent(agentId, RevertAgentRequest(version))) {
                is Result.Success -> {
                    stateHandle.update { applyAgentData(result.data).copy(isLoading = false) }
                    // A reverted version often has a different file set (different
                    // execute_code / file_search / context attachments). Clear the
                    // stale enrichment cache and re-fetch /api/files/agent/:id so
                    // the new file_ids resolve to filename/bytes/type instead of
                    // showing bare IDs in the chips.
                    filesDelegate.resetFileCache()
                    filesDelegate.loadAgentFiles(agentId)
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(isLoading = false, error = result.message ?: "Failed to revert agent")
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private companion object {
        /** Validation limits mirrored from upstream agentSchema. */
        const val NAME_MAX = 256
        const val DESCRIPTION_MAX = 512
        const val SUPPORT_NAME_MIN = 3

        // Pragmatic email regex matching upstream client-side validateEmail.
        // Server still runs its own check, so this only catches obvious typos.
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

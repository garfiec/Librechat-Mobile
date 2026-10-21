package com.garfiec.librechat.feature.agents.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentAclUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val principals: List<Principal> = emptyList(),
    val isPublic: Boolean = false,
    val publicAccessRoleId: String? = null,
    val availableRoles: List<AccessRole> = emptyList(),
    val showGrantDialog: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Principal> = emptyList(),
    val isSearching: Boolean = false,
)

/**
 * Standalone ACL viewmodel for the agent editor's sharing section. Kept
 * separate from [AgentEditorViewModel] so the ACL surface can be toggled in
 * without colliding with the editor's existing state shape.
 */
class AgentAclViewModel(
    private val permissionsRepository: PermissionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentAclUiState())
    val uiState: StateFlow<AgentAclUiState> = _uiState.asStateFlow()

    private var currentAgentId: String? = null
    private var searchJob: Job? = null

    fun load(agentId: String) {
        currentAgentId = agentId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val rolesResult = permissionsRepository.getResourceRoles(ResourceType.AGENT)
            val roles = (rolesResult as? Result.Success)?.data ?: emptyList()

            when (val grantsResult = permissionsRepository.getResourcePermissions(ResourceType.AGENT, agentId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        principals = grantsResult.data.principals,
                        isPublic = grantsResult.data.public,
                        publicAccessRoleId = grantsResult.data.publicAccessRoleId,
                        availableRoles = roles,
                        error = null,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        availableRoles = roles,
                        error = grantsResult.message,
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun openGrantDialog() {
        _uiState.value = _uiState.value.copy(showGrantDialog = true, searchQuery = "", searchResults = emptyList())
    }

    fun dismissGrantDialog() {
        _uiState.value = _uiState.value.copy(showGrantDialog = false)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true)
            when (val r = permissionsRepository.searchPrincipals(query)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(searchResults = r.data, isSearching = false)
                is Result.Error -> {
                    Logger.d(r.exception) { "searchPrincipals failed: ${r.message}" }
                    _uiState.value = _uiState.value.copy(isSearching = false)
                }
                is Result.Loading -> Unit
            }
        }
    }

    // grant() and revoke() leave `public` out of the body (null): any value there turns public
    // access on or off. Only setPublic() sends it.
    fun grant(principal: Principal, accessRoleId: String) {
        val agentId = currentAgentId ?: return
        val granted = principal.copy(accessRoleId = accessRoleId)
        viewModelScope.launch {
            val body = UpdateResourcePermissionsRequest(
                updated = listOf(granted),
                removed = emptyList(),
            )
            applyUpdate(agentId, body, dismissDialogOnSuccess = true)
        }
    }

    fun revoke(principal: Principal) {
        val agentId = currentAgentId ?: return
        viewModelScope.launch {
            val body = UpdateResourcePermissionsRequest(
                updated = emptyList(),
                removed = listOf(principal),
            )
            applyUpdate(agentId, body, dismissDialogOnSuccess = false)
        }
    }

    fun setPublic(enabled: Boolean, accessRoleId: String?) {
        val agentId = currentAgentId ?: return
        viewModelScope.launch {
            val body = UpdateResourcePermissionsRequest(
                updated = emptyList(),
                removed = emptyList(),
                public = enabled,
                publicAccessRoleId = accessRoleId,
            )
            applyUpdate(agentId, body, dismissDialogOnSuccess = false)
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // The PUT answers `{message, results}`, not the GET shape, so the state read from it below is
    // incomplete until the next load().
    private suspend fun applyUpdate(
        agentId: String,
        body: UpdateResourcePermissionsRequest,
        dismissDialogOnSuccess: Boolean,
    ) {
        when (val r = permissionsRepository.updateResourcePermissions(ResourceType.AGENT, agentId, body)) {
            is Result.Success -> {
                _uiState.value = _uiState.value.copy(
                    principals = r.data.principals,
                    isPublic = r.data.public,
                    publicAccessRoleId = r.data.publicAccessRoleId,
                    showGrantDialog = if (dismissDialogOnSuccess) false else _uiState.value.showGrantDialog,
                    error = null,
                )
            }
            is Result.Error -> {
                _uiState.value = _uiState.value.copy(error = r.message)
            }
            is Result.Loading -> Unit
        }
    }
}

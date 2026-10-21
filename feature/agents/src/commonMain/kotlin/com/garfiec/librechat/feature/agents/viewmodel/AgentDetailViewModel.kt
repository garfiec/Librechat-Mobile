package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.FavoritesLimits
import com.garfiec.librechat.core.model.UserFavorite
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.agents.AgentDetailDisplayData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AgentDetailUiState(
    val agent: AgentDetailDisplayData? = null,
    val isLoading: Boolean = false,
    /** The page could not be loaded at all; a failed refresh of a shown page leaves this null. */
    val loadError: AgentLoadError? = null,
    /**
     * The edit-gated `/expanded` read answered, so the tools are the agent's own. The VIEW read
     * sends none, and a list row only a cut-down projection, so the page shows Tools only then.
     * Model is on the VIEW read too and doesn't wait for this.
     */
    val isFullRecord: Boolean = false,
    val canEdit: Boolean = false,
    /** The role's AGENTS.CREATE. Permissive until the role says otherwise. */
    val canCreate: Boolean = true,
    /** Pinned to the chat model menu (`/api/user/settings/favorites`). */
    val isPinned: Boolean = false,
    /** The server's categories, for an admin's own category label. Empty until (unless) they load. */
    val categories: List<AgentCategory> = emptyList(),
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val isDuplicating: Boolean = false,
    /** A Delete, Duplicate or pin that failed. Shown once as a snackbar; the page stays. */
    val actionError: AgentActionError? = null,
) {
    /** The duplicate route needs AGENTS.CREATE and EDIT on the source (upstream routes/agents/v1.js). */
    val canDuplicate: Boolean get() = canEdit && canCreate

    /**
     * The delete route needs AGENTS.CREATE and the agent's DELETE bit. Nothing reports DELETE on its
     * own, so EDIT stands in for it; a 403 still lands in the action-error snackbar.
     */
    val canDelete: Boolean get() = canEdit && canCreate
}

/** Why the page could not load: the server's [message], or null for the page's own wording. */
@Immutable
data class AgentLoadError(val message: String?)

/** An action on the page that failed. The screen words each; [message] is the server's, when it sent one. */
@Immutable
sealed interface AgentActionError {
    data class Delete(val message: String?) : AgentActionError
    data class Duplicate(val message: String?) : AgentActionError
    data class Pin(val message: String?) : AgentActionError

    /** The pin list is full ([max] favorites); nothing was sent. */
    data class PinLimit(val max: Int) : AgentActionError
}

sealed interface AgentDetailEvent {
    data object Deleted : AgentDetailEvent
    data class Duplicated(val agentId: String) : AgentDetailEvent
}

class AgentDetailViewModel(
    private val agentRepository: AgentRepository,
    private val serverDataStore: ServerDataStore,
    private val favoritesRepository: FavoritesRepository,
    private val roleRepository: RoleRepository,
    initialAgentId: String? = null,
) : ViewModel() {

    private val agentId: String = checkNotNull(initialAgentId) { "agentId must be provided" }

    private val _uiState = MutableStateFlow(AgentDetailUiState())
    val uiState: StateFlow<AgentDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AgentDetailEvent>()
    val events: SharedFlow<AgentDetailEvent> = _events.asSharedFlow()

    /**
     * Whether [FavoritesRepository.favorites] has been read from the server since this page
     * opened. A pin is a write of the WHOLE list, so one built on the empty not-yet-loaded list
     * would replace every other pin the user has.
     */
    private var favoritesLoaded = false
    private var pinJob: Job? = null
    private var categoriesRequested = false

    init {
        loadAgent()
        observeRole()
        observePins()
        viewModelScope.launch { refreshFavorites() }
        // Saving in the editor pops back to this page rather than opening a fresh copy of it.
        viewModelScope.launch {
            agentRepository.revision.drop(1).collect { fetchAgent(isRefresh = true) }
        }
    }

    fun loadAgent() = fetchAgent(isRefresh = false)

    /** The web link that opens a new chat with this agent. */
    fun chatLink(): String = agentChatLink(serverDataStore.getBaseUrl(), agentId)

    private fun observeRole() {
        viewModelScope.launch {
            roleRepository.userPermissions.collect { role ->
                _uiState.update {
                    it.copy(canCreate = role.hasAccessOrPermissive(PermissionType.AGENTS, Permission.CREATE))
                }
            }
        }
    }

    /** Pins made anywhere (the chat model menu, Settings) show here, since all read one list. */
    private fun observePins() {
        viewModelScope.launch {
            favoritesRepository.favorites.collect { list ->
                _uiState.update { state -> state.copy(isPinned = list.any { it.agentId == agentId }) }
            }
        }
    }

    private suspend fun refreshFavorites(): Boolean = when (val result = favoritesRepository.refresh()) {
        is Result.Success -> {
            favoritesLoaded = true
            true
        }
        is Result.Error -> {
            Logger.d(result.exception) { "Failed to load favorites: ${result.message}" }
            false
        }
        is Result.Loading -> false
    }

    /** A refresh keeps the page on screen: no spinner, and a failure leaves the last good copy. */
    private fun fetchAgent(isRefresh: Boolean) {
        viewModelScope.launch {
            val showsAgent = isRefresh && _uiState.value.agent != null
            if (!showsAgent) _uiState.update { it.copy(isLoading = true, loadError = null) }
            // Try the expanded endpoint first (returns full agent data including
            // description, category, tools, conversation_starters). It requires
            // EDIT permission server-side, so a 403 there means the user may view
            // but not edit this agent. Any other failure is transient/unknown, so
            // stay optimistic rather than hiding Edit from an owner during a flaky
            // request — the editor re-checks permission on entry. Fall back to the
            // standard view-only endpoint for the displayed data.
            // When the agent list already said this user can't edit it, the probe could only 403.
            val listedVerdict = agentRepository.listedEditVerdict(agentId)
            val expanded = if (listedVerdict == false) {
                null
            } else {
                agentRepository.getAgentForEditing(agentId)
            }
            // A skipped probe leaves the verdict to `serverSaysEditable` below, which is false
            // unless the agent's own copy says otherwise.
            val canEdit = expanded == null || expanded !is Result.Error || !expanded.isPermissionDenied()
            val result = when (expanded) {
                is Result.Success -> expanded
                // The VIEW read, which every viewer may make: the agent's own model, Public flag,
                // update time and contacts, with the tapped list row's category and Top pick.
                // Its tools are not the agent's; `isFullRecord` stays false and Tools stay hidden.
                else -> agentRepository.getAgentForViewing(agentId)
            }
            when (result) {
                is Result.Success -> {
                    // `isEditable` only ever NARROWS the probe's verdict: an explicit false hides
                    // Edit, absence leaves the probe in charge. Never the other way round — an
                    // absent field read as permission grows an Edit button that 403s on tap on
                    // every server that predates it.
                    //
                    // Read from the LIST's answer, since neither endpoint loaded above stamps the
                    // field; the agent's own copy still wins should a server ever send it there.
                    val serverSaysEditable =
                        result.data.isEditable ?: listedVerdict
                    val agent = result.data.toDetailDisplayData(serverDataStore.getBaseUrl())
                    _uiState.update {
                        it.copy(
                            agent = agent,
                            isFullRecord = expanded is Result.Success,
                            canEdit = canEdit && serverSaysEditable != false,
                            isLoading = false,
                        )
                    }
                    loadCategoriesFor(agent)
                }
                is Result.Error -> {
                    if (showsAgent) return@launch
                    _uiState.update {
                        it.copy(isLoading = false, canEdit = false, loadError = AgentLoadError(result.message))
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /**
     * Reads the categories once, and only for an agent with a category to label: the route is the
     * only place an admin's own category names its label. A failure leaves the value's own label.
     */
    private fun loadCategoriesFor(agent: AgentDetailDisplayData) {
        val category = agent.categoryValue ?: return
        if (category == GENERAL_CATEGORY || categoriesRequested) return
        categoriesRequested = true
        viewModelScope.launch {
            when (val result = agentRepository.getAgentCategories()) {
                is Result.Success -> _uiState.update { it.copy(categories = result.data) }
                is Result.Error -> categoriesRequested = false
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Pins or unpins this agent in the chat model menu, as the menu's own star does
     * (`FavoritesDelegate.toggleAgent` in feature/chat). The repository publishes the new list
     * at once and rolls back on failure; a tap while a write is in flight is ignored.
     */
    fun togglePin() {
        if (pinJob?.isActive == true) return
        pinJob = viewModelScope.launch {
            if (!favoritesLoaded && !refreshFavorites()) {
                _uiState.update { it.copy(actionError = AgentActionError.Pin(message = null)) }
                return@launch
            }
            val current = favoritesRepository.favorites.value
            val pinned = current.any { it.agentId == agentId }
            val next = if (pinned) {
                current.filterNot { it.agentId == agentId }
            } else {
                if (current.size >= FavoritesLimits.MAX_FAVORITES) {
                    _uiState.update { it.copy(actionError = AgentActionError.PinLimit(FavoritesLimits.MAX_FAVORITES)) }
                    return@launch
                }
                current + UserFavorite(agentId = agentId)
            }
            when (val result = favoritesRepository.setFavorites(next)) {
                is Result.Error -> _uiState.update { it.copy(actionError = AgentActionError.Pin(result.message)) }
                else -> Unit
            }
        }
    }

    fun dismissActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    fun showDeleteConfirmation() {
        if (!_uiState.value.canDelete) return
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteAgent() {
        val state = _uiState.value
        if (state.isDeleting || !state.canDelete) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, showDeleteDialog = false, actionError = null) }
            when (val result = agentRepository.deleteAgent(agentId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isDeleting = false) }
                    _events.emit(AgentDetailEvent.Deleted)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isDeleting = false, actionError = AgentActionError.Delete(result.message))
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun duplicateAgent() {
        val state = _uiState.value
        if (state.isDuplicating || !state.canDuplicate) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDuplicating = true, actionError = null) }
            when (val result = agentRepository.duplicateAgent(agentId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isDuplicating = false) }
                    _events.emit(AgentDetailEvent.Duplicated(result.data.id))
                }
                is Result.Error -> _uiState.update {
                    it.copy(isDuplicating = false, actionError = AgentActionError.Duplicate(result.message))
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    private fun Result.Error.isPermissionDenied(): Boolean =
        (exception as? ApiException)?.statusCode == HTTP_FORBIDDEN

    private companion object {
        const val HTTP_FORBIDDEN = 403

        /** The category every uncategorised agent has; the page doesn't label it. */
        const val GENERAL_CATEGORY = "general"
    }
}

package com.garfiec.librechat.feature.agents.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.skills.SkillViewer
import com.garfiec.librechat.core.data.skills.toSkillViewer
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.PermissionBits
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.model.isSearchablePrincipalQuery
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.canSearchPeople
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import com.garfiec.librechat.core.ui.sharing.defaultAccessRoleId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AgentAclUiState(
    val isLoading: Boolean = false,
    /** The grants have been fetched at least once for this agent; until then nothing is known. */
    val loaded: Boolean = false,
    /** The last grants fetch failed. Shown with a Retry; until a fetch lands the grants are unknown, not empty. */
    val loadFailed: Boolean = false,
    /** The last change failed, with the server's text. A failed grants fetch is [loadFailed]. */
    val error: String? = null,
    /** A change is on its way; the section's controls wait for it, so changes can't land out of order. */
    val isUpdating: Boolean = false,
    val principals: List<Principal> = emptyList(),
    val isPublic: Boolean = false,
    val publicAccessRoleId: String? = null,
    val availableRoles: List<AccessRole> = emptyList(),
    /** The roles fetch failed: nothing can be granted without them. Opening the dialog retries. */
    val rolesFailed: Boolean = false,
    val showGrantDialog: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Principal> = emptyList(),
    val isSearching: Boolean = false,
    /** The last search failed, which is not the same as finding no one. */
    val searchFailed: Boolean = false,
    /**
     * Fail-CLOSED: the role may search people (see `canSearchPeople`); without it the server
     * refuses every search, so the dialog says so instead of offering a field.
     */
    val canSearchPeople: Boolean = false,
    /**
     * Fail-CLOSED: whether the viewer may share THIS agent, as web's AgentFooter gates its share
     * dialog: role AGENTS.SHARE (or ADMIN), and author, ADMIN or the agent's ACL SHARE bit. Both
     * `GET` and `PUT /api/permissions/agent/:id` check the ACL SHARE bit, and the PUT also runs
     * checkShareAccess for the role. Without it the section offers nothing. False until the role,
     * the signed-in user and the agent's bits have all answered.
     */
    val canShare: Boolean = false,
    /**
     * The signed-in user or the agent's bits failed to load, and with them the viewer might be
     * able to share. The section shows this with a Retry rather than hiding itself for good.
     */
    val shareCheckFailed: Boolean = false,
    /** The Retry after [shareCheckFailed] is on its way. */
    val isCheckingShare: Boolean = false,
    /** The agent's author. Their own grant is what keeps the agent theirs, so it isn't offered for removal. */
    val authorId: String? = null,
    /** Fail-CLOSED: the role holds AGENTS.SHARE_PUBLIC, which checkSharePublicAccess needs for `public: true`. */
    val canSharePublic: Boolean = false,
) {
    /** People can be added, and public access turned on, only once the roles are known. */
    val canGrant: Boolean get() = availableRoles.isNotEmpty() && !rolesFailed

    /** [principal] is the author's own grant: removing it would lock them out of their agent. */
    fun isAuthor(principal: Principal): Boolean = principal.isAgentAuthor(authorId)

    /**
     * [principal] can be added from the grant dialog: not the author, and not already listed. A
     * grant is an upsert on the server, so either would silently replace that person's role.
     */
    fun isGrantable(principal: Principal): Boolean {
        val key = principal.aclKey()
        return !isAuthor(principal) && principals.none { it.aclKey() == key }
    }

    /** The role a new grant or the public switch starts at: web's viewer default, never just the first listed. */
    val defaultRoleId: String? get() = defaultAccessRoleId(ResourceType.AGENT, availableRoles)
}

/**
 * Standalone ACL viewmodel for the agent editor's sharing section. Kept
 * separate from [AgentEditorViewModel] so the ACL surface can be toggled in
 * without colliding with the editor's existing state shape. Its loads and its people search
 * follow the skills sharing sheet's SkillAclViewModel.
 */
class AgentAclViewModel(
    private val permissionsRepository: PermissionsRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentAclUiState())
    val uiState: StateFlow<AgentAclUiState> = _uiState.asStateFlow()

    /** The agent's database `_id`: the permissions routes look the ACL up by it, not by `agent_…`. */
    private var currentAgentId: String? = null
    private var searchJob: Job? = null
    private var grantsJob: Job? = null

    // Inputs to [recomputeShare]. Each arrives on its own; nothing is offered until all have answered.
    private var role: UserRolePermissions? = null
    private var roleKnown = false
    private var viewer: SkillViewer? = null
    private var viewerKnown = false
    private var viewerJob: Job? = null
    private var agentBits = 0
    private var bitsKnown = false
    private var bitsFailed = false
    private var bitsJob: Job? = null

    /** A Retry of a failed share check is on its way: the failure stays shown until it answers. */
    private var shareRetryPending = false

    /** Apart from [grantsJob]: a change cancels the grants fetch, and the roles must still arrive. */
    private var rolesJob: Job? = null

    init {
        observeRole()
    }

    private fun observeRole() {
        viewModelScope.launch {
            roleRepository.userPermissions.collect { role ->
                this@AgentAclViewModel.role = role
                roleKnown = true
                val canSearchPeople = role.canSearchPeople()
                if (!canSearchPeople) searchJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    canSearchPeople = canSearchPeople,
                    canSharePublic = role.hasAccessStrictOrDenied(PermissionType.AGENTS, Permission.SHARE_PUBLIC),
                ).let { state ->
                    if (canSearchPeople) {
                        state
                    } else {
                        state.copy(searchQuery = "", searchResults = emptyList(), isSearching = false, searchFailed = false)
                    }
                }
                recomputeShare()
            }
        }
    }

    /**
     * Opens the section on the agent whose database `_id` is [agentDbId], written by [authorId].
     * Fetches what decides whether the viewer may share it; the grants and the roles follow once
     * that allows it, which can be after this call (the role, the user and the bits each arrive on
     * their own). Nothing else is fetched without the share gate: the server would refuse it.
     */
    fun load(agentDbId: String, authorId: String?) {
        if (agentDbId != currentAgentId) {
            agentBits = 0
            bitsKnown = false
            bitsFailed = false
        }
        currentAgentId = agentDbId
        _uiState.value = _uiState.value.copy(authorId = authorId)
        loadViewer()
        loadBits(agentDbId)
        val couldShare = _uiState.value.canShare
        recomputeShare()
        // Already sharable (a reload of the same agent): refresh the grants now.
        if (couldShare && _uiState.value.canShare) loadGrantsAndRoles(agentDbId)
    }

    private fun loadGrantsAndRoles(agentDbId: String) {
        loadGrants(agentDbId)
        if (_uiState.value.availableRoles.isEmpty()) loadRoles()
    }

    /** The signed-in user, for the author and admin branches. Fetched until it succeeds once; a failure grants neither. */
    private fun loadViewer() {
        if (viewer?.userId != null || viewerJob?.isActive == true) return
        viewerKnown = false
        viewerJob = viewModelScope.launch {
            val result = userRepository.getUser()
            if (result is Result.Success) viewer = result.data.toSkillViewer()
            viewerKnown = true
            recomputeShare()
        }
    }

    /**
     * The viewer's ACL bits on the agent. A failure keeps the last known bits and counts as
     * answered, so the gate resolves; [retryShareCheck] fetches them again.
     */
    private fun loadBits(agentDbId: String) {
        bitsJob?.cancel()
        bitsJob = viewModelScope.launch {
            val result = permissionsRepository.getAllEffectivePermissions(ResourceType.AGENT)
            if (result is Result.Success) agentBits = result.data[agentDbId] ?: 0
            bitsFailed = result !is Result.Success
            bitsKnown = true
            recomputeShare()
        }
    }

    /** The section's Retry after [AgentAclUiState.shareCheckFailed]: refetches whichever check failed. */
    fun retryShareCheck() {
        val agentId = currentAgentId ?: return
        if (!_uiState.value.shareCheckFailed || shareRetryPending) return
        shareRetryPending = true
        if (viewer?.userId == null) loadViewer()
        if (bitsFailed) {
            bitsKnown = false
            loadBits(agentId)
        }
        recomputeShare()
    }

    private fun recomputeShare() {
        val state = _uiState.value
        val agentId = currentAgentId
        val canShare = roleKnown && viewerKnown && bitsKnown && agentId != null &&
            resolveAgentCanShare(role = role, viewer = viewer, authorId = state.authorId, agentBits = agentBits)
        // Only a failure that could be hiding a yes: without the user, they may be the author or an
        // admin; without the bits, they may hold SHARE on it.
        val viewerFailed = viewerKnown && viewer?.userId == null
        val couldShareWithBits = resolveAgentCanShare(role, viewer, state.authorId, PermissionBits.SHARE)
        val answered = roleKnown && viewerKnown && bitsKnown
        if (answered) shareRetryPending = false
        val shareCheckFailed = if (shareRetryPending) {
            state.shareCheckFailed
        } else {
            !canShare && answered && agentId != null && (viewerFailed || (bitsFailed && couldShareWithBits))
        }
        _uiState.value = state.copy(
            canShare = canShare,
            shareCheckFailed = shareCheckFailed,
            isCheckingShare = shareRetryPending,
            showGrantDialog = state.showGrantDialog && canShare,
        )
        if (canShare && !state.canShare) loadGrantsAndRoles(agentId)
    }

    /**
     * Skipped while a change is in flight: the change reads the grants back once it lands, and a
     * GET started before then could land after it with the state from before.
     */
    private fun loadGrants(agentId: String, keepError: Boolean = false) {
        if (_uiState.value.isUpdating) return
        grantsJob?.cancel()
        grantsJob = viewModelScope.launch {
            // A failed change's message stays on screen over the reload it started.
            val shownError = _uiState.value.error.takeIf { keepError }
            _uiState.value = _uiState.value.copy(isLoading = true, loadFailed = false, error = shownError)
            applyGrants(permissionsRepository.getResourcePermissions(ResourceType.AGENT, agentId))
        }
    }

    private fun applyGrants(result: Result<ResourcePermissions>) {
        _uiState.value = when (result) {
            is Result.Success -> _uiState.value.copy(
                isLoading = false,
                loaded = true,
                loadFailed = false,
                principals = result.data.principals,
                isPublic = result.data.public,
                publicAccessRoleId = result.data.publicAccessRoleId,
            )
            is Result.Error -> {
                Logger.d(result.exception) { "getResourcePermissions failed: ${result.message}" }
                _uiState.value.copy(isLoading = false, loadFailed = true)
            }
            is Result.Loading -> _uiState.value.copy(isLoading = false)
        }
    }

    /** The section's Retry: refetches the grants, and the roles if they failed or never arrived. */
    fun retryLoad() {
        val agentId = currentAgentId ?: return
        val state = _uiState.value
        if (!state.canShare) return
        if (state.loadFailed || !state.loaded) loadGrants(agentId)
        retryRoles()
    }

    private fun loadRoles() {
        if (rolesJob?.isActive == true) return
        rolesJob = viewModelScope.launch {
            when (val result = permissionsRepository.getResourceRoles(ResourceType.AGENT)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(availableRoles = result.data, rolesFailed = false)
                is Result.Error -> {
                    Logger.d(result.exception) { "getResourceRoles failed: ${result.message}" }
                    _uiState.value = _uiState.value.copy(rolesFailed = true)
                }
                is Result.Loading -> Unit
            }
        }
    }

    /** Opens the dialog on a fresh search (a search still on its way is dropped), refetching failed roles. */
    fun openGrantDialog() {
        if (!_uiState.value.canShare) return
        clearSearch(showGrantDialog = true)
        retryRoles()
    }

    /** Refetches the roles if they failed or never arrived; the grant dialog's Retry. */
    fun retryRoles() {
        val state = _uiState.value
        if (state.availableRoles.isEmpty() || state.rolesFailed) loadRoles()
    }

    fun dismissGrantDialog() {
        clearSearch(showGrantDialog = false)
    }

    private fun clearSearch(showGrantDialog: Boolean) {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showGrantDialog = showGrantDialog,
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            searchFailed = false,
        )
    }

    fun onSearchQueryChanged(query: String) {
        if (!_uiState.value.canSearchPeople) return
        _uiState.value = _uiState.value.copy(searchQuery = query, searchFailed = false)
        searchJob?.cancel()
        // The server refuses a trimmed query under its minimum with a 400.
        if (!isSearchablePrincipalQuery(query)) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        // Pending from the first keystroke: the last query's results would otherwise stay listed
        // (and grantable) under this one through the debounce.
        _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = true)
        val trimmed = query.trim()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(trimmed)
        }
    }

    /** Re-runs the current search without the debounce wait. */
    fun retrySearch() {
        val state = _uiState.value
        if (!state.canSearchPeople || !isSearchablePrincipalQuery(state.searchQuery)) return
        searchJob?.cancel()
        _uiState.value = state.copy(searchResults = emptyList(), isSearching = true, searchFailed = false)
        val trimmed = state.searchQuery.trim()
        searchJob = viewModelScope.launch { runSearch(trimmed) }
    }

    private suspend fun runSearch(trimmed: String) {
        when (val r = permissionsRepository.searchPrincipals(trimmed)) {
            is Result.Success -> _uiState.value = _uiState.value.copy(
                searchResults = r.data,
                isSearching = false,
                searchFailed = false,
            )
            is Result.Error -> {
                Logger.d(r.exception) { "searchPrincipals failed: ${r.message}" }
                _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false, searchFailed = true)
            }
            is Result.Loading -> Unit
        }
    }

    /**
     * Grants and revokes leave `public` out (null), so the server leaves public access as it is.
     * Sending the cached flag would put `public: false` on the wire for a stale or non-public
     * state, which the server reads as "revoke public access".
     */
    fun grant(principal: Principal, accessRoleId: String) {
        val agentId = currentAgentId ?: return
        // Granting someone already listed would replace their role, the author's owner grant included.
        if (!_uiState.value.isGrantable(principal)) return
        val granted = principal.copy(accessRoleId = accessRoleId)
        submit(agentId, UpdateResourcePermissionsRequest(updated = listOf(granted)), dismissDialogOnSuccess = true)
    }

    /** Refused for the author's own grant: access checks don't fall back to `agent.author`, so losing it locks them out. */
    fun revoke(principal: Principal) {
        val agentId = currentAgentId ?: return
        if (_uiState.value.isAuthor(principal)) return
        submit(agentId, UpdateResourcePermissionsRequest(removed = listOf(principal)), dismissDialogOnSuccess = false)
    }

    /**
     * The only call that sends `public`: `false` is what revokes public access on the server.
     * Turning it on needs a role ([accessRoleId], else the current one, else the default) and
     * AGENTS.SHARE_PUBLIC; with no role known yet it is refused rather than sent without one.
     */
    fun setPublic(enabled: Boolean, accessRoleId: String?) {
        val agentId = currentAgentId ?: return
        val state = _uiState.value
        if (enabled && !state.canSharePublic) return
        val roleId = if (enabled) {
            accessRoleId ?: state.publicAccessRoleId ?: state.defaultRoleId
        } else {
            accessRoleId
        }
        if (enabled && roleId == null) return
        submit(agentId, UpdateResourcePermissionsRequest(public = enabled, publicAccessRoleId = roleId), dismissDialogOnSuccess = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * One change at a time (mirrors the skills sharing sheet). Sends [body], then reads the grants
     * back with a GET: the PUT's answer is `{ message, results }` and holds only what that call
     * granted, so copying it would blank the list and the public state. A grants fetch already on
     * its way is cancelled, so its older state can't land over the change; a change that then
     * fails fetches them again, since nothing else would. The change has landed once the PUT
     * succeeds, so a failed read-back applies it to the list here instead of reporting it as failed.
     */
    private fun submit(agentId: String, body: UpdateResourcePermissionsRequest, dismissDialogOnSuccess: Boolean) {
        if (_uiState.value.isUpdating || !_uiState.value.canShare) return
        val interruptedLoad = grantsJob?.isActive == true
        grantsJob?.cancel()
        _uiState.value = _uiState.value.copy(isUpdating = true, isLoading = false, error = null)
        viewModelScope.launch {
            when (val r = permissionsRepository.updateResourcePermissions(ResourceType.AGENT, agentId, body)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showGrantDialog = if (dismissDialogOnSuccess) false else _uiState.value.showGrantDialog,
                    )
                    _uiState.value = when (val grants = permissionsRepository.getResourcePermissions(ResourceType.AGENT, agentId)) {
                        is Result.Success -> _uiState.value.copy(
                            loaded = true,
                            loadFailed = false,
                            principals = grants.data.principals,
                            isPublic = grants.data.public,
                            publicAccessRoleId = grants.data.publicAccessRoleId,
                        )
                        is Result.Error -> {
                            Logger.d(grants.exception) { "getResourcePermissions after a change failed: ${grants.message}" }
                            _uiState.value.withApplied(body)
                        }
                        is Result.Loading -> _uiState.value
                    }.copy(isUpdating = false)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(isUpdating = false, error = r.message)
                    if (interruptedLoad || !_uiState.value.loaded) loadGrants(agentId, keepError = true)
                }
                is Result.Loading -> _uiState.value = _uiState.value.copy(isUpdating = false)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/**
 * Whether the viewer may share the agent, as web's AgentFooter decides it at e8f3be0:
 * `(author === user.id || isAdmin || hasPermission(SHARE)) && (hasAccessToShareAgents || isAdmin)`.
 * [viewer] null (the user failed to load) grants neither the author nor the admin branch.
 */
internal fun resolveAgentCanShare(
    role: UserRolePermissions?,
    viewer: SkillViewer?,
    authorId: String?,
    agentBits: Int,
): Boolean {
    val isAdmin = viewer?.isAdmin == true
    val isAuthor = !authorId.isNullOrBlank() && authorId == viewer?.userId
    val roleAllows = isAdmin || role.hasAccessStrictOrDenied(PermissionType.AGENTS, Permission.SHARE)
    return roleAllows && (isAuthor || isAdmin || PermissionBits.has(agentBits, PermissionBits.SHARE))
}

/** Whether [this] grant is the agent author's own (as the skills sheet's `Principal.isAuthor`). */
internal fun Principal.isAgentAuthor(authorId: String?): Boolean =
    !authorId.isNullOrBlank() && type == PrincipalType.USER && id == authorId

/** Identifies a principal across the grants list and search results, as the skills sheet's `principalKey`. */
internal fun Principal.aclKey(): String = "$type:${id ?: idOnTheSource ?: name.orEmpty()}"

/** The search results the grant dialog offers: everyone not already granted, each once. */
internal fun AgentAclUiState.newSearchResults(): List<Principal> =
    searchResults.filter(::isGrantable).distinctBy { it.aclKey() }

/** [body] applied to the grants shown, for a change the server accepted but couldn't be read back. */
internal fun AgentAclUiState.withApplied(body: UpdateResourcePermissionsRequest): AgentAclUiState {
    fun Principal.key() = aclKey()
    val removed = body.removed.mapTo(HashSet()) { it.key() }
    val updated = body.updated.associateBy { it.key() }
    // An updated principal keeps its place in the list; a new one goes at the end.
    val updatedInPlace = principals.mapNotNull { p -> updated[p.key()] ?: p.takeUnless { p.key() in removed } }
    val shown = principals.mapTo(HashSet()) { it.key() }
    val added = body.updated.filter { it.key() !in shown }
    return copy(
        principals = updatedInPlace + added,
        isPublic = body.public ?: isPublic,
        publicAccessRoleId = if (body.public != null) body.publicAccessRoleId else publicAccessRoleId,
    )
}

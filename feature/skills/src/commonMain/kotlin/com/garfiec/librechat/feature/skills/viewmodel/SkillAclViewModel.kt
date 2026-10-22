package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PermissionsRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.ResourcePermissions
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
import com.garfiec.librechat.core.model.isSearchablePrincipalQuery
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.canSearchPeople
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import com.garfiec.librechat.feature.skills.defaultSkillAccessRoleId
import com.garfiec.librechat.feature.skills.principalKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class SkillAclUiState(
    /** The grants are being fetched. */
    val isLoading: Boolean = false,
    /** The grants have been fetched at least once for this skill; until then nothing is known. */
    val loaded: Boolean = false,
    /** The last grants fetch failed. Shown in the sheet with a retry. */
    val loadFailed: Boolean = false,
    /** The last change failed; [errorMessage] is the server's text when there was one. Shown in the sheet. */
    val updateFailed: Boolean = false,
    val errorMessage: String? = null,
    /** A change is on its way; every control in the sheet waits for it. */
    val isUpdating: Boolean = false,
    val principals: List<Principal> = emptyList(),
    val isPublic: Boolean = false,
    val publicAccessRoleId: String? = null,
    val availableRoles: List<AccessRole> = emptyList(),
    /**
     * The roles fetch failed. Nothing can be granted or re-roled without them, so adding is
     * disabled and the sheet offers a retry.
     */
    val rolesFailed: Boolean = false,
    val showSheet: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Principal> = emptyList(),
    val isSearching: Boolean = false,
    /** The last search failed, which is not the same as finding no one. */
    val searchFailed: Boolean = false,
    /**
     * Fail-CLOSED: whether the viewer may change this skill's sharing. Set from the detail page's
     * resolved access (`SkillAccess.canShare`: not a deployment skill, role SKILLS.SHARE, and owner,
     * admin or the SHARE bit). False until that resolves.
     */
    val canShare: Boolean = false,
    /** Fail-CLOSED: the public-share toggle is offered only with SKILLS.SHARE_PUBLIC. */
    val canSharePublic: Boolean = false,
    /**
     * Fail-CLOSED: the people search is offered only when the role may look anyone up (ADMIN, or
     * PEOPLE_PICKER VIEW_USERS, VIEW_GROUPS or VIEW_ROLES); without that, the server refuses every
     * search. Grants already on the skill keep their role menus: changing them needs no search.
     */
    val canSearchPeople: Boolean = false,
) {
    /** People can be added (and roles changed) only once the roles are known. */
    val canGrant: Boolean get() = availableRoles.isNotEmpty() && !rolesFailed

    /** Removing a grant sends no role, so it waits only for a change in flight, not for the roles. */
    val canRevoke: Boolean get() = !isUpdating

    /** The search found people, and every one of them is already on the list: not "No matches". */
    internal val allResultsAlreadyShared: Boolean get() = searchResults.isNotEmpty() && newSearchResults().isEmpty()

    /** The role a new grant or the public switch starts at: web's viewer default. */
    val defaultRoleId: String? get() = defaultSkillAccessRoleId(availableRoles)

    /** The typed query is long enough for the server to search; shorter ones are never sent. */
    val isQuerySearchable: Boolean get() = isSearchablePrincipalQuery(searchQuery)
}

/**
 * Skills-local ACL sharing viewmodel. Mirrors the agent ACL flow but
 * parameterized to [ResourceType.SKILL], reusing the SAME shared
 * [PermissionsRepository] / permissions API / [Principal] / [AccessRole]. No
 * dependency on feature/agents (features depend on :core:* only).
 */
class SkillAclViewModel(
    private val permissionsRepository: PermissionsRepository,
    private val roleRepository: RoleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillAclUiState())
    val uiState: StateFlow<SkillAclUiState> = _uiState.asStateFlow()

    private var currentSkillId: String? = null
    private var searchJob: Job? = null
    private var rolesJob: Job? = null
    private var grantsJob: Job? = null

    init {
        observeSharePermissions()
    }

    private fun observeSharePermissions() {
        viewModelScope.launch {
            roleRepository.userPermissions.collect { role ->
                val canSearchPeople = role.canSearchPeople()
                if (!canSearchPeople) searchJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    canSharePublic = role.hasAccessStrictOrDenied(PermissionType.SKILLS, Permission.SHARE_PUBLIC),
                    canSearchPeople = canSearchPeople,
                ).let { state ->
                    if (canSearchPeople) {
                        state
                    } else {
                        state.copy(searchQuery = "", searchResults = emptyList(), isSearching = false, searchFailed = false)
                    }
                }
            }
        }
    }

    /**
     * Applies the detail page's share gate. The grants load the moment it turns true, which can be
     * after the screen's first resume (the role, the bits and the user each arrive on their own).
     */
    fun setCanShare(skillId: String, canShare: Boolean) {
        val wasSharable = _uiState.value.canShare && currentSkillId == skillId
        _uiState.value = _uiState.value.copy(canShare = canShare, showSheet = _uiState.value.showSheet && canShare)
        if (canShare && !wasSharable) load(skillId)
    }

    /** Fetches the grants and, unless already known, the roles. */
    fun load(skillId: String) {
        currentSkillId = skillId
        loadGrants(skillId)
        if (_uiState.value.availableRoles.isEmpty()) loadRoles()
    }

    /**
     * Skipped while a change is in flight: [submit] reads the grants back once it lands, and a GET
     * started before then (a resume, a retry) could land after it with the state from before.
     */
    private fun loadGrants(skillId: String) {
        if (_uiState.value.isUpdating) return
        grantsJob?.cancel()
        grantsJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            applyGrants(permissionsRepository.getResourcePermissions(ResourceType.SKILL, skillId))
        }
    }

    private fun applyGrants(result: Result<ResourcePermissions>) {
        when (result) {
            is Result.Success -> _uiState.value = _uiState.value.copy(
                isLoading = false,
                loaded = true,
                loadFailed = false,
                principals = result.data.principals,
                isPublic = result.data.public,
                publicAccessRoleId = result.data.publicAccessRoleId,
            )
            is Result.Error -> {
                Logger.d(result.exception) { "getResourcePermissions failed: ${result.message}" }
                _uiState.value = _uiState.value.copy(isLoading = false, loadFailed = true)
            }
            is Result.Loading -> Unit
        }
    }

    private fun loadRoles() {
        if (rolesJob?.isActive == true) return
        rolesJob = viewModelScope.launch {
            when (val result = permissionsRepository.getResourceRoles(ResourceType.SKILL)) {
                is Result.Success -> _uiState.value = _uiState.value.copy(
                    availableRoles = result.data,
                    rolesFailed = false,
                )
                is Result.Error -> {
                    Logger.d(result.exception) { "getResourceRoles failed: ${result.message}" }
                    _uiState.value = _uiState.value.copy(rolesFailed = true)
                }
                is Result.Loading -> Unit
            }
        }
    }

    /** Retries whatever failed to load: the grants, the roles, or both. */
    fun retryLoad() {
        val skillId = currentSkillId ?: return
        val state = _uiState.value
        if (state.loadFailed || !state.loaded) loadGrants(skillId)
        if (state.rolesFailed || state.availableRoles.isEmpty()) loadRoles()
    }

    /** Opens the sharing sheet on a fresh search, refetching anything that failed before. */
    fun openSheet() {
        if (!_uiState.value.canShare) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showSheet = true,
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            searchFailed = false,
            updateFailed = false,
            errorMessage = null,
        )
        retryLoad()
    }

    fun closeSheet() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            showSheet = false,
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
        // The server refuses a trimmed query under its minimum with a 400; sending one would show
        // a search error whose Retry can never succeed.
        if (!isSearchablePrincipalQuery(query)) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        // Pending from the first keystroke: the last query's results would otherwise stay listed
        // (and addable) under this one through the debounce, or read as "No one found".
        _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = true)
        val trimmed = query.trim()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(trimmed)
        }
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
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false,
                    searchFailed = true,
                )
            }
            is Result.Loading -> Unit
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

    /** Shares with [principal] at the default (viewer) role, then clears the search. */
    fun add(principal: Principal) {
        val roleId = _uiState.value.takeIf { it.canGrant }?.defaultRoleId ?: return
        grant(principal, roleId, clearSearch = true)
    }

    /** Changes an existing grant's role; the server replaces the principal's role in place. */
    fun changeRole(principal: Principal, accessRoleId: String) {
        if (!_uiState.value.canGrant || principal.accessRoleId == accessRoleId) return
        grant(principal, accessRoleId, clearSearch = false)
    }

    /**
     * Grants and revokes leave `public` out of the body, so the server leaves public access as it
     * is and skips its SHARE_PUBLIC check, which would refuse a role that can share but not publicly.
     */
    fun grant(principal: Principal, accessRoleId: String, clearSearch: Boolean = true) {
        val skillId = currentSkillId ?: return
        submit(
            skillId,
            UpdateResourcePermissionsRequest(updated = listOf(principal.copy(accessRoleId = accessRoleId))),
            clearSearchOnSuccess = clearSearch,
        )
    }

    fun revoke(principal: Principal) {
        val skillId = currentSkillId ?: return
        submit(
            skillId,
            UpdateResourcePermissionsRequest(removed = listOf(principal)),
            clearSearchOnSuccess = false,
        )
    }

    /** Turns public access on at [accessRoleId] (else the current or default role), or off. */
    fun setPublic(enabled: Boolean, accessRoleId: String? = null) {
        val skillId = currentSkillId ?: return
        val state = _uiState.value
        val roleId = if (enabled) accessRoleId ?: state.publicAccessRoleId ?: state.defaultRoleId else null
        if (enabled && roleId == null) return
        // Sent either way: `false` is what revokes public access, so it has to reach the wire.
        submit(
            skillId,
            UpdateResourcePermissionsRequest(public = enabled, publicAccessRoleId = roleId),
            clearSearchOnSuccess = false,
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(updateFailed = false, errorMessage = null)
    }

    /**
     * One change at a time. On success the grants are read back with a GET before the controls
     * free up: the PUT's own answer holds only what that call granted, wrapped where the model
     * doesn't read it. A grants fetch already on its way is cancelled, so its older state can't
     * land over the change; a change that then fails fetches them again, since nothing else would.
     * The change has landed once the PUT succeeds, so a failed read-back applies it to the list here
     * instead of reporting the sharing as failed to load (as AgentAclViewModel does).
     */
    private fun submit(skillId: String, body: UpdateResourcePermissionsRequest, clearSearchOnSuccess: Boolean) {
        if (_uiState.value.isUpdating) return
        val interruptedLoad = grantsJob?.isActive == true
        grantsJob?.cancel()
        _uiState.value = _uiState.value.copy(isUpdating = true, isLoading = false, updateFailed = false, errorMessage = null)
        viewModelScope.launch {
            when (val r = permissionsRepository.updateResourcePermissions(ResourceType.SKILL, skillId, body)) {
                is Result.Success -> {
                    if (clearSearchOnSuccess) {
                        searchJob?.cancel()
                        _uiState.value = _uiState.value.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
                    }
                    when (val grants = permissionsRepository.getResourcePermissions(ResourceType.SKILL, skillId)) {
                        is Result.Error -> {
                            Logger.d(grants.exception) { "getResourcePermissions after a change failed: ${grants.message}" }
                            _uiState.value = _uiState.value.withApplied(body)
                        }
                        else -> applyGrants(grants)
                    }
                    _uiState.value = _uiState.value.copy(isUpdating = false)
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isUpdating = false,
                        updateFailed = true,
                        errorMessage = r.message,
                    )
                    if (interruptedLoad || !_uiState.value.loaded) loadGrants(skillId)
                }
                is Result.Loading -> _uiState.value = _uiState.value.copy(isUpdating = false)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

/** [body] applied to the grants shown, for a change the server accepted but couldn't be read back. */
internal fun SkillAclUiState.withApplied(body: UpdateResourcePermissionsRequest): SkillAclUiState {
    val removed = body.removed.mapTo(HashSet()) { it.principalKey() }
    val updated = body.updated.associateBy { it.principalKey() }
    // An updated principal keeps its place in the list; a new one goes at the end.
    val kept = principals.mapNotNull { p -> updated[p.principalKey()] ?: p.takeUnless { p.principalKey() in removed } }
    val shown = principals.mapTo(HashSet()) { it.principalKey() }
    val added = body.updated.filter { it.principalKey() !in shown }
    return copy(
        principals = kept + added,
        isPublic = body.public ?: isPublic,
        publicAccessRoleId = if (body.public != null) body.publicAccessRoleId else publicAccessRoleId,
    )
}

/** Search results that aren't already on the list: each person or group appears once. */
internal fun SkillAclUiState.newSearchResults(): List<Principal> {
    val granted = principals.mapTo(HashSet()) { it.principalKey() }
    return searchResults.filter { it.principalKey() !in granted }
}

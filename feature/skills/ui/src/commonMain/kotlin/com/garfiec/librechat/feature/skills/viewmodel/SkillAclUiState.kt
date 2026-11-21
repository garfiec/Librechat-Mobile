package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.acl.PermissionUpdateFailure
import com.garfiec.librechat.core.model.isSearchablePrincipalQuery
import com.garfiec.librechat.feature.skills.defaultSkillAccessRoleId
import com.garfiec.librechat.feature.skills.principalKey

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
    /** The last change was refused for a reason that passes; worded by the sheet, over [errorMessage]. */
    val updateFailure: PermissionUpdateFailure? = null,
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
    val allResultsAlreadyShared: Boolean get() = searchResults.isNotEmpty() && newSearchResults().isEmpty()

    /** The role a new grant or the public switch starts at: web's viewer default. */
    val defaultRoleId: String? get() = defaultSkillAccessRoleId(availableRoles)

    /** The typed query is long enough for the server to search; shorter ones are never sent. */
    val isQuerySearchable: Boolean get() = isSearchablePrincipalQuery(searchQuery)
}

/** Search results that aren't already on the list: each person or group appears once. */
fun SkillAclUiState.newSearchResults(): List<Principal> {
    val granted = principals.mapTo(HashSet()) { it.principalKey() }
    return searchResults.filter { it.principalKey() !in granted }
}

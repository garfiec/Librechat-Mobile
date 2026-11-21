package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.model.acl.PermissionUpdateFailure
import com.garfiec.librechat.core.ui.sharing.defaultAccessRoleId

data class AgentAclUiState(
    val isLoading: Boolean = false,
    /** The grants have been fetched at least once for this agent; until then nothing is known. */
    val loaded: Boolean = false,
    /** The last grants fetch failed. Shown with a Retry; until a fetch lands the grants are unknown, not empty. */
    val loadFailed: Boolean = false,
    /** The last change failed, with the server's text. A failed grants fetch is [loadFailed]. */
    val error: String? = null,
    /** The last change was refused for a reason that passes; worded by the sheet, over [error]. */
    val updateFailure: PermissionUpdateFailure? = null,
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

/** Whether [this] grant is the agent author's own (as the skills sheet's `Principal.isAuthor`). */
fun Principal.isAgentAuthor(authorId: String?): Boolean =
    !authorId.isNullOrBlank() && type == PrincipalType.USER && id == authorId

/** Identifies a principal across the grants list and search results, as the skills sheet's `principalKey`. */
fun Principal.aclKey(): String = "$type:${id ?: idOnTheSource ?: name.orEmpty()}"

/** The search results the grant dialog offers: everyone not already granted, each once. */
fun AgentAclUiState.newSearchResults(): List<Principal> =
    searchResults.filter(::isGrantable).distinctBy { it.aclKey() }

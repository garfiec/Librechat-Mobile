package com.garfiec.librechat.core.model.acl

import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal

/**
 * Who can open one resource, as the access editor shows it. The `:core:data` controller produces
 * it from `/api/permissions` and the user's role; the `:core:ui` section renders it and decides
 * nothing beyond which controls a state shows.
 */
sealed interface AclState {

    data object Closed : AclState

    data object Loading : AclState

    data class Failed(val error: AclError) : AclState

    /**
     * The resource's grants.
     *
     * @param entries the people, groups and roles with access, the owner first and locked.
     * @param isPublic the resource is open to everyone the server's policy allows.
     * @param canSharePublic the user's role may change public access; false hides the switch.
     * @param canSearch the user's role may search people or groups to add (the people picker);
     *   false hides the search.
     * @param roles the roles a new grant may take, never the owner's. A picker shows only when
     *   there is a choice.
     * @param busy a change is in flight; every control holds until it lands.
     * @param error why the last change failed, shown until the next one.
     */
    data class Ready(
        val entries: List<AclEntry>,
        val isPublic: Boolean,
        val canSharePublic: Boolean,
        val canSearch: Boolean,
        val roles: List<AccessRole>,
        val grantRoleId: String?,
        val search: AclSearch = AclSearch(),
        val busy: Boolean = false,
        val error: AclError? = null,
    ) : AclState
}

/** One grant. A [locked] entry is the owner's: shown, never removable or changeable. */
data class AclEntry(
    val principal: Principal,
    val locked: Boolean,
)

/**
 * The people search. [results] leaves out whoever already has access.
 *
 * @param failed the last search request failed (shown as such, not as "no matches").
 */
data class AclSearch(
    val query: String = "",
    val results: List<Principal> = emptyList(),
    val searching: Boolean = false,
    val failed: Boolean = false,
) {
    /** Long enough to send; a shorter query shows nothing rather than "no matches". */
    val isSendable: Boolean get() = isSendable(query)

    companion object {
        /** Upstream createPrincipalSearch answers 400 below this trimmed length (scripts/mirrors.json). */
        const val MIN_QUERY_LENGTH = 2

        fun isSendable(query: String): Boolean = query.trim().length >= MIN_QUERY_LENGTH
    }
}

/** Why reading or changing access failed, as the editor words it. */
enum class AclError {
    NETWORK,
    FORBIDDEN,
    NOT_FOUND,

    /** The server refuses any change to the owner's own access. */
    OWNER_PROTECTED,
    GENERIC,
}

package com.garfiec.librechat.feature.skills

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.ui.sharing.accessRoleLabel
import com.garfiec.librechat.core.ui.sharing.defaultAccessRoleId

/** A skill role's display text; see [accessRoleLabel]. */
@Composable
internal fun skillRoleLabel(accessRoleId: String, roles: List<AccessRole>): String =
    accessRoleLabel(ResourceType.SKILL, accessRoleId, roles)

/** The role a new skill grant or public access starts at; see [defaultAccessRoleId]. */
internal fun defaultSkillAccessRoleId(roles: List<AccessRole>): String? = defaultAccessRoleId(ResourceType.SKILL, roles)

/** Identity of a principal across the grants list and search results. */
internal fun Principal.principalKey(): String = "$type:${id ?: idOnTheSource ?: name.orEmpty()}"

/** What the detail page's sharing row says, before it becomes text. */
internal sealed interface ShareSummary {
    /** The grants haven't loaded (or failed to), so nothing true can be said yet. */
    data object Unknown : ShareSummary

    data object Private : ShareSummary

    /** [count] people or groups, and whether anyone on the server can use it too. */
    data class Shared(val count: Int, val isPublic: Boolean) : ShareSummary
}

/**
 * The grants list includes the author's own owner entry (`getResourcePermissions` returns every ACL
 * entry), so the author is not counted: a skill only its author holds is private.
 */
internal fun shareSummary(
    loaded: Boolean,
    principals: List<Principal>,
    isPublic: Boolean,
    authorId: String?,
): ShareSummary {
    if (!loaded) return ShareSummary.Unknown
    val others = principals.count { !it.isAuthor(authorId) }
    return if (others == 0 && !isPublic) ShareSummary.Private else ShareSummary.Shared(others, isPublic)
}

/** Whether [this] grant is the skill author's own. */
internal fun Principal.isAuthor(authorId: String?): Boolean =
    authorId != null && type == PrincipalType.USER && id == authorId

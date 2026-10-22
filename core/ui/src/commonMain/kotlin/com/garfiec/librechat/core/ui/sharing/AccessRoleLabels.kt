package com.garfiec.librechat.core.ui.sharing

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.AccessRole
import com.garfiec.librechat.core.model.ResourceType
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.acl_role_editor
import com.garfiec.librechat.core.ui.resources.acl_role_owner
import com.garfiec.librechat.core.ui.resources.acl_role_viewer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The agent and skill roles upstream seeds (`packages/data-schemas/src/methods/accessRole.ts`
 * `seedDefaultRoles`), by the web localization key `GET /api/permissions/{type}/roles` sends as each
 * role's `name`. The key names a string the web client carries, not text to show, so the app
 * carries its own. MCP servers and remote agents are seeded under keys of their own and aren't
 * covered.
 */
internal val BUILT_IN_ACCESS_ROLE_LABELS: Map<String, StringResource> = mapOf(
    "com_ui_role_viewer" to Res.string.acl_role_viewer,
    "com_ui_role_editor" to Res.string.acl_role_editor,
    "com_ui_role_owner" to Res.string.acl_role_owner,
)

/** The resource types whose seeded roles carry the `com_ui_role_*` names above. */
private val BUILT_IN_ROLE_RESOURCE_TYPES = setOf(ResourceType.AGENT, ResourceType.SKILL)

/** Upstream `AccessRoleIds`: `agent_viewer`, `skill_owner`, and so on. */
private fun builtInRoleId(resourceType: String, suffix: String) = "${resourceType}_$suffix"

/** The same roles by id, for a grant whose role isn't in the fetched list. */
private fun builtInRoleLabelById(resourceType: String, accessRoleId: String): StringResource? {
    if (resourceType !in BUILT_IN_ROLE_RESOURCE_TYPES) return null
    return when (accessRoleId) {
        builtInRoleId(resourceType, "viewer") -> Res.string.acl_role_viewer
        builtInRoleId(resourceType, "editor") -> Res.string.acl_role_editor
        builtInRoleId(resourceType, "owner") -> Res.string.acl_role_owner
        else -> null
    }
}

/**
 * Web's default for a new grant and for public access (`RESOURCE_CONFIGS[type].defaultViewerRoleId`
 * in `client/src/utils/resources.ts`): the type's viewer role.
 */
fun defaultViewerRoleId(resourceType: String): String = builtInRoleId(resourceType, "viewer")

/**
 * The role a new grant starts at: the viewer role when the server offers it, else the role with
 * the fewest permission bits, so an unfamiliar server never defaults anyone to owner. The server
 * returns the roles unsorted, so the list's order means nothing.
 */
fun defaultAccessRoleId(resourceType: String, roles: List<AccessRole>): String? {
    val viewer = defaultViewerRoleId(resourceType)
    return roles.firstOrNull { it.accessRoleId == viewer }?.accessRoleId
        ?: roles.minByOrNull { it.permissionBits ?: Int.MAX_VALUE }?.accessRoleId
}

private const val WEB_LOCALIZATION_KEY_PREFIX = "com_"

/** Where a role's display text comes from. */
sealed interface AccessRoleLabel {
    /** A name the server sent as text (not a web localization key). */
    data class Server(val text: String) : AccessRoleLabel

    /** One of the built-in roles, translated by the app. */
    data class BuiltIn(val resource: StringResource) : AccessRoleLabel

    /** Unknown to both: the role id itself. */
    data class Id(val id: String) : AccessRoleLabel
}

/** The label for [accessRoleId]: the server's own text, else the built-in translation, else the id. */
fun resolveAccessRoleLabel(resourceType: String, accessRoleId: String, roles: List<AccessRole>): AccessRoleLabel {
    val name = roles.firstOrNull { it.accessRoleId == accessRoleId }?.name?.takeIf { it.isNotBlank() }
    if (name != null && !name.startsWith(WEB_LOCALIZATION_KEY_PREFIX)) return AccessRoleLabel.Server(name)
    name?.let { BUILT_IN_ACCESS_ROLE_LABELS[it] }?.let { return AccessRoleLabel.BuiltIn(it) }
    builtInRoleLabelById(resourceType, accessRoleId)?.let { return AccessRoleLabel.BuiltIn(it) }
    return AccessRoleLabel.Id(accessRoleId)
}

/** What to show for [accessRoleId] in a sharing UI: never a raw web key, and an id only as the last resort. */
@Composable
fun accessRoleLabel(resourceType: String, accessRoleId: String, roles: List<AccessRole>): String =
    when (val label = resolveAccessRoleLabel(resourceType, accessRoleId, roles)) {
        is AccessRoleLabel.Server -> label.text
        is AccessRoleLabel.BuiltIn -> stringResource(label.resource)
        is AccessRoleLabel.Id -> label.id
    }

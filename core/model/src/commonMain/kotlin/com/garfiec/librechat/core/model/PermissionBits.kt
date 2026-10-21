package com.garfiec.librechat.core.model

/**
 * Upstream `PrincipalType` enum (accessPermissions.ts:16-21). Wire values are the
 * lowercase strings.
 */
object PrincipalType {
    const val USER = "user"
    const val GROUP = "group"
    const val PUBLIC = "public"
    const val ROLE = "role"
}

/**
 * Resource types the permissions API accepts (accessPermissions.ts:45-50). Used in
 * the URL path: `/api/permissions/{resourceType}/{resourceId}`.
 */
object ResourceType {
    const val AGENT = "agent"
    const val PROMPT_GROUP = "promptGroup"
    const val MCP_SERVER = "mcpServer"
    const val REMOTE_AGENT = "remoteAgent"
    const val SKILL = "skill"
    const val SHARED_LINK = "sharedLink"
}

/**
 * Upstream `AccessRoleIds` (accessPermissions.ts) the client names: the roles a shared link's ACL
 * grants. Mirrored by hand; see `scripts/mirrors.json` `shared-link-access-roles`.
 */
object AccessRoleIds {
    /** Opens the link. The only role a shared link offers to people and to the public. */
    const val SHARED_LINK_VIEWER = "sharedLink_viewer"

    /** The link's creator. The server refuses to grant, move or revoke it. */
    const val SHARED_LINK_OWNER = "sharedLink_owner"
}

/**
 * Upstream `PermissionBits` (accessPermissions.ts): the bit flags in an effective-permissions
 * value. A resource's bits are OR'd together, so test one with [has].
 */
object PermissionBits {
    const val VIEW = 1
    const val EDIT = 2
    const val DELETE = 4
    const val SHARE = 8

    fun has(bits: Int, bit: Int): Boolean = bits and bit != 0
}

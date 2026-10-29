package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.config.InterfaceConfig
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The tags a user sees as bookmarks: every tag but [SAVED_TAG], which is the favorite star and
 * never appears in a bookmark list. The one definition every bookmark surface filters with.
 */
fun List<ConversationTag>.bookmarks(): List<ConversationTag> =
    filter { tag -> tag.tag.let { !it.isNullOrBlank() && it != SAVED_TAG } }

/**
 * Whether bookmarks (and the favorite star, which is a tag) are available: BOOKMARKS USE on the
 * role AND `interface.bookmarks`, as web gates them. Both fail open: a null role (loading, failed,
 * timed out) is permissive like every other role gate, and an absent `interface` block is enabled.
 */
fun bookmarksEnabled(role: UserRolePermissions?, iface: InterfaceConfig?): Boolean =
    role.hasAccessOrPermissive(PermissionType.BOOKMARKS, Permission.USE) && (iface?.bookmarks ?: true)

/** [bookmarksEnabled] over the live role and startup config, for screens and session work. */
class BookmarksGate(
    private val roleRepository: RoleRepository,
    private val configRepository: ConfigRepository,
    private val permissionGate: PermissionGate,
) {
    val enabled: Flow<Boolean> =
        combine(roleRepository.userPermissions, configRepository.startupConfig) { role, config ->
            bookmarksEnabled(role, config?.interfaceConfig)
        }.distinctUntilChanged()

    /**
     * For work that starts with the session, alongside the role fetch: waits for the role
     * ([PermissionGate.awaitRole], permissive on timeout) before deciding.
     */
    suspend fun awaitEnabled(): Boolean =
        bookmarksEnabled(permissionGate.awaitRole(), configRepository.startupConfig.value?.interfaceConfig)
}

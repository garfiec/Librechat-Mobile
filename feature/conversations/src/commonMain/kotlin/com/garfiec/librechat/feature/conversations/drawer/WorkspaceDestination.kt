package com.garfiec.librechat.feature.conversations.drawer

/** The tray's two groups, in tray order. */
enum class WorkspaceGroup { BUILD, DATA }

/**
 * A screen the drawer's Workspace tray opens. Declaration order is tray order.
 *
 * [storageId] is what the pinned list persists ([com.garfiec.librechat.core.data.datastore.AppPreferences.DrawerPinned]),
 * decoupled from the constant's name: never rename one, or that pin is lost.
 */
enum class WorkspaceDestination(val storageId: String, val group: WorkspaceGroup) {
    AGENTS("agents", WorkspaceGroup.BUILD),
    SKILLS("skills", WorkspaceGroup.BUILD),
    PROMPTS("prompts", WorkspaceGroup.BUILD),
    MCP_SERVERS("mcp_servers", WorkspaceGroup.BUILD),
    SCHEDULES("schedules", WorkspaceGroup.BUILD),
    MEMORIES("memories", WorkspaceGroup.DATA),
    BOOKMARKS("bookmarks", WorkspaceGroup.DATA),
    FILES("files", WorkspaceGroup.DATA),
    SHARED_LINKS("shared_links", WorkspaceGroup.DATA),
    ARCHIVED("archived", WorkspaceGroup.DATA),
    ;

    companion object {
        val byStorageId: Map<String, WorkspaceDestination> = entries.associateBy { it.storageId }
    }
}

/** Stored ids → destinations in stored order, skipping unknown ids and ones [visible] leaves out. */
internal fun pinnedDestinations(storedIds: List<String>, visible: List<WorkspaceDestination>): List<WorkspaceDestination> =
    storedIds.mapNotNull(WorkspaceDestination.byStorageId::get).filter { it in visible }

package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.runtime.Immutable

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

/**
 * The Workspace footer and tray. [pinned] is null until the stored pins are read; [sections] holds
 * each [WorkspaceGroup]'s unpinned destinations, in group order.
 */
@Immutable
data class WorkspaceUiState(
    val visible: List<WorkspaceDestination> = emptyList(),
    val pinned: List<WorkspaceDestination>? = null,
    val sections: List<List<WorkspaceDestination>> = WorkspaceGroup.entries.map { emptyList() },
)

internal fun workspaceUiState(visible: List<WorkspaceDestination>, storedIds: List<String>?): WorkspaceUiState {
    val pinned = storedIds?.let { pinnedDestinations(it, visible) }
    val unpinned = visible - pinned.orEmpty().toSet()
    return WorkspaceUiState(
        visible = visible,
        pinned = pinned,
        sections = WorkspaceGroup.entries.map { group -> unpinned.filter { it.group == group } },
    )
}

/** Stored ids → destinations in stored order, skipping unknown ids and ones [visible] leaves out. */
internal fun pinnedDestinations(storedIds: List<String>, visible: List<WorkspaceDestination>): List<WorkspaceDestination> =
    storedIds.mapNotNull(WorkspaceDestination.byStorageId::get).filter { it in visible }

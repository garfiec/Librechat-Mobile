package com.garfiec.librechat.web.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.common.datetime.toDateGroup
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.search.TitleMatch
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.feature.conversations.drawer.DrawerAccountFooter
import com.garfiec.librechat.feature.conversations.drawer.DrawerContent
import com.garfiec.librechat.feature.conversations.drawer.DrawerConversationDisplayData
import com.garfiec.librechat.feature.conversations.drawer.DrawerGroupKey
import com.garfiec.librechat.feature.conversations.drawer.DrawerTab
import com.garfiec.librechat.feature.conversations.drawer.DrawerUiState
import com.garfiec.librechat.feature.conversations.drawer.InlineProjectChatsState
import com.garfiec.librechat.feature.conversations.drawer.ListLoad
import com.garfiec.librechat.feature.conversations.drawer.WorkspaceDestination
import com.garfiec.librechat.feature.conversations.drawer.workspaceUiState
import com.garfiec.librechat.feature.conversations.drawer.AccountUiModel
import kotlin.time.Instant

/**
 * The app's real drawer, fed sample chats for the active account ("personal", "work" or "family").
 * Its actions edit the sample data; nothing leaves the page.
 */
@Composable
fun DrawerDemo(
    accounts: List<AccountUiModel>,
    selectedConversationId: String?,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onOpenHit: (conversationId: String, jump: ChatJump) -> Unit,
    onOpenSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountId = accounts.firstOrNull { it.isActive }?.accountId
    val sample = AccountSamples[accountId] ?: EmptySampleAccount
    val reference = remember { RelativeTimeReference.current() }

    // Edits the user makes, per account, so switching away and back keeps them.
    var edits by remember { mutableStateOf(mapOf<String, DrawerSampleEdits>()) }
    val edit = edits[accountId] ?: DrawerSampleEdits.of(sample)
    val update: (DrawerSampleEdits.() -> DrawerSampleEdits) -> Unit = { change ->
        if (accountId != null) edits = edits + (accountId to edit.change())
    }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(DrawerTab.Chats) }
    var expandedProject by remember(accountId) { mutableStateOf<String?>(null) }
    var pinnedWorkspace by remember {
        mutableStateOf(listOf(WorkspaceDestination.AGENTS.storageId, WorkspaceDestination.BOOKMARKS.storageId))
    }

    val rows = sample.chats
        .filter { it.id !in edit.removed }
        .sortedBy { it.ago }
        .map { chat -> chat.toRow(reference.now, edit, selectedConversationId) }
    val state = sampleState(rows, query, reference).copy(messageSearch = drawerMessageSearch(accountId, query))
    val projects = edit.projects.map { project ->
        project.copy(conversationCount = rows.count { it.chatProjectId == project.id })
    }
    val saveProject: (ChatProject) -> Unit = { saved ->
        update {
            val replaced = projects.map { if (it.id == saved.id) saved else it }
            copy(projects = if (replaced.any { it.id == saved.id }) replaced else replaced + saved)
        }
    }

    CompositionLocalProvider(LocalRelativeTimeReference provides reference) {
        DrawerContent(
            uiState = state,
            onSearchQueryChange = { query = it },
            onMessageHitClick = { hit -> onOpenHit(hit.conversationId, hit.jump(query)) },
            onOpenSearch = onOpenSearch,
            onNewChat = onNewChat,
            onConversationClick = onSelectConversation,
            onWorkspaceClick = {},
            modifier = modifier,
            footerContent = {
                DrawerAccountFooter(
                    accounts = accounts,
                    onSettingsClick = onOpenSettings,
                    onAccountClick = onOpenAccounts,
                    onSwitchAccountInPlace = onSwitchAccount,
                )
            },
            workspace = workspaceUiState(state.visibleWorkspace, pinnedWorkspace),
            onPinWorkspace = { pinnedWorkspace = pinnedWorkspace + it.storageId },
            onUnpinWorkspace = { pinnedWorkspace = pinnedWorkspace - it.storageId },
            onReorderWorkspacePins = { order -> pinnedWorkspace = order.map { it.storageId } },
            onToggleFavorite = { row -> update { copy(favorites = favorites.toggle(row.conversationId)) } },
            onRename = { id, title -> update { copy(titles = titles + (id to title)) } },
            onArchive = { id -> update { copy(removed = removed + id) } },
            onDelete = { id -> update { copy(removed = removed + id) } },
            onPin = { id, pin -> update { copy(pinned = if (pin) pinned + id else pinned - id) } },
            projects = projects,
            onMoveToProject = { id, projectId -> update { copy(projectOf = projectOf + (id to projectId)) } },
            onCreateProjectAndAssign = { id, name ->
                val created = ChatProject(id = "new-${projects.size}-$name", name = name)
                update { copy(projects = projects + created, projectOf = projectOf + (id to created.id)) }
            },
            onReorderProjects = { order -> update { copy(projects = order.mapNotNull { id -> projects.find { it.id == id } }) } },
            onCreateProject = { name, description, onResult ->
                saveProject(ChatProject(id = "new-${projects.size}-$name", name = name, description = description))
                onResult(true)
            },
            onEditProject = { id, name, description, onResult ->
                projects.find { it.id == id }?.let { saveProject(it.copy(name = name, description = description)) }
                onResult(true)
            },
            onDeleteProject = { id -> update { copy(projects = projects.filterNot { it.id == id }) } },
            onNewChatInProject = { _, _ -> onNewChat() },
            selectedTab = tab,
            onSelectTab = { tab = it },
            inlineProjectChats = InlineProjectChatsState(
                expandedProjectId = expandedProject,
                conversations = rows.filter { row ->
                    if (expandedProject == ChatProject.UNASSIGNED) row.chatProjectId == null else row.chatProjectId == expandedProject
                },
            ),
            onToggleProject = { id -> expandedProject = id.takeIf { it != expandedProject } },
            onPickUpProject = { expandedProject = null },
            onMarkRead = { id -> update { copy(unread = unread - id) } },
            onMarkUnread = { id -> update { copy(unread = unread + id) } },
        )
    }
}

private fun sampleState(rows: List<DrawerConversationDisplayData>, query: String, reference: RelativeTimeReference): DrawerUiState {
    val searching = query.isNotBlank()
    val listed = if (searching) rows.filter { TitleMatch.matches(it.title, query) } else rows
    // As in the app, pinned chats live in their own section and leave the date groups, except in search.
    val grouped = listed
        .filter { searching || !it.isPinned }
        .groupBy { row -> DrawerGroupKey.Date(row.updatedAt!!.toDateGroup(reference)) }
        .toList()
    return DrawerUiState(
        groupedConversations = grouped,
        favoriteConversations = rows.filter { it.isFavorite },
        pinnedConversations = rows.filter { it.isPinned },
        searchQuery = query,
        listLoad = ListLoad.LOADED,
        hasMore = false,
        skillsEnabled = true,
        schedulesEnabled = true,
        pinEnabled = true,
        projectsEnabled = true,
        readStateEnabled = true,
    )
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

/** What the visitor changed in one account's drawer. */
private data class DrawerSampleEdits(
    val favorites: Set<String>,
    val pinned: Set<String>,
    val unread: Set<String>,
    val removed: Set<String> = emptySet(),
    val titles: Map<String, String> = emptyMap(),
    val projectOf: Map<String, String?> = emptyMap(),
    val projects: List<ChatProject>,
) {
    companion object {
        fun of(sample: SampleAccount) = DrawerSampleEdits(
            favorites = sample.chats.filter { it.favorite }.map { it.id }.toSet(),
            pinned = sample.chats.filter { it.pinned }.map { it.id }.toSet(),
            unread = sample.chats.filter { it.unread }.map { it.id }.toSet(),
            projects = sample.projects,
        )
    }
}

private fun SampleChat.toRow(now: Instant, edits: DrawerSampleEdits, selectedId: String?) = DrawerConversationDisplayData(
    conversationId = id,
    title = edits.titles[id] ?: title,
    model = model.name,
    endpoint = model.endpoint,
    updatedAt = now - ago,
    isActive = id == selectedId,
    isFavorite = id in edits.favorites,
    isPinned = id in edits.pinned,
    chatProjectId = if (id in edits.projectOf) edits.projectOf[id] else project,
    tags = emptyList(),
    iconProvider = model.provider,
    isUnread = id in edits.unread && id != selectedId,
)

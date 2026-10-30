package com.garfiec.librechat.shared.navigation

import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.agents.navigation.AgentDetail
import com.garfiec.librechat.feature.agents.navigation.AgentEditorCreate
import com.garfiec.librechat.feature.agents.navigation.AgentEditorEdit
import com.garfiec.librechat.feature.agents.navigation.AgentMarketplace
import com.garfiec.librechat.feature.chat.navigation.PromptEditor
import com.garfiec.librechat.feature.chat.navigation.PromptsLibrary
import com.garfiec.librechat.feature.conversations.drawer.WorkspaceDestination
import com.garfiec.librechat.feature.conversations.navigation.ArchivedConversations
import com.garfiec.librechat.feature.conversations.navigation.BookmarkChats
import com.garfiec.librechat.feature.conversations.navigation.Bookmarks
import com.garfiec.librechat.feature.files.navigation.Files
import com.garfiec.librechat.feature.schedules.navigation.ScheduleEditor
import com.garfiec.librechat.feature.schedules.navigation.SchedulesList
import com.garfiec.librechat.feature.settings.navigation.McpServerEditor
import com.garfiec.librechat.feature.settings.navigation.McpServers
import com.garfiec.librechat.feature.settings.navigation.Memories
import com.garfiec.librechat.feature.settings.navigation.SharedLinks
import com.garfiec.librechat.feature.skills.navigation.SkillDetail
import com.garfiec.librechat.feature.skills.navigation.SkillEditor
import com.garfiec.librechat.feature.skills.navigation.SkillFile
import com.garfiec.librechat.feature.skills.navigation.SkillsList

/** The screen a Workspace row opens. */
internal fun WorkspaceDestination.route(): NavKey = when (this) {
    WorkspaceDestination.AGENTS -> AgentMarketplace
    WorkspaceDestination.SKILLS -> SkillsList
    WorkspaceDestination.PROMPTS -> PromptsLibrary
    WorkspaceDestination.MCP_SERVERS -> McpServers
    WorkspaceDestination.SCHEDULES -> SchedulesList
    WorkspaceDestination.MEMORIES -> Memories
    WorkspaceDestination.BOOKMARKS -> Bookmarks
    WorkspaceDestination.FILES -> Files
    WorkspaceDestination.SHARED_LINKS -> SharedLinks
    WorkspaceDestination.ARCHIVED -> ArchivedConversations
}

/**
 * The Workspace destination a screen belongs to, for the tablet sidebar's highlight: its own route
 * or one of its sub-screens (an editor, a detail page). Null for anything else.
 */
internal fun NavKey?.workspaceDestination(): WorkspaceDestination? = when (this) {
    AgentMarketplace, is AgentDetail, AgentEditorCreate, is AgentEditorEdit -> WorkspaceDestination.AGENTS
    SkillsList, is SkillDetail, is SkillEditor, is SkillFile -> WorkspaceDestination.SKILLS
    PromptsLibrary, is PromptEditor -> WorkspaceDestination.PROMPTS
    McpServers, is McpServerEditor -> WorkspaceDestination.MCP_SERVERS
    SchedulesList, is ScheduleEditor -> WorkspaceDestination.SCHEDULES
    Memories -> WorkspaceDestination.MEMORIES
    Bookmarks, is BookmarkChats -> WorkspaceDestination.BOOKMARKS
    Files -> WorkspaceDestination.FILES
    SharedLinks -> WorkspaceDestination.SHARED_LINKS
    ArchivedConversations -> WorkspaceDestination.ARCHIVED
    else -> null
}

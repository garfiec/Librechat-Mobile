package com.garfiec.librechat.feature.agents.screen

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.agents.AgentContactDisplayData
import com.garfiec.librechat.feature.agents.AgentDetailDisplayData
import com.garfiec.librechat.feature.agents.viewmodel.AgentDetailUiState
import com.garfiec.librechat.feature.agents.viewmodel.AgentLoadError
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

// The agent page at the widths and translations that squeeze it: an editable full record with an
// HTML description, tools and starters; a view-only agent with a long name and no starters; and the
// page that couldn't load.

@PreviewNarrowScreens
@Composable
private fun AgentPageFullPreview() = DetailHost(UiStyle.MATERIAL, fullRecord())

@PreviewNarrowScreens
@Composable
private fun AgentPageFullGlassPreview() = DetailHost(UiStyle.LIQUID_GLASS, fullRecord())

@PreviewNarrowScreens
@Composable
private fun AgentPageMinimalPreview() = DetailHost(UiStyle.MATERIAL, viewOnly)

@PreviewNarrowScreens
@Composable
private fun AgentPageMinimalGlassPreview() = DetailHost(UiStyle.LIQUID_GLASS, viewOnly)

@PreviewNarrowScreens
@Composable
private fun AgentPageErrorPreview() = DetailHost(UiStyle.MATERIAL, loadFailed)

@PreviewNarrowScreens
@Composable
private fun AgentPageErrorGlassPreview() = DetailHost(UiStyle.LIQUID_GLASS, loadFailed)

@Composable
private fun DetailHost(style: UiStyle, state: AgentDetailUiState) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        ProvideDateTimeFormat(DateTimeFormatPrefs()) {
            AgentDetailContent(uiState = state, actions = AgentDetailActions())
        }
    }
}

private fun fullRecord() = AgentDetailUiState(
    agent = AgentDetailDisplayData(
        id = "release",
        name = "Release Notes Writer",
        description = "<p>Turns merged PRs into <b>user-facing</b> release notes.</p>" +
            "<ul><li>Groups by feature</li><li>Drops internal-only changes</li></ul>" +
            "<p>Style guide: <a href=\"https://example.com/voice\">house voice</a></p>",
        avatarUrl = null,
        model = "claude-sonnet-4-5-20250929",
        categoryValue = "rd",
        isPromoted = true,
        isPublic = true,
        updatedAt = (Clock.System.now() - 3.days).toString(),
        tools = listOf("web_search", "execute_code", "file_search", "list_pull_requests_mcp_github"),
        conversationStarters = listOf(
            "Write release notes for everything merged since the last tag",
            "Summarise this PR for the changelog",
        ),
        contact = AgentContactDisplayData(name = "Dana Okafor", email = "dana@example.com"),
    ),
    isFullRecord = true,
    canEdit = true,
    isPinned = true,
    categories = listOf(AgentCategory(value = "rd", label = "Research & Development")),
)

private val viewOnly = AgentDetailUiState(
    agent = AgentDetailDisplayData(
        id = "onboarding",
        name = "Onboarding Buddy for New Engineers Joining the Platform Team",
        description = "Answers questions about our repos, CI and on-call.\nAsk it anything.",
        avatarUrl = null,
        model = null,
        categoryValue = "general",
        contact = AgentContactDisplayData(name = "Platform", email = null),
    ),
    canCreate = false,
)

private val loadFailed = AgentDetailUiState(loadError = AgentLoadError(message = null))

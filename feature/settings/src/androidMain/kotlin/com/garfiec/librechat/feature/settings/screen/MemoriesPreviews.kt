package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

private val sampleMemories = listOf(
    Memory(
        id = "m1",
        key = "preferred_language",
        value = "Replies in British English, metric units, 24-hour clock",
        updatedAt = "2026-10-18T14:02:00Z",
        tokenCount = 18,
    ),
    Memory(
        id = "m2",
        key = "current_projects_and_long_running_goals",
        value = "Building a mobile client for LibreChat; training for a half marathon in March, three runs a week",
        updatedAt = "2026-10-02T09:15:00Z",
        agentId = "agent_research",
        agentName = "Research Assistant",
        tokenCount = 24,
    ),
    // An agent the user can no longer see: no name, so the generic label.
    Memory(id = "m3", key = "citation_style", value = "APA 7th", agentId = "agent_gone", tokenCount = 4),
    Memory(id = "m4", key = "", value = "", contentFilterBlocked = true, updatedAt = "2026-09-28T08:00:00Z", tokenCount = 9),
    // The filter matched the value only: the key arrives and shows; the value's place says why.
    Memory(id = "m5", key = "phone_number", value = "", contentFilterBlocked = true, tokenCount = 7),
    // Only the summary matched: key and value arrive intact and show as they are.
    Memory(id = "m6", key = "home_city", value = "Lisbon, moving to Porto in spring", contentFilterBlocked = true),
)

private fun usageAt(total: Int, limit: Int = 4_000): MemoriesResponse = MemoriesResponse(
    memories = sampleMemories,
    totalTokens = total,
    tokenLimit = limit,
    // As the server caps it.
    usagePercentage = minOf(100, Math.round(total * 100f / limit)),
)

@PreviewNarrowScreens
@Composable
private fun MemoryCardsMaterialPreview() = MemoryCardsHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun MemoryCardsGlassPreview() = MemoryCardsHost(dark = true, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun MemoriesSwitchCardMaterialPreview() = SwitchCardsHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun MemoriesSwitchCardGlassPreview() = SwitchCardsHost(dark = true, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun MemoriesEmptyMaterialPreview() = EmptyHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun MemoriesEmptyGlassPreview() = EmptyHost(dark = true, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun MemoriesViewOnlyMaterialPreview() = ViewOnlyHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun MemoriesViewOnlyGlassPreview() = ViewOnlyHost(dark = true, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun MemoriesNoAccessMaterialPreview() = NoAccessHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun MemoriesNoAccessGlassPreview() = NoAccessHost(dark = true, UiStyle.LIQUID_GLASS)

@Composable
private fun PreviewSurface(dark: Boolean, style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

/** Long key, agent with and without a name, hidden entries (whole, value only, summary only); then a deleting card, a numbered agent and the placeholder. */
@Composable
private fun MemoryCardsHost(dark: Boolean, style: UiStyle) = PreviewSurface(dark, style) {
    sampleMemories.forEach { memory ->
        MemoryCard(memory = memory, canUpdate = true, onOpen = {}, onEdit = {}, onDelete = {})
    }
    MemoryCard(memory = sampleMemories[0], canUpdate = false, onOpen = {}, onEdit = {}, onDelete = {})
    MemoryCard(memory = sampleMemories[0], canUpdate = true, deleting = true, onOpen = {}, onEdit = {}, onDelete = {})
    // A second unnamed agent: numbered so its label differs from the first one's.
    MemoryCard(memory = sampleMemories[2], canUpdate = true, agentOrdinal = 2, onOpen = {}, onEdit = {}, onDelete = {})
    MemoryCardPlaceholder()
}

/** 60 / 80 / 95 % and over the limit (a PATCH can push it past), the switch unknown and unreadable, usage alone. */
@Composable
private fun SwitchCardsHost(dark: Boolean, style: UiStyle) = PreviewSurface(dark, style) {
    listOf(2_400, 3_200, 3_800, 4_500).forEach { total ->
        MemoriesSwitchCard(
            showSwitch = true,
            enabled = true,
            busy = false,
            usage = memoryUsage(usageAt(total)),
            onToggle = {},
        )
    }
    MemoriesSwitchCard(showSwitch = true, enabled = null, busy = false, usage = null, onToggle = {})
    // The opt-out could not be read: Retry in the switch's place.
    MemoriesSwitchCard(showSwitch = true, enabled = null, busy = false, usage = null, onToggle = {}, loadFailed = true)
    MemoriesSwitchCard(showSwitch = false, enabled = false, busy = false, usage = memoryUsage(usageAt(1_200)), onToggle = {})
}

@Composable
private fun EmptyHost(dark: Boolean, style: UiStyle) = PreviewSurface(dark, style) {
    MemoriesSwitchCard(showSwitch = true, enabled = true, busy = false, usage = null, onToggle = {})
    Box(Modifier.height(420.dp)) { MemoriesEmptyState(canCreate = true, onAdd = {}) }
}

@Composable
private fun ViewOnlyHost(dark: Boolean, style: UiStyle) = PreviewSurface(dark, style) {
    MemoriesSwitchCard(showSwitch = true, enabled = false, busy = false, usage = null, onToggle = {})
    MemoriesViewOnlyNotice()
    sampleMemories.take(2).forEach { memory ->
        MemoryCard(memory = memory, canUpdate = false, onOpen = {}, onEdit = {}, onDelete = {})
    }
    // A hidden entry without UPDATE: no menu, and its text doesn't offer a Delete.
    MemoryCard(memory = sampleMemories[3], canUpdate = false, onOpen = {}, onEdit = {}, onDelete = {})
    Box(Modifier.height(240.dp)) { MemoriesEmptyState(canCreate = false, onAdd = {}) }
}

@Composable
private fun NoAccessHost(dark: Boolean, style: UiStyle) = PreviewSurface(dark, style) {
    Box(Modifier.height(420.dp)) { MemoriesNoAccessState() }
}

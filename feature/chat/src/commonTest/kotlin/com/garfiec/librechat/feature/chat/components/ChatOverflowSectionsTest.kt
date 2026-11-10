package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.feature.chat.components.ChatBarItem.BACKGROUND_TASKS
import com.garfiec.librechat.feature.chat.components.ChatBarItem.CONTEXT_RING
import com.garfiec.librechat.feature.chat.components.ChatBarItem.FIND
import com.garfiec.librechat.feature.chat.components.ChatBarItem.NEW_CHAT
import com.garfiec.librechat.feature.chat.components.ChatBarItem.OVERFLOW
import com.garfiec.librechat.feature.chat.components.ChatBarItem.TEMPORARY_CHAT
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.ARCHIVE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.BOOKMARKS
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.COMPARE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.CONTEXT_USAGE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.DELETE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.DUPLICATE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.LOAD_PRESET
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.PROMPTS_LIBRARY
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.PULL_REQUEST
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.RENAME
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.SAVE_PRESET
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.SEARCH
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.SHARE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.SHOW_ALL_MEDIA
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.TRACE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Material dropdown and the native iOS menu both render this list, so its gating and order are
 * the menu's gating and order on both platforms.
 */
class ChatOverflowSectionsTest {

    private val usage = ContextUsage(breakdown = TokenBudgetBreakdown(maxContextTokens = 1000, messageTokens = 250))

    private fun sections(
        conversationId: String? = "c1",
        hasShowAllMedia: Boolean = true,
        presetsEnabled: Boolean = true,
        hasPromptsLibrary: Boolean = true,
        promptsEnabled: Boolean = true,
        multiConvoEnabled: Boolean = true,
        traceViewerAvailable: Boolean = true,
        contextUsage: ContextUsage? = usage,
        contextUsageEnabled: Boolean = true,
        contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OVERFLOW_MENU,
        sharedLinksEnabled: Boolean = true,
        compactNudgeVisible: Boolean = false,
        firstTurnStreaming: Boolean = false,
        bookmarksEnabled: Boolean = true,
        isTemporaryChat: Boolean = false,
        hasPullRequest: Boolean = false,
        openApps: Int = 0,
        findInRail: Boolean = false,
    ) = chatOverflowSections(
        ChatOverflowGates(
            conversationId, hasShowAllMedia, presetsEnabled, hasPromptsLibrary, promptsEnabled, multiConvoEnabled,
            traceViewerAvailable, contextUsage, contextUsageEnabled, contextBarPlacement, sharedLinksEnabled,
            compactNudgeVisible, firstTurnStreaming, bookmarksEnabled, isTemporaryChat, hasPullRequest, openApps,
            findInRail,
        ),
    )

    private fun bar(
        rail: Boolean = true,
        conversationId: String? = "c1",
        hasNewChat: Boolean = true,
        showTempChatToggle: Boolean = false,
        showPullRequestChip: Boolean = false,
        backgroundTasksVisible: Boolean = false,
        contextUsageEnabled: Boolean = true,
        contextBarPlacement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
        usedTokens: Int = 250,
    ) = chatBarItems(
        ChatBarGates(
            rail, conversationId, hasNewChat, showTempChatToggle, showPullRequestChip, backgroundTasksVisible,
            contextUsageEnabled, contextBarPlacement, usedTokens,
        ),
    )

    @Test
    fun compactAppearsUnderContextOnlyWhenDueAndTheGaugeLivesInTheMenu() {
        val tools = sections(compactNudgeVisible = true).first()
        assertEquals(ChatOverflowItem.COMPACT, tools[tools.indexOf(ChatOverflowItem.CONTEXT_USAGE) + 1])
        assertFalse(ChatOverflowItem.COMPACT in sections(compactNudgeVisible = false).flatten())
        assertFalse(
            ChatOverflowItem.COMPACT in
                sections(compactNudgeVisible = true, contextBarPlacement = ContextBarPlacement.ABOVE_INPUT).flatten(),
        )
    }

    @Test
    fun everythingOnGivesThreeSectionsInMenuOrder() {
        assertEquals(
            listOf(
                listOf(SEARCH, SHOW_ALL_MEDIA, LOAD_PRESET, SAVE_PRESET, PROMPTS_LIBRARY, COMPARE, TRACE, CONTEXT_USAGE),
                listOf(SHARE, BOOKMARKS, RENAME, DUPLICATE, ARCHIVE),
                listOf(DELETE),
            ),
            sections(),
        )
    }

    @Test
    fun aKnownPullRequestLeadsTheConversationSectionAndIsAbsentOtherwise() {
        assertEquals(listOf(PULL_REQUEST, SHARE, BOOKMARKS, RENAME, DUPLICATE, ARCHIVE), sections(hasPullRequest = true)[1])
        assertFalse(PULL_REQUEST in sections().flatten())
        assertFalse(PULL_REQUEST in sections(conversationId = null, hasPullRequest = true).flatten())
    }

    @Test
    fun theLandingHasNoConversationActions() {
        assertEquals(
            listOf(listOf(SHOW_ALL_MEDIA, LOAD_PRESET, SAVE_PRESET, PROMPTS_LIBRARY, COMPARE, TRACE, CONTEXT_USAGE)),
            sections(conversationId = null),
        )
    }

    @Test
    fun anEmptyToolsSectionIsDroppedRatherThanLeavingALeadingDivider() {
        val result = sections(
            conversationId = null,
            hasShowAllMedia = false,
            presetsEnabled = false,
            hasPromptsLibrary = false,
            multiConvoEnabled = false,
            traceViewerAvailable = false,
            contextUsage = null,
        )
        assertEquals(emptyList(), result)
    }

    @Test
    fun serverGatesRemoveTheirItems() {
        val result = sections(
            presetsEnabled = false,
            promptsEnabled = false,
            sharedLinksEnabled = false,
            multiConvoEnabled = false,
            bookmarksEnabled = false,
        )
        assertEquals(listOf(SEARCH, SHOW_ALL_MEDIA, TRACE, CONTEXT_USAGE), result[0])
        assertEquals(listOf(RENAME, DUPLICATE, ARCHIVE), result[1])
    }

    @Test
    fun shareAndBookmarksHideWhileTheFirstTurnIsStreaming() {
        assertEquals(listOf(RENAME, DUPLICATE, ARCHIVE), sections(firstTurnStreaming = true)[1])
        assertEquals(listOf(SHARE, BOOKMARKS, RENAME, DUPLICATE, ARCHIVE), sections(firstTurnStreaming = false)[1])
    }

    @Test
    fun bookmarksSitAfterShareOnASavedChat() {
        assertEquals(listOf(SHARE, BOOKMARKS, RENAME, DUPLICATE, ARCHIVE), sections()[1])
        assertEquals(listOf(BOOKMARKS, RENAME, DUPLICATE, ARCHIVE), sections(sharedLinksEnabled = false)[1])
    }

    @Test
    fun bookmarksHideOnATemporaryChat() {
        assertFalse(BOOKMARKS in sections(isTemporaryChat = true).flatten())
        assertEquals(listOf(SHARE, RENAME, DUPLICATE, ARCHIVE), sections(isTemporaryChat = true)[1])
    }

    @Test
    fun bookmarksHideOnANewChat() {
        assertFalse(BOOKMARKS in sections(conversationId = null).flatten())
    }

    @Test
    fun bookmarksHideWhenTheRoleOrServerTurnsThemOff() {
        assertFalse(BOOKMARKS in sections(bookmarksEnabled = false).flatten())
        assertEquals(listOf(SHARE, RENAME, DUPLICATE, ARCHIVE), sections(bookmarksEnabled = false)[1])
    }

    @Test
    fun contextUsageAppearsOnlyInTheOverflowPlacementWithTokensUsed() {
        assertEquals(false, CONTEXT_USAGE in sections(contextBarPlacement = ContextBarPlacement.OPTIONS_SHEET)[0])
        assertEquals(false, CONTEXT_USAGE in sections(contextUsageEnabled = false)[0])
        assertEquals(false, CONTEXT_USAGE in sections(contextUsage = ContextUsage())[0])
    }

    @Test
    fun minimizedAppsPutOpenAppsAtTheTopEvenOnTheLanding() {
        assertEquals(ChatOverflowItem.OPEN_APPS, sections(openApps = 2).first().first())
        assertEquals(listOf(ChatOverflowItem.OPEN_APPS), sections(conversationId = null, hasShowAllMedia = false, presetsEnabled = false,
            hasPromptsLibrary = false, multiConvoEnabled = false, traceViewerAvailable = false, contextUsage = null, openApps = 1).first())
        assertFalse(ChatOverflowItem.OPEN_APPS in sections(openApps = 0).flatten())
    }

    @Test
    fun findLeavesTheMenuOnlyWhileTheRailShowsIt() {
        assertTrue(SEARCH in sections().flatten())
        assertFalse(SEARCH in sections(findInRail = true).flatten())
        assertEquals(listOf(SHOW_ALL_MEDIA, LOAD_PRESET), sections(findInRail = true)[0].take(2))
    }

    @Test
    fun railTopGroupIsNewChatFindToggleThenOverflow() {
        assertEquals(listOf(NEW_CHAT, FIND, TEMPORARY_CHAT, OVERFLOW), bar(showTempChatToggle = true).actions)
        assertEquals(listOf(NEW_CHAT, FIND, OVERFLOW), bar().actions)
    }

    @Test
    fun railLeavesOutFindOnTheLandingAndNewChatWithoutAHandler() {
        assertEquals(listOf(NEW_CHAT, TEMPORARY_CHAT, OVERFLOW), bar(conversationId = null, showTempChatToggle = true).actions)
        assertEquals(listOf(FIND, OVERFLOW), bar(hasNewChat = false).actions)
    }

    @Test
    fun railKeepsThePullRequestChipOffItsTopGroup() {
        assertFalse(ChatBarItem.PULL_REQUEST in bar(showPullRequestChip = true).actions)
    }

    @Test
    fun railBottomGroupIsBackgroundTasksThenTheRing() {
        assertEquals(listOf(BACKGROUND_TASKS, CONTEXT_RING), bar(backgroundTasksVisible = true).status)
        assertEquals(listOf(CONTEXT_RING), bar().status)
    }

    @Test
    fun railRingNeedsUsageEnabledTokensUsedAndAVisiblePlacement() {
        assertEquals(emptyList(), bar(contextUsageEnabled = false).status)
        assertEquals(emptyList(), bar(usedTokens = 0).status)
        assertEquals(emptyList(), bar(contextBarPlacement = ContextBarPlacement.HIDDEN).status)
        assertEquals(listOf(CONTEXT_RING), bar(contextBarPlacement = ContextBarPlacement.ABOVE_INPUT).status)
        assertEquals(listOf(CONTEXT_RING), bar(contextBarPlacement = ContextBarPlacement.OVERFLOW_MENU).status)
    }

    @Test
    fun horizontalBarAddsBackgroundTasksBeforeTheToggleAndNothingElse() {
        val items =
            bar(rail = false, showTempChatToggle = true, showPullRequestChip = true, backgroundTasksVisible = true)
        assertEquals(listOf(ChatBarItem.PULL_REQUEST, BACKGROUND_TASKS, TEMPORARY_CHAT, OVERFLOW), items.actions)
        assertEquals(emptyList(), items.status)
        assertEquals(listOf(OVERFLOW), bar(rail = false).actions)
    }
}

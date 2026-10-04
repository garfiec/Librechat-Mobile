package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.ARCHIVE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.COMPARE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.CONTEXT_USAGE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.DELETE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.DUPLICATE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.LOAD_PRESET
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.PROMPTS_LIBRARY
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.RENAME
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.SAVE_PRESET
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.SEARCH
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.SHARE
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.SHOW_ALL_MEDIA
import com.garfiec.librechat.feature.chat.components.ChatOverflowItem.TRACE
import kotlin.test.Test
import kotlin.test.assertEquals

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
    ) = chatOverflowSections(
        ChatOverflowGates(
            conversationId, hasShowAllMedia, presetsEnabled, hasPromptsLibrary, promptsEnabled, multiConvoEnabled,
            traceViewerAvailable, contextUsage, contextUsageEnabled, contextBarPlacement, sharedLinksEnabled,
        ),
    )

    @Test
    fun everythingOnGivesThreeSectionsInMenuOrder() {
        assertEquals(
            listOf(
                listOf(SEARCH, SHOW_ALL_MEDIA, LOAD_PRESET, SAVE_PRESET, PROMPTS_LIBRARY, COMPARE, TRACE, CONTEXT_USAGE),
                listOf(SHARE, RENAME, DUPLICATE, ARCHIVE),
                listOf(DELETE),
            ),
            sections(),
        )
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
        val result =
            sections(presetsEnabled = false, promptsEnabled = false, sharedLinksEnabled = false, multiConvoEnabled = false)
        assertEquals(listOf(SEARCH, SHOW_ALL_MEDIA, TRACE, CONTEXT_USAGE), result[0])
        assertEquals(listOf(RENAME, DUPLICATE, ARCHIVE), result[1])
    }

    @Test
    fun contextUsageAppearsOnlyInTheOverflowPlacementWithTokensUsed() {
        assertEquals(false, CONTEXT_USAGE in sections(contextBarPlacement = ContextBarPlacement.OPTIONS_SHEET)[0])
        assertEquals(false, CONTEXT_USAGE in sections(contextUsageEnabled = false)[0])
        assertEquals(false, CONTEXT_USAGE in sections(contextUsage = ContextUsage())[0])
    }
}

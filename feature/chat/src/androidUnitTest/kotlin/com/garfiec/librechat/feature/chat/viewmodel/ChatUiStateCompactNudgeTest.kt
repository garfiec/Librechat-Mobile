package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.TokenBudgetBreakdown
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [ChatUiState.compactNudge]: the suggestion to compact. Shown from the threshold in the active
 * placement, never one tap; hidden whenever compacting isn't possible, on an estimate, or once
 * "Not now" covers the current band.
 */
class ChatUiStateCompactNudgeTest {

    private val leaf = Message(messageId = "leaf-1", conversationId = "conv-1", parentMessageId = "user-1")

    private fun usageAt(percent: Int) = ContextUsage(
        breakdown = TokenBudgetBreakdown(maxContextTokens = 1000, messageTokens = percent * 10),
    )

    private fun state(
        percent: Int = 72,
        source: ContextUsageSource = ContextUsageSource.SNAPSHOT,
        placement: ContextBarPlacement = ContextBarPlacement.ABOVE_INPUT,
        threshold: Int = 70,
        snoozes: Map<String, Int> = emptyMap(),
        isStreaming: Boolean = false,
        isCompacting: Boolean = false,
    ) = ChatUiState(
        conversation = ConversationMetaState(conversationId = "conv-1"),
        selection = ModelSelectionState(selectedEndpoint = "openAI"),
        gates = FeatureGatesState(compactionEnabled = true, contextUsageEnabled = true),
        prefs = ChatPrefsState(
            contextBarPlacement = placement,
            compactNudgeThreshold = threshold,
            compactNudgeSnoozes = snoozes,
        ),
        content = MessagesState(
            isStreaming = isStreaming,
            isCompacting = isCompacting,
            displayMessages = listOf(MessageNode(message = leaf, children = emptyList(), siblingIndex = 0, siblingCount = 1)),
            contextUsage = usageAt(percent),
            contextUsageSource = source,
        ),
    )

    @Test
    fun `appears from the threshold with its band`() {
        assertThat(state(percent = 69).compactNudge).isNull()
        assertThat(state(percent = 70).compactNudge).isEqualTo(CompactNudge(band = 0, percent = 70))
        assertThat(state(percent = 85).compactNudge?.band).isEqualTo(1)
        assertThat(state(percent = 96).compactNudge?.band).isEqualTo(2)
    }

    @Test
    fun `off means never`() {
        assertThat(state(percent = 99, threshold = 0).compactNudge).isNull()
    }

    @Test
    fun `hidden while a reply streams or a compaction runs`() {
        assertThat(state(isStreaming = true).compactNudge).isNull()
        // canCompactNow doesn't cover this: isCompacting is set before isStreaming flips.
        assertThat(state(isCompacting = true).compactNudge).isNull()
    }

    @Test
    fun `an estimate never suggests compacting`() {
        assertThat(state(source = ContextUsageSource.ESTIMATE).compactNudge).isNull()
        assertThat(state(source = ContextUsageSource.LIVE).compactNudge).isNotNull()
    }

    @Test
    fun `hidden when the user hid the gauge`() {
        assertThat(state(placement = ContextBarPlacement.HIDDEN).compactNudge).isNull()
    }

    @Test
    fun `not now covers its band and re-arms at the next`() {
        val snoozed = mapOf("conv-1" to 0)

        assertThat(state(percent = 75, snoozes = snoozed).compactNudge).isNull()
        assertThat(state(percent = 80, snoozes = snoozed).compactNudge?.band).isEqualTo(1)
        assertThat(state(percent = 85, snoozes = mapOf("conv-1" to 1)).compactNudge).isNull()
        assertThat(state(percent = 95, snoozes = mapOf("conv-1" to 1)).compactNudge?.band).isEqualTo(2)
        // Another conversation's snooze doesn't apply.
        assertThat(state(percent = 75, snoozes = mapOf("conv-2" to 0)).compactNudge).isNotNull()
    }

    @Test
    fun `the plus button is marked only under the options sheet placement`() {
        assertThat(state(placement = ContextBarPlacement.OPTIONS_SHEET).compactNudgeOnToolsButton).isTrue()
        assertThat(state(placement = ContextBarPlacement.ABOVE_INPUT).compactNudgeOnToolsButton).isFalse()
        assertThat(state(placement = ContextBarPlacement.OPTIONS_SHEET, percent = 50).compactNudgeOnToolsButton).isFalse()
    }

    @Test
    fun `below the threshold is a real reading under it`() {
        assertThat(state(percent = 50).isBelowCompactThreshold).isTrue()
        assertThat(state(percent = 75).isBelowCompactThreshold).isFalse()
        // An estimate can't clear a snooze.
        assertThat(state(percent = 50, source = ContextUsageSource.ESTIMATE).isBelowCompactThreshold).isFalse()
    }
}

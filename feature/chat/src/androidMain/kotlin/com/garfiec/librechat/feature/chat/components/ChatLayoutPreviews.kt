package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.ui.contextusage.SampleContextModel
import com.garfiec.librechat.core.ui.glass.GlassSheetHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.prompts.components.PromptFilterSheet
import com.garfiec.librechat.feature.chat.prompts.components.PromptSortOrder
import com.garfiec.librechat.feature.chat.viewmodel.ContextGaugeDetails

// The chat's bodies laid out as ChatScreen lays them out (above the keyboard, under the floating
// bar, with the composer over their foot), and the chat's sheets at their tallest.

@PreviewNarrowScreens
@Composable
private fun LandingPreview() = PreviewSurface(UiStyle.MATERIAL) { LandingHost() }

@PreviewNarrowScreens
@Composable
private fun TemporaryLandingPreview() = PreviewSurface(UiStyle.MATERIAL) { LandingHost(isTemporaryChat = true) }

@PreviewNarrowScreens
@Composable
private fun MessagesUnavailablePreview() = PreviewSurface(UiStyle.MATERIAL) { MessagesUnavailableHost() }

@PreviewNarrowScreens
@Composable
private fun ContextUsageSheetPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ContextUsageSheetHost() }

@PreviewNarrowScreens
@Composable
private fun ForkOptionsPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ForkOptionsHost() }

@PreviewNarrowScreens
@Composable
private fun PromptFilterSheetPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { PromptFilterSheetHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        GlassSheetHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}

@Composable
private fun ChatBodyHost(body: @Composable ColumnScope.(Modifier) -> Unit) {
    Box(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize()) { body(Modifier.weight(1f).padding(top = PreviewTopBarHeight)) }
        // Stands in for the composer, which ChatScreen draws over the bottom of the body.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(PreviewComposerHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
    }
}

@Composable
private fun LandingHost(isTemporaryChat: Boolean = false) = ChatBodyHost { modifier ->
    LandingContent(
        selectedModel = "gpt-5",
        selectedAgentName = "Travel Planner",
        isTemporaryChat = isTemporaryChat,
        modifier = modifier,
    )
}

@Composable
private fun MessagesUnavailableHost() = ChatBodyHost { modifier ->
    // ChatScreen floors the list's bottom padding at 160dp, so the preview does too.
    MessagesUnavailable(
        onRetry = {},
        bottomContentPadding = maxOf(160.dp, PreviewComposerHeight + 16.dp),
        modifier = modifier,
    )
}

@Composable
private fun ContextUsageSheetHost() {
    ContextUsageSheet(
        details = ContextGaugeDetails(
            model = SampleContextModel,
            sections = ContextDetailPreset.DETAILED.sections,
            nudge = null,
        ),
        onDismiss = {},
        onCompact = {},
    )
}

@Composable
private fun ForkOptionsHost() {
    ForkOptionsBottomSheet(onDismiss = {}, onFork = { _, _ -> })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptFilterSheetHost() {
    PromptFilterSheet(
        categories = listOf("coding", "writing", "research", "marketing", "education", "productivity", "travel", "finance"),
        selectedCategory = "coding",
        onCategorySelect = {},
        sortOrder = PromptSortOrder.RECENT,
        onSortOrderChange = {},
        onDismiss = {},
    )
}

private val PreviewTopBarHeight = 80.dp
private val PreviewComposerHeight = 72.dp

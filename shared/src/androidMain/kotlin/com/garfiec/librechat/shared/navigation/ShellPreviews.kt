package com.garfiec.librechat.shared.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.glass.rememberGlassStyle
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.components.CodeBlock
import com.garfiec.librechat.feature.conversations.drawer.DrawerContent
import com.garfiec.librechat.feature.conversations.drawer.DrawerConversationDisplayData
import com.garfiec.librechat.feature.conversations.drawer.DrawerGroupKey
import com.garfiec.librechat.feature.conversations.drawer.DrawerUiState

// The open phone drawer, and how much screen it leaves to tap away on.
@Preview(name = "320dp", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "360dp", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun PhoneDrawerGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { PhoneDrawerHost() }

@Preview(name = "320dp", widthDp = 320, heightDp = 640, showBackground = true)
@Composable
private fun PhoneDrawerMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { PhoneDrawerHost() }

// Which shell a window gets, decided by usesSidebarLayout. A sketch of the shell rather than the real
// one (TabletLayout needs the app's ViewModels): the sidebar's drawer beside a chat pane.
@Preview(name = "600dp", widthDp = 600, heightDp = 800, showBackground = true)
@Preview(name = "700dp", widthDp = 700, heightDp = 800, showBackground = true)
@Composable
private fun ShellChoicePreview() = PreviewSurface(UiStyle.MATERIAL) { ShellChoiceHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun PhoneDrawerHost() {
    val glass = rememberGlassStyle()
    val drawerState = rememberDrawerState(DrawerValue.Open)
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = DrawerDefaults.scrimColor,
        drawerContent = {
            if (glass == null) {
                MaterialDrawerSheet(drawerState = drawerState) { PreviewDrawer() }
            } else {
                GlassDrawerSheet(drawerState = drawerState, glass = glass, backdrop = null, onWidthChange = {}) {
                    PreviewDrawer()
                }
            }
        },
    ) {
        ChatPane(Modifier.fillMaxSize())
    }
}

@Composable
private fun ShellChoiceHost() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (usesSidebarLayout(maxWidth, maxHeight)) {
            Row(modifier = Modifier.fillMaxSize()) {
                PreviewDrawer(Modifier.width(320.dp).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ChatPane(Modifier.fillMaxSize())
            }
        } else {
            ChatPane(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PreviewDrawer(modifier: Modifier = Modifier) = ProvideDateTimeFormat(DateTimeFormatPrefs()) {
    DrawerContent(
        uiState = previewDrawerState,
        onSearchQueryChange = {},
        onNewChat = {},
        onConversationClick = {},
        onAgentsClick = {},
        onFilesClick = {},
        onSkillsClick = {},
        onSchedulesClick = {},
        modifier = modifier,
    )
}

/** An assistant turn: a paragraph and a code block, at whatever width the shell leaves it. */
@Composable
private fun ChatPane(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        val paneWidth = maxWidth
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Chat pane: ${paneWidth.value.toInt()}dp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Sure. Here is a retry wrapper that backs off exponentially and gives up after five attempts:",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CodeBlock(
                code = "suspend fun <T> retrying(block: suspend () -> T): T {\n" +
                    "    repeat(4) { attempt -> runCatching { return block() }; delay(200L shl attempt) }\n" +
                    "    return block()\n}",
                language = "kotlin",
            )
        }
    }
}

private fun chat(id: String, title: String) = DrawerConversationDisplayData(
    conversationId = id,
    title = title,
    model = "gpt-5",
    endpoint = "openAI",
    updatedAt = null,
    isActive = false,
    isFavorite = false,
    isPinned = false,
    tags = emptyList(),
)

private val previewDrawerState = DrawerUiState(
    groupedConversations = listOf(
        DrawerGroupKey.Date(DateGroup.Today) to listOf(
            chat("a", "Retry wrapper with backoff"),
            chat("b", "Trip to Lisbon in May"),
            chat("c", "Birthday dinner ideas"),
        ),
    ),
)

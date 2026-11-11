package com.garfiec.librechat.feature.settings.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.EndpointAvatar
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.theme.themeShapes
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val LayoutOptions = listOf(ChatLayoutConstants.THREAD, ChatLayoutConstants.TWO_SIDED)

/** Settings › Appearance › Chat appearance, in its own bar. */
@Composable
fun ChatLayoutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatLayoutSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsPageScaffold(
        title = stringResource(Res.string.appearance_chat_appearance),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { contentPadding ->
        ChatAppearanceContent(
            state = state,
            actions = ChatAppearanceActions(
                onLayoutStyle = viewModel::setLayoutStyle,
                onFontSize = viewModel::setFontSize,
                onHeaderContent = viewModel::setHeaderContent,
                onHeaderAlignment = viewModel::setHeaderAlignment,
                onShowBubbles = viewModel::setShowBubbles,
                onShowAvatars = viewModel::setShowAvatars,
            ),
            contentPadding = contentPadding,
        )
    }
}

internal class ChatAppearanceActions(
    val onLayoutStyle: (String) -> Unit,
    val onFontSize: (ChatFontSize) -> Unit,
    val onHeaderContent: (ChatHeaderContent) -> Unit,
    val onHeaderAlignment: (ChatHeaderAlignment) -> Unit,
    val onShowBubbles: (Boolean) -> Unit,
    val onShowAvatars: (Boolean) -> Unit,
)

/**
 * A live preview of a short conversation under the chat's title bar, then the choices that change it:
 * layout, text size, what the title bar shows and where, bubbles and avatars.
 */
@Composable
internal fun ChatAppearanceContent(
    state: ChatLayoutSettingsUiState,
    actions: ChatAppearanceActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "top_gap") { Spacer(modifier = Modifier.height(SectionGap)) }
        adaptiveSection {
            row(key = "preview") { ChatAppearancePreview(state) }
        }
        item(key = "layout") {
            PillGroup(
                label = stringResource(Res.string.chat_appearance_layout),
                options = LayoutOptions.map { chatLayoutLabel(it) },
                selectedIndex = LayoutOptions.indexOf(state.layoutStyle).coerceAtLeast(0),
                onSelect = { actions.onLayoutStyle(LayoutOptions[it]) },
                description = stringResource(
                    if (state.layoutStyle == ChatLayoutConstants.TWO_SIDED) {
                        Res.string.chat_layout_two_sided_desc
                    } else {
                        Res.string.chat_layout_thread_desc
                    },
                ),
            )
        }
        item(key = "text_size") {
            PillGroup(
                label = stringResource(Res.string.chat_appearance_text_size),
                options = ChatFontSize.entries.map { fontSizeLabel(it) },
                selectedIndex = state.fontSize.ordinal,
                onSelect = { actions.onFontSize(ChatFontSize.entries[it]) },
            )
        }
        item(key = "title_bar_content") {
            PillGroup(
                label = stringResource(Res.string.chat_appearance_title_bar),
                options = ChatHeaderContent.entries.map { chatHeaderContentLabel(it) },
                selectedIndex = state.headerContent.ordinal,
                onSelect = { actions.onHeaderContent(ChatHeaderContent.entries[it]) },
            )
        }
        item(key = "title_position") {
            PillGroup(
                label = stringResource(Res.string.chat_appearance_title_position),
                options = ChatHeaderAlignment.entries.map { chatHeaderAlignmentLabel(it) },
                selectedIndex = state.headerAlignment.ordinal,
                onSelect = { actions.onHeaderAlignment(ChatHeaderAlignment.entries[it]) },
                // With nothing in the title bar there is nothing to position.
                enabled = state.headerContent != ChatHeaderContent.NONE,
            )
        }
        item(key = "switches_gap") { Spacer(modifier = Modifier.height(SwitchesGap)) }
        adaptiveSection {
            row(key = "show_bubbles") {
                SettingsSwitchRow(
                    title = stringResource(Res.string.show_bubbles),
                    description = stringResource(Res.string.show_bubbles_desc),
                    checked = state.showBubbles,
                    onCheckedChange = actions.onShowBubbles,
                )
            }
            row(key = "show_avatars") {
                SettingsSwitchRow(
                    title = stringResource(Res.string.show_avatars),
                    description = stringResource(Res.string.show_avatars_desc),
                    checked = state.showAvatars,
                    onCheckedChange = actions.onShowAvatars,
                )
            }
        }
        item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/** Appearance's one-line summary of this page: the layout, the text size, then what the title bar shows. */
@Composable
internal fun chatLayoutSummary(state: ChatLayoutSettingsUiState): String = buildList {
    add(chatLayoutLabel(state.layoutStyle))
    add(fontSizeLabel(state.fontSize))
    if (state.headerContent != ChatHeaderContent.NONE) add(chatHeaderContentLabel(state.headerContent))
}.joinToString(" · ")

/**
 * The chat's title bar over a question and its answer, laid out as the chat lays them out, at the
 * chat's own measures and text size. Keep it in step with `ChatFloatingTopBar` and `MessageBubble`
 * in `:feature:chat`.
 */
@Composable
private fun ChatAppearancePreview(state: ChatLayoutSettingsUiState) {
    val twoSided = state.layoutStyle == ChatLayoutConstants.TWO_SIDED
    val bodyStyle = MaterialTheme.typography.bodyLarge.let { style ->
        val scale = state.fontSize.chatScale
        style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
    }
    val agentName = stringResource(Res.string.chat_appearance_sample_agent)
    val youName = stringResource(Res.string.chat_appearance_sample_you)
    val messages = listOf(
        SampleMessage(isUser = true, text = stringResource(Res.string.chat_appearance_sample_question)),
        SampleMessage(isUser = false, text = stringResource(Res.string.chat_appearance_sample_answer)),
        SampleMessage(isUser = true, text = stringResource(Res.string.chat_appearance_sample_thanks)),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 12.dp)
            .animateContentSize()
            .clearAndSetSemantics {},
    ) {
        SampleTitleBar(
            content = state.headerContent,
            alignment = state.headerAlignment,
            title = stringResource(Res.string.chat_appearance_sample_title),
            agentName = agentName,
        )
        messages.forEach { message ->
            val name = if (message.isUser) youName else agentName
            if (twoSided) {
                TwoSidedSample(message, name, bodyStyle, state.showBubbles, state.showAvatars)
            } else {
                ThreadSample(message, name, bodyStyle, state.showBubbles, state.showAvatars)
            }
        }
    }
}

/** The chat's text size as a multiple of the body style; the chat screen scales by the same factors. */
private val ChatFontSize.chatScale: Float
    get() = when (this) {
        ChatFontSize.SMALL -> 0.85f
        ChatFontSize.MEDIUM -> 1.0f
        ChatFontSize.LARGE -> 1.2f
    }

private class SampleMessage(val isUser: Boolean, val text: String)

/** The drawer button, the title or model chip where the user put it, and the overflow button. */
@Composable
private fun SampleTitleBar(
    content: ChatHeaderContent,
    alignment: ChatHeaderAlignment,
    title: String,
    agentName: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = if (alignment == ChatHeaderAlignment.CENTER) Alignment.Center else Alignment.CenterStart,
        ) {
            val chipText = when (content) {
                ChatHeaderContent.TITLE -> title
                ChatHeaderContent.MODEL -> agentName
                ChatHeaderContent.NONE -> null
            }
            if (chipText != null) {
                Text(
                    text = chipText,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .then(if (alignment == ChatHeaderAlignment.FILL) Modifier.fillMaxWidth() else Modifier)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ThreadSample(
    message: SampleMessage,
    name: String,
    bodyStyle: TextStyle,
    showBubbles: Boolean,
    showAvatars: Boolean,
) {
    val bubble = if (showBubbles) bubbleColor(message.isUser) else null
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showAvatars) {
                SampleAvatar(message.isUser)
                Spacer(modifier = Modifier.width(8.dp))
            }
            SampleName(
                name = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .padding(start = if (showAvatars) 36.dp else 0.dp)
                .then(if (bubble != null) Modifier.background(bubble, themeShapes().bubble).padding(12.dp) else Modifier),
        ) {
            Text(text = message.text, style = bodyStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TwoSidedSample(
    message: SampleMessage,
    name: String,
    bodyStyle: TextStyle,
    showBubbles: Boolean,
    showAvatars: Boolean,
) {
    val isUser = message.isUser
    val bubble = if (showBubbles) bubbleColor(isUser) else null
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser && showAvatars) {
            SampleAvatar(isUser = false)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (bubble != null) {
                        Modifier.background(bubble, themeShapes().bubble).padding(12.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    },
                ),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            // As in `MessageBubble`, only the name takes the bubble's content colour; the text stays onSurface.
            SampleName(name = name, style = MaterialTheme.typography.labelSmall, color = nameColor(isUser, showBubbles))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = message.text, style = bodyStyle, color = MaterialTheme.colorScheme.onSurface)
        }
        if (isUser && showAvatars) {
            Spacer(modifier = Modifier.width(6.dp))
            SampleAvatar(isUser = true)
        }
    }
}

@Composable
private fun SampleAvatar(isUser: Boolean) {
    if (isUser) {
        AvatarImage(imageUrl = null, showPersonIcon = true, size = AvatarSize, contentDescription = null)
    } else {
        EndpointAvatar(
            imageUrl = null,
            endpointName = EndpointConstants.AGENTS,
            provider = null,
            fallbackText = "",
            size = AvatarSize,
            contentDescription = null,
        )
    }
}

@Composable
private fun SampleName(name: String, style: TextStyle, color: Color) {
    Text(
        text = name,
        style = style.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun bubbleColor(isUser: Boolean): Color =
    if (isUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant

@Composable
private fun nameColor(isUser: Boolean, showBubbles: Boolean): Color = when {
    !showBubbles -> MaterialTheme.colorScheme.onSurface
    isUser -> MaterialTheme.colorScheme.onSecondaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private val SectionGap = 16.dp
private val SwitchesGap = 24.dp
private val AvatarSize = 28.dp

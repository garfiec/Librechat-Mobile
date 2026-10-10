package com.garfiec.librechat.feature.settings.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.EModelEndpoint
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptivePillChoice
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AvatarImage
import com.garfiec.librechat.core.ui.components.BubbleShape
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.components.endpointIconPainter
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.ChatLayoutSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val LayoutOptions = listOf(ChatLayoutConstants.THREAD, ChatLayoutConstants.TWO_SIDED)

/**
 * Settings → Chat → Chat layout: a preview of a short conversation on top, drawn the way the chat
 * lays messages out, and the controls that change it below.
 */
@Composable
fun ChatLayoutSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatLayoutSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AdaptiveScaffold(
        modifier = modifier,
        // Matches the grouped page, so the bar and system-bar insets share its colour.
        containerColor = GlassControlColors.groupedBackground,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.chat_layout)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        AdaptiveGroupedPage(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // No section headers here, which is where grouped sections get their spacing elsewhere.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = SectionGap),
            ) {
                adaptiveSection {
                    row(key = "preview") { ChatLayoutPreview(state) }
                }
                // On the page rather than in a cell: a cell clips to its rounded shape, and the glass
                // thumb's swell overhangs the control further than a cell's padding.
                item(key = "layout") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = SectionGap),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AdaptivePillChoice(
                            options = LayoutOptions.map { chatLayoutLabel(it) },
                            selectedIndex = LayoutOptions.indexOf(state.layoutStyle).coerceAtLeast(0),
                            onSelect = { viewModel.setLayoutStyle(LayoutOptions[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                if (state.layoutStyle == ChatLayoutConstants.TWO_SIDED) {
                                    Res.string.chat_layout_two_sided_desc
                                } else {
                                    Res.string.chat_layout_thread_desc
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                adaptiveSection {
                    row(key = "controls") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ToggleRow(
                                title = stringResource(Res.string.show_bubbles),
                                description = stringResource(Res.string.show_bubbles_desc),
                                checked = state.showBubbles,
                                onChange = viewModel::setShowBubbles,
                            )
                            ToggleRow(
                                title = stringResource(Res.string.show_avatars),
                                description = stringResource(Res.string.show_avatars_desc),
                                checked = state.showAvatars,
                                onChange = viewModel::setShowAvatars,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The Chat tab's one-line summary of this page: the layout, then bubbles and avatars when on. */
@Composable
internal fun chatLayoutSummary(state: ChatLayoutSettingsUiState): String = buildList {
    add(chatLayoutLabel(state.layoutStyle))
    if (state.showBubbles) add(stringResource(Res.string.chat_layout_bubbles))
    if (state.showAvatars) add(stringResource(Res.string.chat_layout_avatars))
}.joinToString(" · ")

/**
 * A question and its answer laid out as the chat lays them out, at the chat's own measures, with text
 * drawn as lines so it reads as a conversation without asking to be read. Keep the measures in step
 * with `MessageBubble` in `:feature:chat`.
 */
@Composable
private fun ChatLayoutPreview(state: ChatLayoutSettingsUiState) {
    val twoSided = state.layoutStyle == ChatLayoutConstants.TWO_SIDED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 12.dp)
            .animateContentSize()
            .clearAndSetSemantics {},
    ) {
        SampleMessages.forEach { message ->
            if (twoSided) {
                TwoSidedSample(message, state.showBubbles, state.showAvatars)
            } else {
                ThreadSample(message, state.showBubbles, state.showAvatars)
            }
        }
    }
}

/** One message in the preview: who sent it, and its lines as fractions of the width they can take. */
private class SampleMessage(val isUser: Boolean, val lines: List<Float>)

private val SampleMessages = listOf(
    SampleMessage(isUser = true, lines = listOf(0.9f, 0.5f)),
    SampleMessage(isUser = false, lines = listOf(1f, 0.94f, 0.97f, 0.6f)),
    SampleMessage(isUser = true, lines = listOf(0.7f)),
)

@Composable
private fun ThreadSample(message: SampleMessage, showBubbles: Boolean, showAvatars: Boolean) {
    val bubble = if (showBubbles) bubbleColor(message.isUser) else null
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showAvatars) {
                SampleAvatar(message.isUser)
                Spacer(modifier = Modifier.width(8.dp))
            }
            SampleName(MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .padding(start = if (showAvatars) 36.dp else 0.dp)
                .then(if (bubble != null) Modifier.background(bubble, BubbleShape).padding(12.dp) else Modifier),
        ) {
            SampleLines(message, MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TwoSidedSample(message: SampleMessage, showBubbles: Boolean, showAvatars: Boolean) {
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
                        Modifier.background(bubble, BubbleShape).padding(12.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    },
                ),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            // As in `MessageBubble`, only the name takes the bubble's content colour; the text stays onSurface.
            SampleName(nameColor(isUser, showBubbles), height = 8.dp)
            Spacer(modifier = Modifier.height(4.dp))
            SampleLines(message, MaterialTheme.colorScheme.onSurface)
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
        AvatarImage(
            imageUrl = null,
            fallbackIconPainter = endpointIconPainter(EModelEndpoint.AGENTS),
            tintIcon = true,
            size = AvatarSize,
            contentDescription = null,
        )
    }
}

@Composable
private fun SampleName(color: Color, height: Dp = 10.dp) {
    Box(
        modifier = Modifier
            .width(NameWidth)
            .height(height)
            .background(color.copy(alpha = NAME_ALPHA), CircleShape),
    )
}

/**
 * The message's text as rounded bars. A user's short message hugs its text, as a real one does in the
 * thread layout, so its lines are fractions of a fixed width rather than of the row.
 */
@Composable
private fun SampleLines(message: SampleMessage, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        message.lines.forEach { width ->
            Box(
                modifier = Modifier
                    .then(if (message.isUser) Modifier.width(UserTextWidth * width) else Modifier.fillMaxWidth(width))
                    .height(10.dp)
                    .background(color.copy(alpha = LINE_ALPHA), CircleShape),
            )
        }
    }
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
private val AvatarSize = 28.dp
private val NameWidth = 64.dp
private val UserTextWidth = 200.dp
private const val NAME_ALPHA = 0.7f
private const val LINE_ALPHA = 0.35f

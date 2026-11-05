package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.model.pullrequest.PullRequestIcon
import com.garfiec.librechat.core.model.usage.percentOf
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestLabel
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestMenuSubtitle
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestSummary
import com.garfiec.librechat.core.ui.components.pullrequest.pullRequestToneColor
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcon
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarRename
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.BarTitleAlignment
import com.garfiec.librechat.core.ui.components.topbar.BarTitleStyle
import com.garfiec.librechat.core.ui.resources.bookmarks
import com.garfiec.librechat.feature.chat.components.veil.LocalModeVeil
import com.garfiec.librechat.feature.chat.components.veil.VeilReaction
import com.garfiec.librechat.feature.chat.components.veil.rememberVeilTarget
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_archive
import com.garfiec.librechat.feature.chat.resources.action_duplicate
import com.garfiec.librechat.feature.chat.resources.action_rename
import com.garfiec.librechat.feature.chat.resources.action_search
import com.garfiec.librechat.feature.chat.resources.action_show_all_media
import com.garfiec.librechat.feature.chat.resources.cancel
import com.garfiec.librechat.feature.chat.resources.cd_more_options
import com.garfiec.librechat.feature.chat.resources.cd_open_drawer
import com.garfiec.librechat.feature.chat.resources.cd_temporary_chat
import com.garfiec.librechat.feature.chat.resources.compact_nudge_menu
import com.garfiec.librechat.feature.chat.resources.compare_models
import com.garfiec.librechat.feature.chat.resources.context_usage_label
import com.garfiec.librechat.feature.chat.resources.delete
import com.garfiec.librechat.feature.chat.resources.dialog_title_rename
import com.garfiec.librechat.feature.chat.resources.load_preset
import com.garfiec.librechat.feature.chat.resources.mcp_app_open_apps
import com.garfiec.librechat.feature.chat.resources.prompts_library
import com.garfiec.librechat.feature.chat.resources.save_as_preset
import com.garfiec.librechat.feature.chat.resources.select_model
import com.garfiec.librechat.feature.chat.resources.temporary_chat_enforced
import com.garfiec.librechat.feature.chat.resources.trace_open
import com.garfiec.librechat.feature.chat.screen.rememberChatModelLabel
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreRes

/**
 * The chat top bar as data, for the Liquid Glass renderers. Mirrors the Material bar's chips: the
 * drawer button, the configurable title/model bubble, the pull request chip on a wide pane
 * ([showPullRequestChip]), the temporary-chat toggle and the overflow menu (from the same
 * [chatOverflowSections] list the Material dropdown renders).
 */
@Composable
internal fun rememberChatTopBarSpec(
    uiState: ChatUiState,
    actions: ChatTopBarActions,
    sections: List<List<ChatOverflowItem>>,
    onItem: (ChatOverflowItem) -> Unit,
    onOpenDrawer: (() -> Unit)?,
    showTempChatToggle: Boolean,
    showPullRequestChip: Boolean = false,
    openApps: Int = 0,
): AdaptiveTopBarSpec {
    // The actions capsule holds the toggle, so the band crosses it where it starts.
    val veil = LocalModeVeil.current
    val reportToggle = remember(veil) { veil?.let { v -> { bounds: Rect -> v.reportOrigin(bounds.center) } } }
    val toggleAnchor = remember(veil) { veil?.let { v -> { v.resolvedOrigin } } }
    val capsuleVeil = rememberVeilTarget(
        isTemporary = uiState.isTemporaryChat,
        reaction = VeilReaction.NUDGE,
        anchor = toggleAnchor,
    )
    val navigation = onOpenDrawer?.let {
        BarNavigation(BarIcons.Menu, stringResource(Res.string.cd_open_drawer), it)
    }

    val alignment = when (uiState.chatHeaderAlignment) {
        ChatHeaderAlignment.LEFT -> BarTitleAlignment.LEADING
        ChatHeaderAlignment.CENTER -> BarTitleAlignment.CENTER
        ChatHeaderAlignment.FILL -> BarTitleAlignment.FILL
    }
    val conversationId = uiState.conversationId
    val conversationTitle = uiState.conversationTitle
    val title = when (uiState.chatHeaderContent) {
        ChatHeaderContent.TITLE ->
            if (conversationId != null && !conversationTitle.isNullOrBlank()) {
                BarTitle(
                    text = conversationTitle,
                    alignment = alignment,
                    rename = BarRename(
                        dialogTitle = stringResource(Res.string.dialog_title_rename),
                        initial = conversationTitle,
                        confirmLabel = stringResource(Res.string.action_rename),
                        cancelLabel = stringResource(Res.string.cancel),
                        onCommit = actions.onRename,
                    ),
                )
            } else {
                BarTitle(text = null, alignment = alignment)
            }

        ChatHeaderContent.MODEL -> {
            val label = rememberChatModelLabel(
                selectedEndpoint = uiState.selectedEndpoint,
                selectedModel = uiState.selectedModel,
                agents = uiState.agents,
                selectedSpec = uiState.selectedSpec,
                modelSpecs = uiState.modelSpecs,
            )
            BarTitle(
                text = label.displayModel ?: stringResource(Res.string.select_model),
                style = BarTitleStyle.CHIP,
                alignment = alignment,
                onClick = actions.onOpenModelSheet.takeIf { uiState.modelPickerShown },
            )
        }

        ChatHeaderContent.NONE -> BarTitle(text = null, alignment = alignment)
    }

    val menuSections = sections.map { section ->
        BarMenuSection(section.mapNotNull { item -> overflowMenuItem(item, uiState, openApps) { onItem(item) } })
    }

    val pullRequest = uiState.pullRequest.pullRequest
    val pullRequestLook = uiState.pullRequest.presentation
    val pullRequestChip = if (showPullRequestChip && pullRequest != null && pullRequestLook != null) {
        val glyph = BarIcons.pullRequest(pullRequestLook.icon)
        BarAction.Text(
            id = "pull_request",
            label = pullRequestNumberLabel(pullRequest, pullRequestLook),
            compactIcon = glyph,
            leadingIcon = glyph,
            iconTint = pullRequestToneColor(pullRequestLook.iconTone),
            badgeTint = pullRequestLook.dotTone?.let { pullRequestToneColor(it) },
            // The native bar draws no dot: the summary says the state and checks in words.
            accessibilityLabel = pullRequestSummary(pullRequest, pullRequestLook),
            onClick = actions.onOpenPullRequest,
        )
    } else {
        null
    }

    val actions = buildList {
        pullRequestChip?.let(::add)
        if (showTempChatToggle) {
            add(
                BarAction.Toggle(
                    id = "temporary_chat",
                    iconOff = BarIcons.Visible,
                    iconOn = BarIcons.Hidden,
                    label = stringResource(
                        if (uiState.temporaryChatEnforced) Res.string.temporary_chat_enforced else Res.string.cd_temporary_chat,
                    ),
                    checked = uiState.isTemporaryChat,
                    onCheckedChange = { actions.onToggleTemporaryChat() },
                    checkedTint = ChatInputDefaults.temporaryAccent,
                    onAnchor = reportToggle,
                ),
            )
        }
        add(
            BarAction.Menu(
                id = "overflow",
                icon = BarIcons.More,
                label = stringResource(Res.string.cd_more_options),
                sections = menuSections,
            ),
        )
    }

    return AdaptiveTopBarSpec(navigation = navigation, title = title, actions = actions, actionsFlare = capsuleVeil.flare)
}

@Composable
private fun overflowMenuItem(item: ChatOverflowItem, uiState: ChatUiState, openApps: Int, onClick: () -> Unit): BarMenuItem? {
    fun entry(
        label: String,
        icon: BarIcon,
        checked: Boolean = false,
        destructive: Boolean = false,
        subtitle: String? = null,
        enabled: Boolean = true,
    ) = BarMenuItem(item.name, label, icon, onClick, checked = checked, destructive = destructive, enabled = enabled, subtitle = subtitle)

    return when (item) {
        ChatOverflowItem.OPEN_APPS -> entry(stringResource(Res.string.mcp_app_open_apps, openApps), BarIcons.Apps)
        ChatOverflowItem.SEARCH -> entry(stringResource(Res.string.action_search), BarIcons.Search)
        ChatOverflowItem.SHOW_ALL_MEDIA -> entry(stringResource(Res.string.action_show_all_media), BarIcons.Media)
        ChatOverflowItem.LOAD_PRESET -> entry(stringResource(Res.string.load_preset), BarIcons.LoadPreset)
        ChatOverflowItem.SAVE_PRESET -> entry(stringResource(Res.string.save_as_preset), BarIcons.SavePreset)
        ChatOverflowItem.PROMPTS_LIBRARY -> entry(stringResource(Res.string.prompts_library), BarIcons.Prompts)
        ChatOverflowItem.COMPARE ->
            entry(
                stringResource(Res.string.compare_models),
                BarIcons.Compare,
                // Checked through setup too: tapping it then cancels the setup, as it would stop a comparison.
                checked = uiState.comparisonState.isEnabled || uiState.comparisonState.isSettingUp,
                enabled = uiState.canToggleComparison,
            )
        ChatOverflowItem.TRACE -> entry(stringResource(Res.string.trace_open), BarIcons.Trace)
        ChatOverflowItem.CONTEXT_USAGE -> entry(
            stringResource(Res.string.context_usage_label),
            BarIcons.ContextUsage,
            subtitle = uiState.contextUsage?.let {
                percentLabel(percentOf(it.usedTokens, it.windowTokens), uiState.contextUsageIsEstimate)
            },
        )
        ChatOverflowItem.COMPACT -> entry(stringResource(Res.string.compact_nudge_menu), BarIcons.Compact)
        // The native menu draws no CI dot: the subtitle says the state and checks in words.
        ChatOverflowItem.PULL_REQUEST -> uiState.pullRequest.pullRequest?.let { pr ->
            val presentation = uiState.pullRequest.presentation ?: return null
            entry(
                pullRequestLabel(pr, presentation),
                if (presentation.icon == PullRequestIcon.MERGED) BarIcons.PullRequestMerged else BarIcons.PullRequest,
                subtitle = pullRequestMenuSubtitle(presentation),
            )
        }
        ChatOverflowItem.SHARE -> entry(shareItemLabel(uiState.shareLinkActive), BarIcons.Share)
        // The filled glyph, not `checked`: a checked row draws a tick, and this one opens a checklist.
        ChatOverflowItem.BOOKMARKS -> entry(
            stringResource(CoreRes.string.bookmarks),
            if (uiState.hasBookmark) BarIcons.BookmarkFilled else BarIcons.Bookmark,
        )
        ChatOverflowItem.RENAME -> entry(stringResource(Res.string.action_rename), BarIcons.Edit)
        ChatOverflowItem.DUPLICATE -> entry(stringResource(Res.string.action_duplicate), BarIcons.Duplicate)
        ChatOverflowItem.ARCHIVE -> entry(stringResource(Res.string.action_archive), BarIcons.Archive)
        ChatOverflowItem.DELETE -> entry(stringResource(Res.string.delete), BarIcons.Delete, destructive = true)
    }
}

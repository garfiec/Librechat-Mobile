package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
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
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_archive
import com.garfiec.librechat.feature.chat.resources.action_duplicate
import com.garfiec.librechat.feature.chat.resources.action_rename
import com.garfiec.librechat.feature.chat.resources.action_search
import com.garfiec.librechat.feature.chat.resources.action_share
import com.garfiec.librechat.feature.chat.resources.action_show_all_media
import com.garfiec.librechat.feature.chat.resources.cancel
import com.garfiec.librechat.feature.chat.resources.cd_more_options
import com.garfiec.librechat.feature.chat.resources.cd_open_drawer
import com.garfiec.librechat.feature.chat.resources.cd_temporary_chat
import com.garfiec.librechat.feature.chat.resources.compare_models
import com.garfiec.librechat.feature.chat.resources.context_usage_label
import com.garfiec.librechat.feature.chat.resources.delete
import com.garfiec.librechat.feature.chat.resources.dialog_title_rename
import com.garfiec.librechat.feature.chat.resources.load_preset
import com.garfiec.librechat.feature.chat.resources.prompts_library
import com.garfiec.librechat.feature.chat.resources.save_as_preset
import com.garfiec.librechat.feature.chat.resources.select_model
import com.garfiec.librechat.feature.chat.resources.trace_open
import com.garfiec.librechat.feature.chat.screen.rememberChatModelLabel
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import org.jetbrains.compose.resources.stringResource

/**
 * The chat top bar as data, for the Liquid Glass renderers. Mirrors the Material bar's chips: the
 * drawer button, the configurable title/model bubble, the temporary-chat toggle and the overflow menu
 * (from the same [chatOverflowSections] list the Material dropdown renders).
 */
@Composable
internal fun rememberChatTopBarSpec(
    uiState: ChatUiState,
    actions: ChatTopBarActions,
    sections: List<List<ChatOverflowItem>>,
    onItem: (ChatOverflowItem) -> Unit,
    onOpenDrawer: (() -> Unit)?,
    showTempChatToggle: Boolean,
): AdaptiveTopBarSpec {
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
            )
            BarTitle(
                text = label.displayModel ?: stringResource(Res.string.select_model),
                style = BarTitleStyle.CHIP,
                alignment = alignment,
                onClick = actions.onOpenModelSheet,
            )
        }

        ChatHeaderContent.NONE -> BarTitle(text = null, alignment = alignment)
    }

    val menuSections = sections.map { section ->
        BarMenuSection(section.map { item -> overflowMenuItem(item, uiState) { onItem(item) } })
    }

    val actions = buildList {
        if (showTempChatToggle) {
            add(
                BarAction.Toggle(
                    id = "temporary_chat",
                    iconOff = BarIcons.Visible,
                    iconOn = BarIcons.Hidden,
                    label = stringResource(Res.string.cd_temporary_chat),
                    checked = uiState.isTemporaryChat,
                    onCheckedChange = { actions.onToggleTemporaryChat() },
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

    return AdaptiveTopBarSpec(navigation = navigation, title = title, actions = actions)
}

@Composable
private fun overflowMenuItem(item: ChatOverflowItem, uiState: ChatUiState, onClick: () -> Unit): BarMenuItem {
    fun entry(label: String, icon: BarIcon, checked: Boolean = false, destructive: Boolean = false, subtitle: String? = null) =
        BarMenuItem(item.name, label, icon, onClick, checked = checked, destructive = destructive, subtitle = subtitle)

    return when (item) {
        ChatOverflowItem.SEARCH -> entry(stringResource(Res.string.action_search), BarIcons.Search)
        ChatOverflowItem.SHOW_ALL_MEDIA -> entry(stringResource(Res.string.action_show_all_media), BarIcons.Media)
        ChatOverflowItem.LOAD_PRESET -> entry(stringResource(Res.string.load_preset), BarIcons.LoadPreset)
        ChatOverflowItem.SAVE_PRESET -> entry(stringResource(Res.string.save_as_preset), BarIcons.SavePreset)
        ChatOverflowItem.PROMPTS_LIBRARY -> entry(stringResource(Res.string.prompts_library), BarIcons.Prompts)
        ChatOverflowItem.COMPARE ->
            entry(stringResource(Res.string.compare_models), BarIcons.Compare, checked = uiState.comparisonState.isEnabled)
        ChatOverflowItem.TRACE -> entry(stringResource(Res.string.trace_open), BarIcons.Trace)
        ChatOverflowItem.CONTEXT_USAGE -> entry(
            stringResource(Res.string.context_usage_label),
            BarIcons.ContextUsage,
            subtitle = uiState.contextUsage?.let { "${(it.usedFraction * 100).toInt()}%" },
        )
        ChatOverflowItem.SHARE -> entry(stringResource(Res.string.action_share), BarIcons.Share)
        ChatOverflowItem.RENAME -> entry(stringResource(Res.string.action_rename), BarIcons.Edit)
        ChatOverflowItem.DUPLICATE -> entry(stringResource(Res.string.action_duplicate), BarIcons.Duplicate)
        ChatOverflowItem.ARCHIVE -> entry(stringResource(Res.string.action_archive), BarIcons.Archive)
        ChatOverflowItem.DELETE -> entry(stringResource(Res.string.delete), BarIcons.Delete, destructive = true)
    }
}

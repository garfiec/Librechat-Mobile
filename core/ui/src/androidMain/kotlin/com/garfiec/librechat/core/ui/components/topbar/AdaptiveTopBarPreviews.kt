package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The bars of the screens whose titles run out of room first, rebuilt from their specs with each
// screen's longest translation quoted as a literal (those strings live in the feature modules).

@PreviewNarrowScreens
@Composable
private fun TextActionBarsMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { BarStack(textActionBars) }

@PreviewNarrowScreens
@Composable
private fun TextActionBarsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { BarStack(textActionBars) }

@PreviewNarrowScreens
@Composable
private fun FilesBarsMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { BarStack(filesBars) }

@PreviewNarrowScreens
@Composable
private fun FilesBarsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { BarStack(filesBars) }

@PreviewNarrowScreens
@Composable
private fun FixedTitleBarsMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { BarStack(fixedTitleBars) }

@PreviewNarrowScreens
@Composable
private fun FixedTitleBarsGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { BarStack(fixedTitleBars) }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        // The glass bar takes its text colour from the screen, as it does under a scaffold.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

@Composable
private fun BarStack(specs: List<Pair<String, AdaptiveTopBarSpec>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        specs.forEach { (_, spec) -> AdaptiveTopBar(spec) }
    }
}

private val back = BarNavigation(BarIcons.Back, "Back") {}
private val close = BarNavigation(BarIcons.Close, "Close") {}

private fun textActionBar(title: String, label: String, icon: BarIcon = BarIcons.Confirm) = AdaptiveTopBarSpec(
    navigation = back,
    title = BarTitle(title),
    actions = listOf(BarAction.Text(id = "text", label = label, compactIcon = icon, onClick = {})),
)

/** Provider API Keys with a key set, and the schedule and skill editors in edit mode. */
private val textActionBars = listOf(
    "provider-keys-en" to textActionBar("Provider API Keys", "Revoke All", BarIcons.ClearAll),
    "provider-keys-de" to textActionBar("Anbieter-API-Schlüssel", "Alle widerrufen", BarIcons.ClearAll),
    "provider-keys-es" to textActionBar("Claves de API de proveedores", "Revocar todo", BarIcons.ClearAll),
    "schedule-editor-fr" to textActionBar("Modifier la planification", "Enregistrer"),
    "skill-editor-fr" to textActionBar("Modifier la compétence", "Enregistrer"),
)

private fun filesPicker(title: String, attach: String, count: Int) = AdaptiveTopBarSpec(
    navigation = close,
    title = BarTitle(title),
    actions = listOf(
        BarAction.Icon(id = "select_all", icon = BarIcons.SelectAll, label = "Select all", onClick = {}),
        BarAction.Icon(id = "view_mode", icon = BarIcons.GridView, label = "Switch to grid", onClick = {}),
        BarAction.Text(id = "attach", label = attach, compactIcon = BarIcons.Attach, enabled = count > 0, onClick = {}),
    ),
)

private fun filesSelection(title: String, done: String) = AdaptiveTopBarSpec(
    navigation = close,
    title = BarTitle(title),
    actions = listOf(
        BarAction.Icon(id = "select_all", icon = BarIcons.SelectAll, label = "Select all", onClick = {}),
        BarAction.Icon(id = "delete", icon = BarIcons.DeleteFilled, label = "Delete", tint = BarTint.DESTRUCTIVE, onClick = {}),
        BarAction.Text(id = "done", label = done, compactIcon = BarIcons.Confirm, onClick = {}),
    ),
)

/** The Files picker (nothing and three selected) and selection mode, as FilesScreen builds them. */
private val filesBars = listOf(
    "files-picker-empty-en" to filesPicker("Select files", "Attach (0)", 0),
    "files-picker-3-en" to filesPicker("3 selected", "Attach (3)", 3),
    "files-picker-3-ru" to filesPicker("Выбрано: 3", "Прикрепить (3)", 3),
    "files-selection-12-en" to filesSelection("12 selected", "Done"),
)

private fun fixedTitle(title: String, actions: List<BarAction> = emptyList()) =
    AdaptiveTopBarSpec(navigation = back, title = BarTitle(title), actions = actions)

private val agentEditorMenu = BarAction.Menu(
    id = "more",
    icon = BarIcons.More,
    label = "More options",
    sections = listOf(BarMenuSection(listOf(BarMenuItem(id = "delete", label = "Delete", icon = BarIcons.DeleteFilled, onClick = {})))),
)

/**
 * Fixed screen titles with no text action, the titles that are content (an agent's name, a file
 * name kept on one line), and the chat header: a tappable chip that has to stay on one line however
 * long the conversation's name.
 */
private val fixedTitleBars = listOf(
    "agent-detail-en" to fixedTitle("Research assistant for quarterly market reports", listOf(agentEditorMenu)),
    "file-preview-en" to AdaptiveTopBarSpec(
        navigation = close,
        title = BarTitle("Screenshot_20261017-142233_Switchboard_export.png", maxLines = 1),
    ),
    "two-factor-en" to fixedTitle("Two-Factor Authentication"),
    "schedules-ja" to fixedTitle("スケジュールされたチャット"),
    "agent-editor-ja" to fixedTitle("エージェントを編集", listOf(agentEditorMenu)),
    "artifact-shortcuts-es" to fixedTitle("Accesos directos en la pantalla de inicio"),
    "prefetch-pt" to fixedTitle("Pré-carregamento em segundo plano"),
    "reset-password-fr" to fixedTitle("Réinitialiser le mot de passe"),
    "archived-de" to fixedTitle("Archivierte Unterhaltungen"),
    "chat-header-en" to AdaptiveTopBarSpec(
        navigation = BarNavigation(BarIcons.Menu, "Menu") {},
        title = BarTitle(
            text = "A conversation whose name runs well past the bar",
            style = BarTitleStyle.CHIP,
            alignment = BarTitleAlignment.CENTER,
            onClick = {},
        ),
        actions = listOf(agentEditorMenu),
    ),
)

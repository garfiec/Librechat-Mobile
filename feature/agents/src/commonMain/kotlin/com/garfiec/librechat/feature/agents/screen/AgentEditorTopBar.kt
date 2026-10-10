package com.garfiec.librechat.feature.agents.screen

import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarMenuItem
import com.garfiec.librechat.core.ui.components.topbar.BarMenuSection
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.feature.agents.resources.*
import com.garfiec.librechat.feature.agents.resources.Res
import org.jetbrains.compose.resources.stringResource

/** Editor top bar: title, back, and an edit-mode overflow (duplicate / version history / delete). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentEditorTopBar(
    isEditMode: Boolean,
    onBack: () -> Unit,
    onDuplicate: () -> Unit,
    onVersionHistory: () -> Unit,
    onDelete: () -> Unit,
) {
    AdaptiveTopBar(
        spec = AdaptiveTopBarSpec(
            navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onBack),
            title = BarTitle(if (isEditMode) stringResource(Res.string.edit_agent) else stringResource(Res.string.create_agent)),
            actions = if (isEditMode) {
                listOf(
                    BarAction.Menu(
                        id = "more",
                        icon = BarIcons.More,
                        label = stringResource(Res.string.cd_more_options),
                        sections = listOf(
                            BarMenuSection(
                                listOf(
                                    BarMenuItem(
                                        id = "duplicate",
                                        label = stringResource(Res.string.duplicate),
                                        icon = BarIcons.DuplicateFilled,
                                        onClick = onDuplicate,
                                    ),
                                    BarMenuItem(
                                        id = "history",
                                        label = stringResource(Res.string.version_history),
                                        icon = BarIcons.History,
                                        onClick = onVersionHistory,
                                    ),
                                ),
                            ),
                            BarMenuSection(
                                listOf(
                                    BarMenuItem(
                                        id = "delete",
                                        label = stringResource(Res.string.delete),
                                        icon = BarIcons.DeleteFilled,
                                        destructive = true,
                                        onClick = onDelete,
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            } else {
                emptyList()
            },
        ),
        materialStyle = MaterialBarStyle(containerColor = MaterialTheme.colorScheme.surface),
    )
}

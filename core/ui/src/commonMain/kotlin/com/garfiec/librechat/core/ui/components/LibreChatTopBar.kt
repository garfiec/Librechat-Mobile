package com.garfiec.librechat.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarAction
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.BarTitleAlignment
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.topbar_back
import com.garfiec.librechat.core.ui.resources.topbar_menu
import org.jetbrains.compose.resources.stringResource

/** A centred-title top bar with a back or menu button, in the current interface style. */
@Composable
fun LibreChatTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: List<BarAction> = emptyList(),
) {
    val navigation = when {
        onNavigateBack != null -> BarNavigation(BarIcons.Back, stringResource(Res.string.topbar_back), onNavigateBack)
        onMenuClick != null -> BarNavigation(BarIcons.Menu, stringResource(Res.string.topbar_menu), onMenuClick)
        else -> null
    }
    AdaptiveTopBar(
        spec = AdaptiveTopBarSpec(
            navigation = navigation,
            title = BarTitle(text = title, alignment = BarTitleAlignment.CENTER),
            actions = actions,
        ),
        modifier = modifier,
        materialStyle = MaterialBarStyle(containerColor = MaterialTheme.colorScheme.surface),
    )
}

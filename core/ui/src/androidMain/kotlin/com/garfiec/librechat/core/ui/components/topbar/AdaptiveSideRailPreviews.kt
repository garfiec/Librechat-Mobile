package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.platform.BarSide
import com.garfiec.librechat.core.ui.platform.LocalWindowFeatures
import com.garfiec.librechat.core.ui.platform.VerticalBars
import com.garfiec.librechat.core.ui.platform.WindowFeatures
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The iPhone Duo's cover screen: an 84pt column on the right whose top 174pt holds the camera and
// status bar. Previews have no safe area, so the body pads the column itself.
private val coverBars = VerticalBars(side = BarSide.RIGHT, columnWidth = 84.dp, systemZoneHeight = 174.dp)

private val tasks = BarAction.Icon(
    id = "tasks",
    icon = BarIcons.History,
    label = "Background tasks: 2 running",
    badge = "2",
    onClick = {},
)

/** Six actions, a text action and a menu: more than a short column holds, so the last ones overflow. */
private val busySpec = AdaptiveTopBarSpec(
    navigation = BarNavigation(BarIcons.Back, "Back") {},
    title = BarTitle("Research assistant for quarterly market reports"),
    actions = listOf(
        BarAction.Icon(id = "edit", icon = BarIcons.EditFilled, label = "Edit", onClick = {}),
        BarAction.Icon(id = "share", icon = BarIcons.ShareFilled, label = "Share", onClick = {}),
        BarAction.Toggle(
            id = "star",
            iconOff = BarIcons.Star,
            iconOn = BarIcons.StarFilled,
            label = "Favourite",
            checked = true,
            onCheckedChange = {},
        ),
        BarAction.Icon(id = "duplicate", icon = BarIcons.DuplicateFilled, label = "Duplicate", onClick = {}),
        BarAction.Text(id = "save", label = "Speichern", compactIcon = BarIcons.Confirm, onClick = {}),
        BarAction.Menu(
            id = "more",
            icon = BarIcons.More,
            label = "More options",
            sections = listOf(
                BarMenuSection(listOf(BarMenuItem(id = "delete", label = "Delete", icon = BarIcons.DeleteFilled, onClick = {}))),
            ),
        ),
    ),
    bottomActions = listOf(tasks),
)

@PreviewNarrowScreens
@Composable
private fun SideRailMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { RailColumns() }

@PreviewNarrowScreens
@Composable
private fun SideRailGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { RailColumns() }

@PreviewNarrowScreens
@Composable
private fun RailScaffoldMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { RailScaffold() }

@PreviewNarrowScreens
@Composable
private fun RailScaffoldGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { RailScaffold() }

/** The rail at a cover-screen height, and in a column short enough that actions overflow into ⋯. */
@Composable
private fun RailColumns() {
    Box(Modifier.fillMaxWidth().height(678.dp).background(MaterialTheme.colorScheme.background)) {
        AdaptiveSideRail(spec = busySpec, bars = coverBars, modifier = Modifier.align(AbsoluteAlignment.TopRight))
        AdaptiveSideRail(
            spec = busySpec,
            bars = coverBars.copy(systemZoneHeight = 0.dp),
            modifier = Modifier.align(AbsoluteAlignment.TopLeft).height(360.dp),
        )
    }
}

/** A screen in rail mode: its title is a heading in the body, its controls in the column. */
@Composable
private fun RailScaffold() {
    CompositionLocalProvider(LocalWindowFeatures provides WindowFeatures(verticalBars = coverBars)) {
        Box(Modifier.fillMaxWidth().height(678.dp)) {
            AdaptiveScaffold(
                topBar = { AdaptiveTopBar(busySpec) },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxHeight()
                        .padding(padding)
                        .absolutePadding(left = 16.dp, right = coverBars.columnWidth + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(6) {
                        Text("Card text that runs up to the rail and stops short of it", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, content = content)
    }
}

package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ArtifactShortcut
import com.garfiec.librechat.core.model.displayGlyph
import com.garfiec.librechat.core.model.displayLabel
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.artifactTypeLabel
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.artifact_shortcuts_empty
import com.garfiec.librechat.feature.settings.resources.artifact_shortcuts_title
import com.garfiec.librechat.feature.settings.resources.cd_artifact_shortcut_delete
import com.garfiec.librechat.feature.settings.resources.cd_back
import org.jetbrains.compose.resources.stringResource

/** The pinned-artifact list for [shortcuts]; [onDelete] removes one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtifactShortcutsContent(
    shortcuts: List<ArtifactShortcut>,
    onDelete: (ArtifactShortcut) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveScaffold(
        modifier = modifier,
        topBar = {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.cd_back), onNavigateBack),
                    title = BarTitle(stringResource(Res.string.artifact_shortcuts_title)),
                    actions = emptyList(),
                ),
            )
        },
    ) { innerPadding ->
        if (shortcuts.isEmpty()) {
            EmptyState(
                title = stringResource(Res.string.artifact_shortcuts_empty),
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                items(shortcuts, key = { it.id }) { shortcut ->
                    ArtifactShortcutRow(
                        shortcut = shortcut,
                        onDelete = { onDelete(shortcut) },
                    )
                    AdaptiveDivider()
                }
            }
        }
    }
}

@Composable
internal fun ArtifactShortcutRow(
    shortcut: ArtifactShortcut,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Sized in dp, as the box is: at a large font scale a 22sp glyph outgrows its 40dp box.
            Text(
                text = shortcut.displayGlyph,
                fontSize = with(LocalDensity.current) { GlyphSize.toSp() },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortcut.displayLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artifactTypeLabel(shortcut.type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(Res.string.cd_artifact_shortcut_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val GlyphSize = 22.dp

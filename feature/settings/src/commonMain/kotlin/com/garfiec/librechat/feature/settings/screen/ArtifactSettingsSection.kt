package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.ArtifactDisplayPrefs
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.ui.artifacts.artifactsModeName
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.resources.artifacts_mode_title
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreUiRes

@Composable
internal fun ArtifactSettingsSection(
    displayPrefs: ArtifactDisplayPrefs,
    inlineArtifactSummary: String,
    librariesSummary: String,
    artifactsMode: ArtifactsMode,
    onOpenArtifactViewerDialog: () -> Unit,
    onOpenRenderInlineDialog: () -> Unit,
    onOpenLibrariesDialog: () -> Unit,
    onOpenArtifactsModeDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectorRow(
                title = stringResource(Res.string.artifact_viewer_title),
                value = artifactDisplayModeLabel(displayPrefs.mode),
                onClick = onOpenArtifactViewerDialog,
            )

            SelectorRow(
                title = stringResource(Res.string.artifact_inline_title),
                value = inlineArtifactSummary,
                onClick = onOpenRenderInlineDialog,
            )

            SelectorRow(
                title = stringResource(Res.string.artifact_libraries_title),
                value = librariesSummary,
                onClick = onOpenLibrariesDialog,
            )

            SelectorRow(
                title = stringResource(CoreUiRes.string.artifacts_mode_title),
                value = artifactsModeName(artifactsMode),
                onClick = onOpenArtifactsModeDialog,
            )
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

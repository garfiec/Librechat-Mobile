package com.garfiec.librechat.core.ui.artifacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ArtifactsMode
import com.garfiec.librechat.core.ui.components.AdaptiveRadioButton
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.artifacts_mode_custom
import com.garfiec.librechat.core.ui.resources.artifacts_mode_custom_desc
import com.garfiec.librechat.core.ui.resources.artifacts_mode_default
import com.garfiec.librechat.core.ui.resources.artifacts_mode_default_desc
import com.garfiec.librechat.core.ui.resources.artifacts_mode_shadcnui
import com.garfiec.librechat.core.ui.resources.artifacts_mode_shadcnui_desc
import org.jetbrains.compose.resources.stringResource

@Composable
fun artifactsModeName(mode: ArtifactsMode): String = stringResource(
    when (mode) {
        ArtifactsMode.DEFAULT -> Res.string.artifacts_mode_default
        ArtifactsMode.SHADCN_UI -> Res.string.artifacts_mode_shadcnui
        ArtifactsMode.CUSTOM -> Res.string.artifacts_mode_custom
    },
)

@Composable
fun artifactsModeDescription(mode: ArtifactsMode): String = stringResource(
    when (mode) {
        ArtifactsMode.DEFAULT -> Res.string.artifacts_mode_default_desc
        ArtifactsMode.SHADCN_UI -> Res.string.artifacts_mode_shadcnui_desc
        ArtifactsMode.CUSTOM -> Res.string.artifacts_mode_custom_desc
    },
)

/** The three Artifacts modes as radio rows, each with a plain-language line on what it does. */
@Composable
fun ArtifactsModeChoices(
    selected: ArtifactsMode,
    onSelect: (ArtifactsMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ArtifactsMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(selected = mode == selected, onClick = { onSelect(mode) }, role = Role.RadioButton)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AdaptiveRadioButton(selected = mode == selected, onClick = null)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = artifactsModeName(mode), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = artifactsModeDescription(mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.ListDragSelection
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.dragSelectExclude
import com.garfiec.librechat.core.ui.components.dragSelectRow
import com.garfiec.librechat.core.ui.components.listDragSelection
import com.garfiec.librechat.core.ui.components.rememberListDragSelection
import com.garfiec.librechat.feature.chat.model.PresetDisplayData
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetPicker(
    presets: List<PresetDisplayData>,
    onPresetSelect: (PresetDisplayData) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onEditPreset: (PresetDisplayData) -> Unit = {},
    onDeletePreset: (PresetDisplayData) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        val dragSelection = rememberListDragSelection()
        // The rows run edge to edge and pad themselves, so the drag highlight has room around their text.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .listDragSelection(dragSelection, holdOnly = true),
        ) {
            Text(
                text = stringResource(Res.string.select_preset),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )

            if (presets.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_presets_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(presets, key = { it.presetId ?: it.title }, contentType = { "preset" }) { preset ->
                        PresetItem(
                            preset = preset,
                            onClick = { onPresetSelect(preset) },
                            onEdit = { onEditPreset(preset) },
                            onDelete = { onDeletePreset(preset) },
                            dragSelection = dragSelection,
                        )
                        AdaptiveDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PresetItem(
    preset: PresetDisplayData,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragSelection: ListDragSelection,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dragSelectRow(dragSelection, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                val endpointLabel = preset.endpointLabel
                if (endpointLabel != null) {
                    Text(
                        text = endpointLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val model = preset.model
                if (model != null) {
                    if (endpointLabel != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = model,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        IconButton(
            onClick = onEdit,
            modifier = Modifier.size(36.dp).dragSelectExclude(dragSelection),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(Res.string.cd_edit_preset, preset.title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp).dragSelectExclude(dragSelection),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(Res.string.cd_delete_preset, preset.title),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

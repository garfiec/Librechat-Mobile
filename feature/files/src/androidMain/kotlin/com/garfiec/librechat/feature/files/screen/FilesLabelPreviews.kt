package com.garfiec.librechat.feature.files.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.files.viewmodel.FileTypeFilter

// The filter tabs over an empty Images tab, and the multi-delete dialog's title for three files.

@PreviewNarrowScreens
@Composable
private fun FilesLabelsMaterialPreview() = FilesLabelsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun FilesLabelsGlassPreview() = FilesLabelsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun FilesLabelsHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            Column {
                var filter by remember { mutableStateOf(FileTypeFilter.IMAGES) }
                FileTypeFilterTabs(selected = filter, onSelect = { filter = it })
                Box(Modifier.height(200.dp)) {
                    FilesEmptyState(filter)
                }
                Text(
                    text = deleteFilesTitle(3),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

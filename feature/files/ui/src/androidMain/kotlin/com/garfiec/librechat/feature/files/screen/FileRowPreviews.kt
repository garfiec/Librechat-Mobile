package com.garfiec.librechat.feature.files.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.files.FileDisplayData
import com.garfiec.librechat.feature.files.FilePreviewDisplayData

// The file preview's details card and the Files grid at the widths that squeeze them.

@PreviewNarrowScreens
@Composable
private fun FileInfoCardPreview() = PreviewSurface(UiStyle.MATERIAL) { FileInfoCardHost() }

@PreviewNarrowScreens
@Composable
private fun FileGridPreview() = PreviewSurface(UiStyle.MATERIAL) { FileGridHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ProvideDateTimeFormat(DateTimeFormatPrefs(), content)
        }
    }
}

@Composable
private fun FileInfoCardHost() {
    FileInfoCard(
        file = FilePreviewDisplayData(
            fileId = "f1",
            filename = "Quarterly report Q3 2026.docx",
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            formattedSize = "1.2 MB",
            createdAt = "2026-10-17T15:45:00Z",
            source = "local",
            userId = null,
        ),
    )
}

/** The grid as the Files screen lays it out. */
@Composable
private fun FileGridHost() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = FileGridCellMinSize),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(gridFiles, key = { it.fileId }) { file ->
            FileGridItem(file = file, isSelectionMode = false, isSelected = false, onLongClick = {}, onClick = {})
        }
    }
}

private val gridFiles = listOf(
    FileDisplayData("a", "notes.txt", "text/plain", "4 KB", "2026-10-17T15:45:00Z", previewUrl = null),
    FileDisplayData("b", "report.pdf", "application/pdf", "1.2 MB", "2026-10-16T09:10:00Z", previewUrl = null),
    FileDisplayData("c", "data.json", "application/json", "18 KB", "2026-10-15T12:00:00Z", previewUrl = null),
    FileDisplayData("d", "song.mp3", "audio/mpeg", "3.4 MB", "2026-10-14T08:30:00Z", previewUrl = null),
)

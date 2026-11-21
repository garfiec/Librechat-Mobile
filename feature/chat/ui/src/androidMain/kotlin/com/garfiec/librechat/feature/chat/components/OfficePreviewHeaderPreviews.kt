package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The office preview card's open header. The document under it is a WebView, which previews can't draw.

@PreviewNarrowScreens
@Composable
internal fun OfficePreviewHeaderLightMaterialPreview() = OfficeHeadersPreview(false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
internal fun OfficePreviewHeaderDarkGlassPreview() = OfficeHeadersPreview(true, UiStyle.LIQUID_GLASS)

@Composable
private fun OfficeHeadersPreview(dark: Boolean, style: UiStyle) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                previewOfficeFiles.forEach { OfficePreviewHeader(attachment = it, onOpen = {}) }
            }
        }
    }
}

private val previewOfficeFiles = listOf(
    Attachment(fileId = "o1", filename = "memo.docx", type = "application/vnd.librechat.docx-preview"),
    Attachment(fileId = "o2", filename = "sales.xlsx", type = "application/vnd.librechat.spreadsheet-preview"),
    Attachment(
        fileId = "o3",
        filename = "quarterly_revenue_breakdown_by_region_final_v2.csv",
        type = "text/csv",
    ),
)

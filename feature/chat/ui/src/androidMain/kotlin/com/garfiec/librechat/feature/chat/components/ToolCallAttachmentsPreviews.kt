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

// A tool call's generated files: a download chip, then the PDFs, which open in the native preview.

@PreviewNarrowScreens
@Composable
internal fun ToolCallAttachmentsLightMaterialPreview() = ToolAttachmentsPreview(false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
internal fun ToolCallAttachmentsDarkGlassPreview() = ToolAttachmentsPreview(true, UiStyle.LIQUID_GLASS)

@Composable
private fun ToolAttachmentsPreview(dark: Boolean, style: UiStyle) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                ToolCallAttachments(attachments = previewToolFiles, toolCallId = "call_1", baseUrl = "https://chat.example.com")
            }
        }
    }
}

private val previewToolFiles = listOf(
    Attachment(fileId = "f1", filename = "summary.csv", type = "text/csv", toolCallId = "call_1", bytes = 1_200),
    Attachment(fileId = "f2", filename = "report.pdf", type = "application/pdf", toolCallId = "call_1", bytes = 1_887),
    Attachment(
        fileId = "f3",
        filename = "quarterly_revenue_breakdown_final.pdf",
        type = "application/pdf",
        toolCallId = "call_1",
        bytes = 245_000,
    ),
)

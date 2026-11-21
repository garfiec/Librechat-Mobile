package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.WorkspaceChange
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// A stateful code session's workspace changes under a reply: 1, 3 and 12 files, and a deep path.

@PreviewNarrowScreens
@Composable
internal fun WorkspaceChangesOneLightMaterialPreview() = WorkspacePreview(false, UiStyle.MATERIAL, previewChanges.take(1))

@PreviewNarrowScreens
@Composable
internal fun WorkspaceChangesThreeLightMaterialPreview() = WorkspacePreview(false, UiStyle.MATERIAL, previewChanges.take(3))

@PreviewNarrowScreens
@Composable
internal fun WorkspaceChangesThreeDarkMaterialPreview() = WorkspacePreview(true, UiStyle.MATERIAL, previewChanges.take(3))

@PreviewNarrowScreens
@Composable
internal fun WorkspaceChangesThreeLightGlassPreview() = WorkspacePreview(false, UiStyle.LIQUID_GLASS, previewChanges.take(3))

@PreviewNarrowScreens
@Composable
internal fun WorkspaceChangesThreeDarkGlassPreview() = WorkspacePreview(true, UiStyle.LIQUID_GLASS, previewChanges.take(3))

@PreviewNarrowScreens
@Composable
internal fun WorkspaceChangesTwelvePreview() = WorkspacePreview(false, UiStyle.MATERIAL, previewChanges)

@PreviewNarrowScreens
@Composable
internal fun WorkspaceChangesLongPathPreview() = WorkspacePreview(true, UiStyle.MATERIAL, listOf(longPathChange))

@PreviewNarrowScreens
@Composable
internal fun WorkspaceChangesCollapsedPreview() =
    WorkspacePreview(false, UiStyle.LIQUID_GLASS, previewChanges.take(3), expanded = false)

@Composable
private fun WorkspacePreview(dark: Boolean, style: UiStyle, changes: List<Attachment>, expanded: Boolean = true) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp)) {
                WorkspaceChangesSection(
                    changes = changes,
                    baseUrl = "https://chat.example.com",
                    stateKey = "preview",
                    initiallyExpanded = expanded,
                )
            }
        }
    }
}

private fun workspaceFile(index: Int, path: String, type: String, operation: String?, fileId: String? = "f$index") =
    Attachment(
        fileId = fileId,
        filename = path.substringAfterLast('/'),
        filepath = fileId?.let { "/api/files/$it" } ?: "/api/files/code/download/s1/$index",
        type = type,
        toolCallId = "call_1",
        workspaceChange = WorkspaceChange(profile = "stateful", operation = operation, path = path),
    )

internal val previewChanges = listOf(
    workspaceFile(1, "analysis.py", "text/x-python", "created"),
    workspaceFile(2, "reports/summary.csv", "text/csv", "updated"),
    workspaceFile(3, "out/revenue_by_region.png", "image/png", "created", fileId = null),
    workspaceFile(4, "data/clean.json", "application/json", "updated"),
    workspaceFile(5, "notes.md", "text/markdown", null),
    workspaceFile(6, "deck/q3_review.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "created"),
    workspaceFile(7, "archive/raw.zip", "application/zip", "created"),
    workspaceFile(8, "report.pdf", "application/pdf", "updated"),
    workspaceFile(9, "src/model.ts", "application/typescript", "created"),
    workspaceFile(10, "audio/clip.mp3", "audio/mpeg", "created"),
    workspaceFile(11, "summary.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "updated"),
    workspaceFile(12, "tmp/scratch.txt", "text/plain", "created"),
)

internal val longPathChange = workspaceFile(
    20,
    "projects/quarterly-planning/2026/q3/regional-breakdowns/north-america/final_revenue_reconciliation_v2.xlsx",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "updated",
)

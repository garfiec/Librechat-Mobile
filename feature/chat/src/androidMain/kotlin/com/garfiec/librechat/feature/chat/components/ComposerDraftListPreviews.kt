package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.chat.util.DraftUploadProgress
import com.garfiec.librechat.feature.chat.util.draftItemsOf

// The list the composer's attachment stack opens over the thread. Items are shared with
// ComposerAttachmentPreviews.kt.

/** The list the stack opens: failed first, then images, files, skills and quotes. */
@PreviewNarrowScreens
@Composable
private fun DraftListMaterialPreview() = StackSurface(UiStyle.MATERIAL) { ListHost() }

@PreviewNarrowScreens
@Composable
private fun DraftListGlassPreview() = StackSurface(UiStyle.LIQUID_GLASS) { ListHost() }

@Composable
private fun ListHost() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = List(3) { "An older reply scrolled up behind the list, long enough to wrap." }.joinToString(" "),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val files = listOf(draftPhoto, draftReport, draftFailed, draftUploading, draftTextRouted)
        ComposerDraftListPanel(
            items = draftItemsOf(
                files = files,
                skills = listOf("quarterly-report-formatting-house-style"),
                quotes = listOf(DRAFT_LONG_QUOTE),
                serverUrl = "",
            ),
            onRemove = {},
            onRetry = {},
            modifier = Modifier.padding(horizontal = 12.dp),
            uploadProgress = DraftUploadProgress { files },
        )
    }
}

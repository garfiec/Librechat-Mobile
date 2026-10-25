package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.padding
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

// The inline editor at the widths where its status line and three actions run out of room. Its own
// file rather than ActionRowPreviews', which sits at detekt's per-file function ceiling.

private const val ORIGINAL = "Can you rewrite this so it reads more formally?"

@PreviewNarrowScreens
@Composable
private fun InlineEditMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { InlineEditHost() }

@PreviewNarrowScreens
@Composable
private fun InlineEditGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { InlineEditHost() }

/** The footer in the state that most needs the room: two changed parts, so Rerun asks for a Save. */
@PreviewNarrowScreens
@Composable
private fun InlineEditPartsFooterPreview() =
    PreviewSurface(UiStyle.MATERIAL) { InlineEditPartsFooterHost() }

@Composable
private fun InlineEditHost() {
    var text by remember { mutableStateOf(ORIGINAL) }
    InlineEditInput(
        session = previewEditSession(
            text = text,
            onTextChange = { text = it },
            hasChanges = text != ORIGINAL,
        ),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun InlineEditPartsFooterHost() {
    MessageEditActions(
        session = previewEditSession(
            text = "",
            onTextChange = {},
            hasChanges = true,
            rerunRequiresSave = true,
        ),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

private fun previewEditSession(
    text: String,
    onTextChange: (String) -> Unit,
    hasChanges: Boolean,
    rerunRequiresSave: Boolean = false,
) = MessageEditSession(
    text = text,
    partDrafts = emptyMap(),
    hasChanges = hasChanges,
    hasBlankChange = false,
    saving = false,
    saveAllowed = true,
    canRerun = true,
    runAllowed = true,
    rerunRequiresSave = rerunRequiresSave,
    rerunDiscardsEdit = false,
    onTextChange = onTextChange,
    onPartTextChange = { _, _ -> },
    onSave = {},
    onRerun = {},
    onCancel = {},
)

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

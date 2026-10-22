package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.settings.viewmodel.MemoryEditorMode
import com.garfiec.librechat.feature.settings.viewmodel.MemoryEditorState

// The memory sheet for content-filtered entries. The server blanks only the fields its filter
// matched, so each case shows a different mix of placeholder and real text.

private val hidden = Memory(
    id = "m4",
    key = "",
    value = "",
    contentFilterBlocked = true,
    updatedAt = "2026-09-28T08:00:00Z",
    tokenCount = 9,
)

private val redactedDraft = memorySheetDraft(MemoryEditorState(mode = MemoryEditorMode.View, original = hidden))

/** Only the value matched the filter: the key arrives intact and shows as it is. */
private val valueHidden = hidden.copy(id = "m5", key = "phone_number")
private val valueHiddenDraft =
    memorySheetDraft(MemoryEditorState(mode = MemoryEditorMode.View, original = valueHidden, key = valueHidden.key))

/** Only the summary matched: key and value arrive intact, and the sheet shows both (read-only). */
private val summaryHidden = hidden.copy(id = "m6", key = "home_city", value = "Lisbon, moving to Porto in spring")
private val summaryHiddenDraft = memorySheetDraft(
    MemoryEditorState(
        mode = MemoryEditorMode.View,
        original = summaryHidden,
        key = summaryHidden.key,
        value = summaryHidden.value,
    ),
)

@PreviewNarrowScreens
@Composable
private fun MemorySheetRedactedGlassPreview() = MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, redactedDraft)

/** A hidden entry for a role without UPDATE: no Delete anywhere, so the text doesn't offer one. */
@PreviewNarrowScreens
@Composable
private fun MemorySheetRedactedNoDeleteMaterialPreview() =
    MemorySheetPreviewHost(UiStyle.MATERIAL, redactedDraft, canDelete = false)

@PreviewNarrowScreens
@Composable
private fun MemorySheetRedactedNoDeleteGlassPreview() =
    MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, redactedDraft, canDelete = false)

@PreviewNarrowScreens
@Composable
private fun MemorySheetValueHiddenMaterialPreview() = MemorySheetPreviewHost(UiStyle.MATERIAL, valueHiddenDraft)

@PreviewNarrowScreens
@Composable
private fun MemorySheetValueHiddenGlassPreview() = MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, valueHiddenDraft)

@PreviewNarrowScreens
@Composable
private fun MemorySheetSummaryHiddenMaterialPreview() = MemorySheetPreviewHost(UiStyle.MATERIAL, summaryHiddenDraft)

@PreviewNarrowScreens
@Composable
private fun MemorySheetSummaryHiddenGlassPreview() = MemorySheetPreviewHost(UiStyle.LIQUID_GLASS, summaryHiddenDraft)

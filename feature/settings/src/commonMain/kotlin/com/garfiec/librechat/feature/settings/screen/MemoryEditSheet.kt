package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.extensions.formatLocalizedNumber
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_cancel
import com.garfiec.librechat.feature.settings.resources.action_close
import com.garfiec.librechat.feature.settings.resources.action_save
import com.garfiec.librechat.feature.settings.resources.add_memory
import com.garfiec.librechat.feature.settings.resources.edit_memory
import com.garfiec.librechat.feature.settings.resources.memory_key_hint
import com.garfiec.librechat.feature.settings.resources.memory_key_label
import com.garfiec.librechat.feature.settings.resources.memory_redacted_key
import com.garfiec.librechat.feature.settings.resources.memory_token_count
import com.garfiec.librechat.feature.settings.resources.memory_token_share
import com.garfiec.librechat.feature.settings.resources.memory_updated_at
import com.garfiec.librechat.feature.settings.resources.memory_value_counter
import com.garfiec.librechat.feature.settings.resources.memory_value_label
import com.garfiec.librechat.feature.settings.resources.view_memory
import com.garfiec.librechat.feature.settings.viewmodel.MemoryEditorMode
import com.garfiec.librechat.feature.settings.viewmodel.MemoryEditorState
import com.garfiec.librechat.feature.settings.viewmodel.MemoryValueProblem
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The memory sheet for adding, editing and viewing one memory: a stateless view of
 * `MemoriesUiState.editor`, whose draft, validation and save guard live in the ViewModel.
 *
 * While the draft has unsaved changes or a save is in flight, every way out (swipe, scrim, back,
 * Cancel) goes to [onDismiss] with the sheet still up; the ViewModel then raises the discard prompt,
 * or ignores it until the save has answered.
 */
@Composable
internal fun MemoryEditSheet(
    editor: MemoryEditorState,
    charLimit: Int,
    /** The role may delete this entry (from its card's menu); a hidden entry's text says so only then. */
    canDelete: Boolean,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        // Held while saving too: the save's answer belongs to this sheet.
        interceptDismiss = editor.isDirty || editor.isSaving,
        modifier = modifier.clearFocusOnTap(),
        skipPartiallyExpanded = true,
        dragHandle = { LowProfileDragHandle() },
    ) {
        MemoryEditSheetContent(
            editor = editor,
            charLimit = charLimit,
            canDelete = canDelete,
            onKeyChange = onKeyChange,
            onValueChange = onValueChange,
            onDismiss = onDismiss,
            onSave = onSave,
        )
    }
}

/** The sheet's body, apart from the sheet so previews can render it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemoryEditSheetContent(
    editor: MemoryEditorState,
    charLimit: Int,
    canDelete: Boolean,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readOnly = editor.mode == MemoryEditorMode.View
    // A content-filtered entry arrives with the matched fields blanked; say so rather than show an
    // empty field, and show any field the filter left intact as it is.
    val hidesKey = editor.original?.hidesKey == true
    val hidesValue = editor.original?.hidesValue == true
    val keyError = memoryKeyErrorText(editor.keyError)
    val localeTag = LocalDateTimeFormat.current.localeTag
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                when (editor.mode) {
                    MemoryEditorMode.Create -> Res.string.add_memory
                    MemoryEditorMode.Edit -> Res.string.edit_memory
                    MemoryEditorMode.View -> Res.string.view_memory
                },
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        // Both fields stay enabled while saving: disabling them drops focus, which closes the
        // keyboard and the sheet with it mid-save. The ViewModel ignores edits until the save answers.
        AdaptiveOutlinedTextField(
            value = if (hidesKey) stringResource(Res.string.memory_redacted_key) else editor.key,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.memory_key_label)) },
            singleLine = true,
            readOnly = readOnly,
            // A key is a-z and underscores: no autocorrect or capitals rewriting it, and Next moves
            // on to the value.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            isError = keyError != null,
            supportingText = when {
                keyError != null -> {
                    { Text(keyError) }
                }
                !readOnly -> {
                    { Text(stringResource(Res.string.memory_key_hint)) }
                }
                else -> null
            },
        )
        AdaptiveOutlinedTextField(
            value = if (hidesValue) redactedValueText(canDelete) else editor.value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.memory_value_label)) },
            readOnly = readOnly,
            minLines = VALUE_MIN_LINES,
            maxLines = VALUE_MAX_LINES,
            isError = editor.valueError == MemoryValueProblem.TOO_LONG,
            // The trimmed length, which is what the server counts against the cap.
            supportingText = if (readOnly) {
                null
            } else {
                {
                    val length = rememberLocalized(editor.valueLength, localeTag)
                    val limit = rememberLocalized(charLimit, localeTag)
                    Text(
                        text = stringResource(Res.string.memory_value_counter, length, limit),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                }
            },
        )
        MemorySheetMeta(editor = editor, localeTag = localeTag)
        editor.saveFailure?.let { failure ->
            Text(
                text = memorySaveFailureText(failure),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                // Announced when it appears: the sheet otherwise only re-enables Save.
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // Wraps, so at 2x font in a long locale Save drops below Cancel instead of being squeezed.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (readOnly) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) }
            } else {
                TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                    Text(stringResource(Res.string.action_cancel))
                }
                AdaptiveButton(onClick = onSave, enabled = editor.canSave) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        }
    }
}

/**
 * The stored entry's tokens (with its share of the personal pool when that can be worked out) and
 * when it was last updated. Nothing for a new memory.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemorySheetMeta(editor: MemoryEditorState, localeTag: String) {
    val original = editor.original ?: return
    val format = LocalDateTimeFormat.current
    val updated = remember(original.updatedAt, format) {
        original.updatedAt?.let { iso -> format.formatAbsoluteOrNull(iso) ?: iso }
    }
    val share = editor.tokenShare
    val tokens = original.tokenCount
    val tokenText = when {
        share != null -> {
            val count = rememberLocalized(share.tokens, localeTag)
            val percent = rememberLocalized(share.percent, localeTag)
            pluralStringResource(Res.plurals.memory_token_share, share.tokens, count, percent)
        }
        tokens != null -> {
            pluralStringResource(Res.plurals.memory_token_count, tokens, rememberLocalized(tokens, localeTag))
        }
        else -> null
    }
    if (tokenText == null && updated == null) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (tokenText != null) MemorySheetMetaText(tokenText)
        if (updated != null) MemorySheetMetaText(stringResource(Res.string.memory_updated_at, updated))
    }
}

/** [value] with [localeTag]'s digit grouping ("10,000", "10.000"). */
@Composable
private fun rememberLocalized(value: Int, localeTag: String): String =
    remember(value, localeTag) { formatLocalizedNumber(value.toDouble(), localeTag) }

@Composable
private fun MemorySheetMetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val VALUE_MIN_LINES = 3

// Then the field scrolls inside itself, so Save stays in reach of a 10,000-character value.
private const val VALUE_MAX_LINES = 10

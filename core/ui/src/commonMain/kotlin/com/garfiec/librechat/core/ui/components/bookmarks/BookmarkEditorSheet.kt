package com.garfiec.librechat.core.ui.components.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.BookmarkNameCheck
import com.garfiec.librechat.core.model.BookmarkNameRules
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.DiscardChangesDialog
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.bookmark_add_to_chat
import com.garfiec.librechat.core.ui.resources.bookmark_cancel
import com.garfiec.librechat.core.ui.resources.bookmark_char_counter
import com.garfiec.librechat.core.ui.resources.bookmark_create
import com.garfiec.librechat.core.ui.resources.bookmark_description
import com.garfiec.librechat.core.ui.resources.bookmark_description_hint
import com.garfiec.librechat.core.ui.resources.bookmark_edit
import com.garfiec.librechat.core.ui.resources.bookmark_error_blank
import com.garfiec.librechat.core.ui.resources.bookmark_error_duplicate
import com.garfiec.librechat.core.ui.resources.bookmark_error_reserved
import com.garfiec.librechat.core.ui.resources.bookmark_error_too_long
import com.garfiec.librechat.core.ui.resources.bookmark_name
import com.garfiec.librechat.core.ui.resources.bookmark_new
import com.garfiec.librechat.core.ui.resources.bookmark_save
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Creates a bookmark, or edits [editing] (its current name). The draft lives here; the caller
 * saves on [onConfirm] (trimmed name and description, and whether to add it to the chat) and drops
 * the sheet once the save went through. While [saving], every way out is held and Create waits.
 *
 * [existingNames] are the account's bookmarks, for the duplicate check ([BookmarkNameRules]).
 * [offerAddToChat] shows "Add to this chat", ticked, when the sheet was opened from a chat.
 * With unsaved edits, swipe, scrim, back and Cancel ask before discarding them.
 */
@Composable
fun BookmarkEditorSheet(
    existingNames: Collection<String>,
    onConfirm: (name: String, description: String, addToChat: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    editing: String? = null,
    initialDescription: String = "",
    offerAddToChat: Boolean = false,
    saving: Boolean = false,
) {
    val draft = rememberBookmarkDraft(editing.orEmpty(), initialDescription)
    var askDiscard by remember { mutableStateOf(false) }
    val dirty = draft.isDirty(editing.orEmpty(), initialDescription)
    val requestClose = {
        when {
            saving -> Unit
            dirty -> askDiscard = true
            else -> onDismiss()
        }
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = requestClose,
        interceptDismiss = dirty || saving,
        modifier = modifier.clearFocusOnTap(),
        skipPartiallyExpanded = true,
        dragHandle = { LowProfileDragHandle() },
    ) {
        BookmarkEditorSheetContent(
            draft = draft,
            existingNames = existingNames,
            editing = editing,
            initialDescription = initialDescription,
            offerAddToChat = offerAddToChat,
            saving = saving,
            onCancel = requestClose,
            onConfirm = onConfirm,
        )
    }

    if (askDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                askDiscard = false
                onDismiss()
            },
            onDismiss = { askDiscard = false },
        )
    }
}

/** The editor's fields, kept across recomposition and process death. */
class BookmarkDraft internal constructor(name: String, description: String, addToChat: Boolean) {
    var name by mutableStateOf(name)
    var description by mutableStateOf(description)
    var addToChat by mutableStateOf(addToChat)

    /** The user has cleared the name at least once: from then on a blank name says so. */
    var nameTouched by mutableStateOf(false)

    fun isDirty(initialName: String, initialDescription: String): Boolean =
        name.trim() != initialName.trim() || description.trim() != initialDescription.trim()
}

@Composable
fun rememberBookmarkDraft(initialName: String = "", initialDescription: String = ""): BookmarkDraft =
    rememberSaveable(saver = BookmarkDraftSaver) { BookmarkDraft(initialName, initialDescription, addToChat = true) }

private val BookmarkDraftSaver = listSaver<BookmarkDraft, Any>(
    save = { listOf(it.name, it.description, it.addToChat, it.nameTouched) },
    restore = { saved ->
        BookmarkDraft(saved[0] as String, saved[1] as String, saved[2] as Boolean).also {
            it.nameTouched = saved[3] as Boolean
        }
    },
)

/** The sheet's body, apart from the sheet so previews can render it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookmarkEditorSheetContent(
    draft: BookmarkDraft,
    existingNames: Collection<String>,
    onCancel: () -> Unit,
    onConfirm: (name: String, description: String, addToChat: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    editing: String? = null,
    initialDescription: String = "",
    offerAddToChat: Boolean = false,
    saving: Boolean = false,
) {
    val check = BookmarkNameRules.validateName(draft.name, existingNames, editing)
    val descriptionLength = draft.description.trim().length
    val descriptionTooLong = descriptionLength > BookmarkNameRules.MAX_DESCRIPTION
    val nameError = nameErrorText(check, draft.nameTouched)
    val changed = editing == null || draft.isDirty(editing, initialDescription)
    val canConfirm = check == BookmarkNameCheck.Ok && !descriptionTooLong && changed && !saving
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(if (editing == null) Res.string.bookmark_new else Res.string.bookmark_edit),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        // Fields stay enabled while saving: disabling one drops focus, and the keyboard with it.
        AdaptiveOutlinedTextField(
            value = draft.name,
            onValueChange = {
                draft.name = it
                if (it.isBlank()) draft.nameTouched = true
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            singleLine = true,
            label = { Text(stringResource(Res.string.bookmark_name)) },
            isError = nameError != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            supportingText = {
                FieldSupport(
                    error = nameError,
                    length = draft.name.trim().length,
                    max = BookmarkNameRules.MAX_NAME,
                )
            },
        )
        AdaptiveOutlinedTextField(
            value = draft.description,
            onValueChange = { draft.description = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = DESCRIPTION_MAX_LINES,
            label = { Text(stringResource(Res.string.bookmark_description)) },
            isError = descriptionTooLong,
            supportingText = {
                FieldSupport(
                    error = if (descriptionTooLong) {
                        tooLongText(BookmarkNameRules.MAX_DESCRIPTION)
                    } else {
                        stringResource(Res.string.bookmark_description_hint)
                    },
                    isError = descriptionTooLong,
                    length = descriptionLength,
                    max = BookmarkNameRules.MAX_DESCRIPTION,
                )
            },
        )
        if (offerAddToChat) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = draft.addToChat,
                        role = Role.Checkbox,
                        onValueChange = { draft.addToChat = it },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveCheckbox(checked = draft.addToChat, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(Res.string.bookmark_add_to_chat),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Wraps, so at 2x font in a long locale the confirm button drops below Cancel.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel, enabled = !saving) {
                Text(stringResource(Res.string.bookmark_cancel))
            }
            AdaptiveButton(
                enabled = canConfirm,
                onClick = { onConfirm(draft.name.trim(), draft.description.trim(), offerAddToChat && draft.addToChat) },
            ) {
                Text(stringResource(if (editing == null) Res.string.bookmark_create else Res.string.bookmark_save))
            }
        }
    }
    // A new bookmark starts at the name; an edit leaves the keyboard down until a field is picked.
    if (editing == null) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}

/** The field's hint or error on the left, its "12/128" counter on the right. */
@Composable
private fun FieldSupport(error: String?, length: Int, max: Int, isError: Boolean = error != null) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = error.orEmpty(),
            modifier = Modifier.weight(1f),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.bookmark_char_counter, length, max),
            textAlign = TextAlign.End,
            color = if (length > max) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun nameErrorText(check: BookmarkNameCheck, touched: Boolean): String? = when (check) {
    BookmarkNameCheck.Ok -> null
    // Not before the user has typed: an empty new form isn't a mistake yet.
    BookmarkNameCheck.Blank -> if (touched) stringResource(Res.string.bookmark_error_blank) else null
    BookmarkNameCheck.TooLong -> tooLongText(BookmarkNameRules.MAX_NAME)
    BookmarkNameCheck.Duplicate -> stringResource(Res.string.bookmark_error_duplicate)
    BookmarkNameCheck.Reserved -> stringResource(Res.string.bookmark_error_reserved)
}

@Composable
private fun tooLongText(max: Int): String = pluralStringResource(Res.plurals.bookmark_error_too_long, max, max)

// Then the field scrolls inside itself, so the buttons stay in reach of a long description.
private const val DESCRIPTION_MAX_LINES = 6

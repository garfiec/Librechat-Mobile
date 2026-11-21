package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_close_search
import com.garfiec.librechat.feature.chat.resources.cd_next_match
import com.garfiec.librechat.feature.chat.resources.cd_previous_match
import com.garfiec.librechat.feature.chat.resources.cd_search_in_conversation
import com.garfiec.librechat.feature.chat.resources.hint_find_in_conversation
import org.jetbrains.compose.resources.stringResource

/**
 * In-conversation search rendered as a single inset, rounded floating capsule. Built on
 * [FloatingBarChip] so it shares the fill + border of the floating top-bar chips and composer input,
 * inset with side margins. Holds the query field, match counter, up/down navigation, and close.
 */
@Composable
fun InConvoSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    currentMatchIndex: Int,
    totalMatches: Int,
    onPreviousMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Focus the field on appear so search is type-ready, unless it opened with a query (a jump from a
    // search hit): that query was already committed, so the keyboard stays down. Guarded because
    // requestFocus can throw if the node isn't placed yet during the caller's enter transition; a miss
    // just degrades to tap-to-focus.
    val openedEmpty = remember { query.isEmpty() }
    LaunchedEffect(Unit) { if (openedEmpty) runCatching { focusRequester.requestFocus() } }
    // The field keeps its own value. Fed straight from [query], each keystroke round-trips through
    // the screen's state, and a burst of them lands on a value a keystroke behind, which puts the
    // cursor back mid-word ("the" became "th|e"). Only an outside reset (emptied) is copied back in.
    var field by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(query) {
        if (query.isEmpty() && field.text.isNotEmpty()) field = TextFieldValue()
    }

    FloatingBarChip(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))

            val searchCd = stringResource(Res.string.cd_search_in_conversation)
            BasicTextField(
                value = field,
                onValueChange = { value ->
                    val textChanged = value.text != field.text
                    field = value
                    if (textChanged) onQueryChange(value.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    // Esc closes find with a hardware keyboard, as in a browser.
                    .onPreviewKeyEvent { event ->
                        val escape = event.key == Key.Escape && event.type == KeyEventType.KeyUp
                        if (escape) {
                            keyboardController?.hide()
                            onClose()
                        }
                        escape
                    }
                    .semantics { contentDescription = searchCd },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        if (totalMatches > 0) onNextMatch()
                    },
                ),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (field.text.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.hint_find_in_conversation),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                // One line, as the field is: a wrapped hint would make the empty bar taller.
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (query.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (totalMatches > 0) {
                        "${currentMatchIndex + 1}/$totalMatches"
                    } else {
                        "0/0"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onPreviousMatch,
                enabled = totalMatches > 0,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.cd_previous_match),
                    tint = if (totalMatches > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(
                onClick = onNextMatch,
                enabled = totalMatches > 0,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.cd_next_match),
                    tint = if (totalMatches > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(
                onClick = {
                    // Drop the keyboard now so it doesn't linger through the exit animation.
                    keyboardController?.hide()
                    onClose()
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.cd_close_search),
                )
            }
        }
    }
}

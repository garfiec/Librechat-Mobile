package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The flat-text editor: one field over the message's `text`, with the shared action footer. */
@Composable
fun InlineEditInput(
    session: MessageEditSession,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MessageEditField(
            value = session.text,
            onValueChange = session.onTextChange,
            enabled = !session.saving,
        )
        MessageEditActions(session)
    }
}

/**
 * One editable block. [label] names which part of the reply it is ("Response" / "Thoughts") so a
 * multi-part reply's fields are tellable apart; the flat-text editor passes none.
 */
@Composable
fun MessageEditField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    label: StringResource? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        AdaptiveOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 200.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            maxLines = 10,
        )
    }
}

/**
 * Cancel / Save / Rerun, with one status line above them.
 *
 * Every state shares that single slot rather than adding a row, so the message below never shifts
 * as the user types, and the states are ordered by how actionable they are: what blocks the save
 * first, then why the rerun is unavailable or will not carry the edit — the question a save-only
 * editor otherwise leaves unanswered.
 */
@Composable
fun MessageEditActions(
    session: MessageEditSession,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(8.dp))

        editStatus(session)?.let { status ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // Announced when it changes: it is the only thing that explains a disabled
                    // Save or a withheld Rerun.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Sized to their labels and wrapping, not equal thirds: a third is too narrow for the
        // rerun label even at phone width, and a translated one needs more still.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveOutlinedButton(onClick = session.onCancel, enabled = !session.saving) {
                Text(stringResource(Res.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            AdaptiveFilledTonalButton(
                onClick = session.onSave,
                enabled = session.saveEnabled,
            ) {
                val label = if (session.saving) Res.string.saving else Res.string.save
                Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Withheld, not disabled, when there is no user turn to replay: the status line says
            // why, and a disabled button with no explanation is the thing being avoided.
            if (session.canRerun) {
                AdaptiveButton(
                    onClick = session.onRerun,
                    enabled = session.rerunEnabled,
                ) {
                    val label = if (session.hasChanges && !session.rerunDiscardsEdit) {
                        Res.string.update_and_rerun
                    } else {
                        Res.string.rerun
                    }
                    Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun editStatus(session: MessageEditSession): StringResource? = when {
    session.hasBlankChange -> Res.string.edit_status_part_empty
    session.rerunRequiresSave -> Res.string.edit_status_save_before_rerun
    session.hasChanges && session.rerunDiscardsEdit && session.canRerun ->
        Res.string.edit_status_rerun_discards_changes
    session.hasChanges -> Res.string.edit_status_unsaved_changes
    !session.canRerun -> Res.string.edit_status_rerun_needs_user_turn
    else -> null
}

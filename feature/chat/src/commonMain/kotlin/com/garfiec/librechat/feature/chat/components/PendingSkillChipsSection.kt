package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveInputChip
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_pending_skills
import com.garfiec.librechat.feature.chat.resources.cd_remove_skill
import org.jetbrains.compose.resources.stringResource

/** A chip's label stops here and ellipsizes, so one long name can't fill a 320dp row. */
private val MAX_CHIP_LABEL_WIDTH = 192.dp

/**
 * The chips wrap onto further rows up to about two rows, then scroll, so up to
 * `MAX_MANUAL_SKILLS` long names on a narrow phone can't push the text field off screen while
 * the keyboard is up.
 */
private val MAX_SECTION_HEIGHT = 96.dp

/**
 * Skills picked from the `/` and `$` list for the next message, one removable chip each, above
 * the composer until a fresh send takes them (`SendDispatchDelegate.takePendingManualSkills`).
 * On the sent user message they become [SkillPills].
 *
 * Tapping a chip removes it: the whole chip is the hit target, so a tap beside the × isn't lost on
 * a chip that does nothing, and a screen reader hears one "Remove skill X" action per chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PendingSkillChipsSection(
    skills: List<String>,
    onRemove: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (skills.isEmpty()) return
    val sectionCd = stringResource(Res.string.cd_pending_skills)
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = MAX_SECTION_HEIGHT)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = sectionCd },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Filled, like the quote and steer rows: the composer overlays the message list and is
        // transparent up here, so M3's transparent chip would show scrolled text through its label.
        // Label and icon colours are the ones the glass variant gives default chips, so a glass
        // chip (which swaps in its own fill) looks like any other glass chip.
        val chipColors = InputChipDefaults.inputChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.primary,
        )
        skills.forEach { name ->
            val removeCd = stringResource(Res.string.cd_remove_skill, name)
            AdaptiveInputChip(
                selected = false,
                onClick = { onRemove(name) },
                colors = chipColors,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = removeCd
                    role = Role.Button
                    onClick {
                        onRemove(name)
                        true
                    }
                },
                label = {
                    Text(
                        text = name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = MAX_CHIP_LABEL_WIDTH),
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_skill_always_applied
import com.garfiec.librechat.feature.chat.resources.cd_skill_used
import org.jetbrains.compose.resources.stringResource

/** A pill's name stops here and ellipsizes (web caps it at 12rem). */
private val MAX_PILL_LABEL_WIDTH = 192.dp

/**
 * The skills primed into a user turn, one pill each, on the user message next to its quotes
 * (upstream `SkillPills.tsx`): the always-applied ones first with a pin, then the ones the user
 * picked with the skill icon. Both lists are persisted on the message (`alwaysAppliedSkills`,
 * `manualSkills`), so the pills survive a reopen. Wraps onto further rows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillPills(
    alwaysApplied: List<String>,
    manual: List<String>,
    modifier: Modifier = Modifier,
) {
    if (alwaysApplied.isEmpty() && manual.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        alwaysApplied.forEach { name ->
            SkillPill(name = name, icon = Icons.Default.PushPin, cd = stringResource(Res.string.cd_skill_always_applied, name))
        }
        manual.forEach { name ->
            SkillPill(name = name, icon = Icons.Default.Extension, cd = stringResource(Res.string.cd_skill_used, name))
        }
    }
}

@Composable
private fun SkillPill(name: String, icon: ImageVector, cd: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .border(1.dp, colors.outlineVariant, CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = cd },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 4.dp)
                .widthIn(max = MAX_PILL_LABEL_WIDTH),
        )
    }
}

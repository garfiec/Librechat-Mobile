package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun SiblingNavigator(
    siblingIndex: Int,
    siblingCount: Int,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (siblingCount <= 1) return

    val position = stringResource(Res.string.sibling_position, siblingIndex + 1, siblingCount)
    Row(
        modifier = modifier.semantics {
            stateDescription = position
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(
            onClick = { onNavigate(siblingIndex - 1) },
            enabled = siblingIndex > 0,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(Res.string.cd_previous_response),
                modifier = Modifier.size(20.dp),
                tint = if (siblingIndex > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        }

        Text(
            text = "${siblingIndex + 1} / $siblingCount",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        IconButton(
            onClick = { onNavigate(siblingIndex + 1) },
            enabled = siblingIndex < siblingCount - 1,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.cd_next_response),
                modifier = Modifier.size(20.dp),
                tint = if (siblingIndex < siblingCount - 1) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        }
    }
}

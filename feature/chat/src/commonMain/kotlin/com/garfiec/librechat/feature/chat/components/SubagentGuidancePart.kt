package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.subagent_guidance_label
import org.jetbrains.compose.resources.stringResource

/**
 * A `steer` part inside a child's activity (v0.8.9-rc1): guidance the parent agent, or the user
 * through it, sent while the child was working, at the point it went in (upstream `SteerPart` in
 * `SubagentActivity`).
 *
 * Not [SteerContentPart]: that one is the reader's own words in their own reply, under their name
 * and avatar. Here the author is whoever briefed the child, which this client cannot name, so the
 * row says what it is instead.
 */
@Composable
internal fun SubagentGuidancePart(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        DisableSelection {
            Text(
                text = stringResource(Res.string.subagent_guidance_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        MarkdownContent(text = text)
    }
}

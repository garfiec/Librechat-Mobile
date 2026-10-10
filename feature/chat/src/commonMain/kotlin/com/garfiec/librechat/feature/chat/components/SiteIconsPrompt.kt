package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.site_icons_prompt_allow
import com.garfiec.librechat.feature.chat.resources.site_icons_prompt_body
import com.garfiec.librechat.feature.chat.resources.site_icons_prompt_deny
import com.garfiec.librechat.feature.chat.resources.site_icons_prompt_settings_hint
import com.garfiec.librechat.feature.chat.resources.site_icons_prompt_title
import com.garfiec.librechat.feature.chat.viewmodel.SiteIconsState
import org.jetbrains.compose.resources.stringResource

/**
 * The website-icons preference as the search-result cards see it, plus the hook a card calls to
 * raise the one-time prompt. Provided by [ChatRoot], which hosts the prompt so several cards on
 * screen raise one dialog.
 */
@Immutable
data class SiteIcons(
    val state: SiteIconsState,
    val requestChoice: () -> Unit,
)

/** Off, and never prompts, outside [ChatRoot]: nothing loads an external icon unasked. */
val LocalSiteIcons = compositionLocalOf { SiteIcons(SiteIconsState.OFF) {} }

/**
 * Asks once whether web-search results may load site icons from external servers. Closing it
 * without a choice is [onDismiss]: nothing is stored, so it asks again on a later launch.
 */
@Composable
internal fun SiteIconsPromptDialog(
    onChoice: (show: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(Res.string.site_icons_prompt_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.site_icons_prompt_body))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(Res.string.site_icons_prompt_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoice(true) }) {
                Text(stringResource(Res.string.site_icons_prompt_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { onChoice(false) }) {
                Text(stringResource(Res.string.site_icons_prompt_deny))
            }
        },
    )
}

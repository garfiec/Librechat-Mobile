package com.garfiec.librechat.shared.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.session_renewal_blocked_message
import com.garfiec.librechat.shared.resources.session_renewal_sign_in_again
import org.jetbrains.compose.resources.stringResource

/**
 * The exit from a session that is kept but cannot renew ([NavHostViewModel.sessionRenewalBlocked]).
 *
 * A banner in the shell, not a dialog: a dismissed dialog would leave the user exactly where they
 * were — signed in, cached chats visible, every request failing with a sentence about tokens. This
 * stays until the refresh succeeds again (the block lifted) or the user takes the one action that
 * helps. Rendered where the server banner is, on both layouts, and only outside the auth flow.
 */
@Composable
internal fun SessionRenewalBlockedBanner(
    visible: Boolean,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
    ) {
        Card(
            modifier = Modifier
                // It is the topmost thing in the shell, above the screen's own app bar, so it has to
                // clear the status bar itself or the clock and icons draw over it.
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            // Sign in again drops under the message rather than squeeze it narrower than
            // MinMessageWidth.
            FlowRow(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                horizontalArrangement = Arrangement.End,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).widthIn(min = MinMessageWidth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(Res.string.session_renewal_blocked_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                TextButton(onClick = onSignInAgain) {
                    Text(
                        text = stringResource(Res.string.session_renewal_sign_in_again),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

private val MinMessageWidth = 160.dp

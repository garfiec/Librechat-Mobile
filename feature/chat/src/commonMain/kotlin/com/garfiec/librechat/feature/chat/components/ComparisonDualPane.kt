package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Tablet-layout comparison view with two side-by-side panes.
 * Each pane has a model selector header and its own MessageList content.
 *
 * @param primaryModelSelector Composable for the primary model selector button
 * @param secondaryModelSelector Composable for the secondary model selector button
 * @param primaryContent Composable content for the primary conversation pane
 * @param secondaryContent Composable content for the secondary conversation pane
 */
@Composable
fun ComparisonDualPane(
    primaryModelSelector: @Composable () -> Unit,
    secondaryModelSelector: @Composable () -> Unit,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onContinueWithPrimary: (() -> Unit)? = null,
    onContinueWithSecondary: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxSize()) {
        // Primary pane
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                primaryModelSelector()
            }
            if (onContinueWithPrimary != null) {
                TextButton(
                    onClick = onContinueWithPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(Res.string.continue_with_response))
                }
            }
            AdaptiveDivider()
            Box(modifier = Modifier.weight(1f)) {
                primaryContent()
            }
        }

        // Divider
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        // Secondary pane
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                secondaryModelSelector()
            }
            if (onContinueWithSecondary != null) {
                TextButton(
                    onClick = onContinueWithSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(stringResource(Res.string.continue_with_response))
                }
            }
            AdaptiveDivider()
            Box(modifier = Modifier.weight(1f)) {
                secondaryContent()
            }
        }
    }
}

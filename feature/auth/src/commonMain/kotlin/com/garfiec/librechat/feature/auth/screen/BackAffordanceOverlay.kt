package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.back
import org.jetbrains.compose.resources.stringResource

/**
 * Top-start back affordance shown only in the add-account variant of the auth screens (the
 * onboarding roots pass a null [onBack], so nothing renders there). Popping the pending add
 * route cancels the flow. Shared by [LoginScreen] and [ServerUrlScreen] so the padding, icon,
 * hit target, and accessibility label stay in one place.
 */
@Composable
fun BoxScope.BackAffordanceOverlay(onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (onBack != null) {
        IconButton(
            onClick = onBack,
            modifier = modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.back),
            )
        }
    }
}

/**
 * How far below the status bar a screen's content starts when [BackAffordanceOverlay] shows, so
 * content scrolled to the top never sits under the button: its 4dp inset and 48dp target, plus a
 * 4dp gap.
 */
internal val BackAffordanceClearance: Dp = 56.dp

package com.garfiec.librechat.feature.auth.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.use_another_account
import org.jetbrains.compose.resources.stringResource

/**
 * The way from a sign-in screen to another account that is still signed in: the shell opens its
 * account switcher on those accounts. Hidden when [onClick] is null (none is signed in, or an
 * add-account flow, whose back affordance already returns to the live account).
 */
@Composable
internal fun UseAnotherAccountButton(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (onClick == null) return
    AdaptiveTextButton(onClick = onClick, modifier = modifier.testTag("auth_use_another_account")) {
        Text(stringResource(Res.string.use_another_account))
    }
}

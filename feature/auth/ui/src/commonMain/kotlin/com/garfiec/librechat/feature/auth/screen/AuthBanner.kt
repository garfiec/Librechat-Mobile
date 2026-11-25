package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.Banner
import com.garfiec.librechat.core.ui.components.BannerDisplay

/**
 * The server's public banner, pinned above a sign-in screen's form. A dismissal holds for this screen
 * only: there is no account yet to remember it against.
 */
@Composable
internal fun AuthBanner(banner: Banner?, modifier: Modifier = Modifier) {
    var dismissedId by rememberSaveable { mutableStateOf<String?>(null) }
    BannerDisplay(
        banner = banner?.takeIf { it.bannerId != dismissedId },
        onDismiss = { dismissedId = it },
        modifier = modifier,
    )
}

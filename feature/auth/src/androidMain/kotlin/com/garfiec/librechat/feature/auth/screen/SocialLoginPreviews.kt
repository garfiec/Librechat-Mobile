package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The sign-in screen's social buttons, as LoginScreen lays them out: one full-width button each.
private val providers = listOf("google", "github", "openid", "saml")

@PreviewNarrowScreens
@Composable
private fun SocialLoginMaterialPreview() = SocialLoginHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SocialLoginGlassPreview() = SocialLoginHost(UiStyle.LIQUID_GLASS)

@Composable
private fun SocialLoginHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                providers.forEach { provider ->
                    AdaptiveOutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                        Text(oAuthProviderLabel(provider))
                    }
                }
            }
        }
    }
}

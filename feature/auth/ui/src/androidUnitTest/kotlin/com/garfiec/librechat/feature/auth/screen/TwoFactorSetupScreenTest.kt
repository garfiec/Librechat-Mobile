package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorSetupPhase
import com.garfiec.librechat.feature.auth.viewmodel.TwoFactorSetupUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoFactorSetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `open authenticator reports no app when the platform cannot open the link`() {
        // Nothing on the device handles otpauth://, so starting the link fails.
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        // iOS's UriHandler reports nothing when no app takes the link; the screen must not trust it.
        val silentHandler = object : UriHandler {
            override fun openUri(uri: String) = Unit
        }
        composeRule.setContent {
            LibreChatTheme {
                CompositionLocalProvider(LocalUriHandler provides silentHandler) {
                    AuthenticatorPhase(
                        state = TwoFactorSetupUiState(
                            phase = TwoFactorSetupPhase.Authenticator,
                            otpauthUrl = "otpauth://totp/LibreChat:a@b?secret=ABC",
                            setupKey = "ABC",
                        ),
                        onNext = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Open authenticator app").performClick()

        composeRule
            .onNodeWithText("No authenticator app on this device could open the link. Enter the setup key instead.")
            .assertExists()
    }
}

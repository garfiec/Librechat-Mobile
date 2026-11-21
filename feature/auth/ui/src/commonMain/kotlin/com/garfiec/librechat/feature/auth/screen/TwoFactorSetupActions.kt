package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Immutable

@Immutable
data class TwoFactorSetupActions(
    val onStart: () -> Unit = {},
    val onContinueToVerify: () -> Unit = {},
    val onCodeChange: (String) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onBackToAuthenticator: () -> Unit = {},
    val onCodesSaved: () -> Unit = {},
    val onCodesAcknowledged: (Boolean) -> Unit = {},
    val onComplete: () -> Unit = {},
    /** Abandons the staged setup, then goes back; the top-bar arrow and back once a secret is staged. */
    val onLeave: () -> Unit = {},
    /** Goes back to sign-in, from the expired-setup pane. */
    val onBack: () -> Unit = {},
)

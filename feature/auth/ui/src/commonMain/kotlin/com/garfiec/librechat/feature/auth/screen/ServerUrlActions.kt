package com.garfiec.librechat.feature.auth.screen

import androidx.compose.runtime.Immutable

@Immutable
data class ServerUrlActions(
    val onUrlChange: (String) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onConfirmHttp: () -> Unit = {},
    val onDismissHttpWarning: () -> Unit = {},
    val onToggleAdvanced: () -> Unit = {},
    val onHeaderNameChange: (Int, String) -> Unit = { _, _ -> },
    val onHeaderValueChange: (Int, String) -> Unit = { _, _ -> },
    val onAddHeader: () -> Unit = {},
    val onRemoveHeader: (Int) -> Unit = {},
    val onSelectKnownServer: (String) -> Unit = {},
    val onGateClientIdChange: (String) -> Unit = {},
    val onSignInToGate: () -> Unit = {},
    val onOpenGatePage: () -> Unit = {},
    val onDismissGatePage: () -> Unit = {},
)

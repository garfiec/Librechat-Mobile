package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.PopupProperties

internal actual fun PopupProperties.withPredictiveBack(): PopupProperties = this

@Composable
internal actual fun MenuPredictiveBackEffect(
    enabled: Boolean,
    dismissOnOutsideTap: Boolean,
    onOutsideTap: () -> Unit,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) = Unit

package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.PopupProperties

// Serves MaterialMenuPopup only — the window fallback inside dialogs and sheets. The in-canvas
// MaterialMenuOverlay gets the back gesture through PredictiveBackHandler directly.

/**
 * These [PopupProperties] with the popup's own back and outside-touch dismissal switched off where
 * [MenuPredictiveBackEffect] takes them over (Android 14+), else unchanged. The popup dismisses on
 * the first back event, and on the touch-down of any touch outside it — which an edge back swipe
 * begins with — so neither would let the gesture's progress reach the menu.
 */
internal expect fun PopupProperties.withPredictiveBack(): PopupProperties

/**
 * Inside a popup whose properties went through [withPredictiveBack]: follows the system back gesture
 * on the popup's own window ([onProgress] 0..1, then [onCancel] or [onCommit]), and, when
 * [dismissOnOutsideTap], calls [onOutsideTap] for a touch that goes down *and* up outside the popup.
 * A touch the system cancels — an edge swipe it claims as back — is not a tap. A no-op where
 * [withPredictiveBack] changes nothing (Android before 14, iOS).
 */
@Composable
internal expect fun MenuPredictiveBackEffect(
    enabled: Boolean,
    dismissOnOutsideTap: Boolean,
    onOutsideTap: () -> Unit,
    onProgress: (Float) -> Unit,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
)

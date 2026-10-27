package com.garfiec.librechat.core.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable

// iOS has no FLAG_SECURE. Callers re-mask secrets on ON_PAUSE (scene resigning active), while Compose
// still draws; by ON_STOP (scene in the background) the display link is paused and the masked frame
// would never reach the app-switcher snapshot.
@Composable
internal actual fun SecureWindowEffect(secure: Boolean) = Unit

@OptIn(ExperimentalMaterial3Api::class)
internal actual fun secureSheetProperties(secure: Boolean): ModalBottomSheetProperties =
    ModalBottomSheetDefaults.properties

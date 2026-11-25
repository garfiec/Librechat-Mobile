package com.garfiec.librechat.core.ui.components.topbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Unreachable: the browser's glassCapability() never selects native bars.
@Composable
internal actual fun NativeGlassTopBar(spec: AdaptiveTopBarSpec, modifier: Modifier) = Unit

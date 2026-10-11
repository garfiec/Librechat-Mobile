package com.garfiec.librechat.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberSystemReduceMotion(): Boolean = remember { UIAccessibilityIsReduceMotionEnabled() }

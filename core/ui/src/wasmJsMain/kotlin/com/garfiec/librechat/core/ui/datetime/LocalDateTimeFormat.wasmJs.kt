package com.garfiec.librechat.core.ui.datetime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.garfiec.librechat.core.ui.theme.LocalAppLocale

@Composable
internal actual fun rememberSystemIs24HourReader(): () -> Boolean = remember { { false } }

@Composable
actual fun rememberDateLocaleTag(): String = LocalAppLocale.current

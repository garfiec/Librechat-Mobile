package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.PopupProperties

actual fun KeyboardOptions.withIosTextContentType(type: IosTextContentType): KeyboardOptions = this

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

@Composable
internal actual fun SecureWindowEffect(secure: Boolean) = Unit

@OptIn(ExperimentalMaterial3Api::class)
internal actual fun secureSheetProperties(secure: Boolean): ModalBottomSheetProperties =
    ModalBottomSheetDefaults.properties

internal actual val keyboardInsertsSmartPunctuation: Boolean = false

actual fun Modifier.testTagsAsResourceIdSubtree(): Modifier = this

package com.garfiec.librechat.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens

@PreviewNarrowScreens
@Composable
private fun PdfPasswordGlassPreview() = PdfPasswordHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun PdfPasswordMaterialPreview() = PdfPasswordHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun OtpGlassKeyboardPreview() = OtpHost(UiStyle.LIQUID_GLASS, height = 400.dp)

@PreviewNarrowScreens
@Composable
private fun LongTextGlassPreview() = LongTextHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun LongTextMaterialPreview() = LongTextHost(UiStyle.MATERIAL)

@Composable
private fun PdfPasswordHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    PdfPasswordDialog(filename = "quarterly-report-final.pdf", incorrectPassword = false, onSubmit = {}, onDismiss = {})
}

@Composable
private fun OtpHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    OtpVerificationDialog(
        title = "Two-factor authentication",
        description = "Enter the six-digit code from your authenticator app to finish signing in.",
        onVerify = { _, _ -> },
        onDismiss = {},
    )
}

/** A message longer than the panel, with two long button labels: the slot has to scroll and the buttons wrap. */
@Composable
private fun LongTextHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    AdaptiveAlertDialog(
        onDismissRequest = {},
        title = { Text("A title long enough to take two lines") },
        text = { Text(List(12) { "This sentence repeats so the message runs past the panel." }.joinToString(" ")) },
        confirmButton = { TextButton(onClick = {}) { Text("Confirm the long action") } },
        dismissButton = { TextButton(onClick = {}) { Text("Keep it as it is") } },
    )
}

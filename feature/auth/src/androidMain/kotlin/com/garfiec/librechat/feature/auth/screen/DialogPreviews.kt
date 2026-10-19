package com.garfiec.librechat.feature.auth.screen

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.cancel
import com.garfiec.librechat.feature.auth.resources.continue_button
import com.garfiec.librechat.feature.auth.resources.sso_risk_body
import com.garfiec.librechat.feature.auth.resources.sso_risk_title
import org.jetbrains.compose.resources.stringResource

@PreviewNarrowScreens
@Composable
private fun HttpWarningGlassPreview() = HttpWarningHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SsoRiskGlassPreview() = SsoRiskHost(UiStyle.LIQUID_GLASS)

@Composable
private fun HttpWarningHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    HttpWarningDialog(onConfirm = {}, onDismiss = {})
}

/** LoginScreen opens this inline, behind its ViewModel; the same call with its strings. */
@Composable
private fun SsoRiskHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    AdaptiveAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.sso_risk_title)) },
        text = { Text(stringResource(Res.string.sso_risk_body)) },
        confirmButton = { TextButton(onClick = {}) { Text(stringResource(Res.string.continue_button)) } },
        dismissButton = { TextButton(onClick = {}) { Text(stringResource(Res.string.cancel)) } },
    )
}

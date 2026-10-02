package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog

@Composable
internal actual fun AvatarUploadDialog(
    currentAvatarUrl: String?,
    isUploading: Boolean,
    onPickImage: (Any) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    // iOS: placeholder dialog — image picker not yet implemented
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Avatar") },
        text = { Text("Avatar upload is not yet available on iOS.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

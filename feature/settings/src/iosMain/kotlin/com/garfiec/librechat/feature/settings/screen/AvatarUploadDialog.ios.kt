package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.avatar_upload_unavailable_ios
import com.garfiec.librechat.feature.settings.resources.dialog_ok
import com.garfiec.librechat.feature.settings.resources.dialog_title_update_avatar
import org.jetbrains.compose.resources.stringResource

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
        title = { Text(stringResource(Res.string.dialog_title_update_avatar)) },
        text = { Text(stringResource(Res.string.avatar_upload_unavailable_ios)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_ok)) }
        },
    )
}

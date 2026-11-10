package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Whether the profile offers changing the avatar: false where there is no image picker. */
internal expect val avatarUploadSupported: Boolean

@Composable
internal expect fun AvatarUploadDialog(
    currentAvatarUrl: String?,
    isUploading: Boolean,
    onPickImage: (Any) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)

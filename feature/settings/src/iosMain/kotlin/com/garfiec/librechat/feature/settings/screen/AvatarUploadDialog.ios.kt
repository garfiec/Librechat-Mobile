package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** iOS has no avatar picker, so the profile offers no avatar change there. */
internal actual val avatarUploadSupported: Boolean = false

/** Never opened: [avatarUploadSupported] is false on iOS. */
@Composable
internal actual fun AvatarUploadDialog(
    currentAvatarUrl: String?,
    isUploading: Boolean,
    onPickImage: (Any) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) = Unit

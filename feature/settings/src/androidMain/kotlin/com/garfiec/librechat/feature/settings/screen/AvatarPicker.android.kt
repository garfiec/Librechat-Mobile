package com.garfiec.librechat.feature.settings.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberAvatarPicker(onPick: (PickedAvatar) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPick(PickedAvatar(ref = uri, preview = uri))
    }
    return remember(launcher) { { launcher.launch("image/*") } }
}

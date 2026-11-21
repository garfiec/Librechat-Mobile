package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.ui.media.imagePreviewOf
import com.garfiec.librechat.core.ui.media.openPhotoPicker

/** The dialog's preview circle is 120pt, drawn at 360 px on a 3x screen. */
private const val AVATAR_PREVIEW_MAX_EDGE_PX = 360

/**
 * The ref is the picked bytes, read inside the picker's callback: the picker's files are gone by the
 * time the upload reads them. The iOS settings ContentReader returns them as they are.
 */
@Composable
internal actual fun rememberAvatarPicker(onPick: (PickedAvatar) -> Unit): () -> Unit {
    val currentOnPick by rememberUpdatedState(onPick)
    return remember {
        {
            openPhotoPicker(
                selectionLimit = 1,
                decode = { bytes, _, image ->
                    PickedAvatar(ref = bytes, preview = imagePreviewOf(image, AVATAR_PREVIEW_MAX_EDGE_PX))
                },
                onResult = { picks -> picks.firstOrNull()?.let { currentOnPick(it) } },
            )
        }
    }
}

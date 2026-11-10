package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

/** A picked image: [ref] is what the upload reads, [preview] the Coil model the dialog draws (null: nothing drawable). */
internal class PickedAvatar(val ref: Any, val preview: Any?)

@Composable
internal expect fun rememberAvatarPicker(onPick: (PickedAvatar) -> Unit): () -> Unit

@Composable
internal fun AvatarUploadDialog(
    currentAvatarUrl: String?,
    isUploading: Boolean,
    onPickImage: (Any) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<PickedAvatar?>(null) }
    val pickImage = rememberAvatarPicker { selected = it }

    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(Res.string.dialog_title_update_avatar)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val avatarPreviewCd = stringResource(Res.string.cd_avatar_preview)
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = avatarPreviewCd
                        },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    val pick = selected
                    if (pick?.preview != null) {
                        AsyncImage(
                            model = pick.preview,
                            contentDescription = stringResource(Res.string.cd_selected_avatar),
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (pick == null && currentAvatarUrl != null) {
                        AsyncImage(
                            model = currentAvatarUrl,
                            contentDescription = stringResource(Res.string.cd_current_avatar),
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                IconButton(
                    onClick = pickImage,
                    enabled = !isUploading,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(Res.string.cd_choose_image),
                    )
                }

                Text(
                    text = stringResource(Res.string.avatar_choose_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (isUploading) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AdaptiveCircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        },
        confirmButton = {
            AdaptiveTextButton(
                onClick = { selected?.let { onPickImage(it.ref) } },
                enabled = selected != null && !isUploading,
            ) {
                Text(stringResource(if (isUploading) Res.string.action_uploading else Res.string.action_upload))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

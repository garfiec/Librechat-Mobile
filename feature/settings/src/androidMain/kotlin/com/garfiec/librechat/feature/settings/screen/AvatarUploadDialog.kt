package com.garfiec.librechat.feature.settings.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TextButton
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
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun AvatarUploadDialog(
    currentAvatarUrl: String?,
    isUploading: Boolean,
    onPickImage: (Any) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
        }
    }

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
                    val displayUri = selectedUri
                    if (displayUri != null) {
                        AsyncImage(
                            model = displayUri,
                            contentDescription = stringResource(Res.string.cd_selected_avatar),
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Crop,
                        )
                    } else if (currentAvatarUrl != null) {
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
                    onClick = { imagePickerLauncher.launch("image/*") },
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
            TextButton(
                onClick = {
                    val uri = selectedUri
                    if (uri != null) {
                        onPickImage(uri)
                    }
                },
                enabled = selectedUri != null && !isUploading,
            ) {
                Text(stringResource(if (isUploading) Res.string.action_uploading else Res.string.action_upload))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

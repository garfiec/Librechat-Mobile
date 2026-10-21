package com.garfiec.librechat.feature.conversations.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.model.share.ShareSheetState
import com.garfiec.librechat.core.ui.components.acl.AclSharingActions
import com.garfiec.librechat.core.ui.components.share.LocalOpenSharedChat
import com.garfiec.librechat.core.ui.components.share.ShareLinkSheet
import com.garfiec.librechat.core.ui.resources.share_sheet_title
import com.garfiec.librechat.feature.conversations.platform.RegisterPlatformActions
import com.garfiec.librechat.feature.conversations.platform.copyToClipboard
import org.jetbrains.compose.resources.stringResource
import com.garfiec.librechat.core.ui.resources.Res as CoreRes

/**
 * The share sheet over [controller], for the drawer, the conversation list and a project's chats:
 * renders its state and sends the sheet's taps back. Shows nothing while the controller is closed.
 * Copy feedback and errors render inside the sheet, since a snackbar would sit behind its scrim.
 */
@Composable
internal fun ShareLinkSheetHost(controller: ShareLinkController, onDismiss: () -> Unit) {
    // Copy needs the Context on Android.
    RegisterPlatformActions()
    val state by controller.state.collectAsStateWithLifecycle()
    val access by controller.access.state.collectAsStateWithLifecycle()
    val openSharedChat = LocalOpenSharedChat.current
    val clipLabel = stringResource(CoreRes.string.share_sheet_title)
    val accessActions = remember(controller) {
        val acl = controller.access
        AclSharingActions(
            onPublicChange = acl::setPublic,
            onSearchQueryChange = acl::onSearchQueryChange,
            onGrant = acl::grant,
            onRevoke = acl::revoke,
            onGrantRoleChange = acl::setGrantRole,
            onRetry = acl::retry,
        )
    }
    ShareLinkSheet(
        state = state,
        onDismiss = onDismiss,
        onCreate = controller::create,
        onUpdate = controller::update,
        onDelete = controller::delete,
        onIncludeFilesChange = controller::setIncludeFiles,
        onCopy = { url -> copyToClipboard(url, clipLabel) },
        onRetry = controller::retry,
        // The viewer is pushed over this screen; the sheet would otherwise stay up over it.
        onPreview = openSharedChat?.let { open ->
            {
                (state as? ShareSheetState.Linked)?.shareId?.let { shareId ->
                    onDismiss()
                    open(shareId)
                }
            }
        },
        onManageAccess = controller::openAccess,
        access = access,
        accessActions = accessActions,
        onCloseAccess = controller::closeAccess,
    )
}

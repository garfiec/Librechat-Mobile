package com.garfiec.librechat.core.ui.components.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.acl.AclState
import com.garfiec.librechat.core.model.share.FilesOption
import com.garfiec.librechat.core.model.share.ShareAudience
import com.garfiec.librechat.core.model.share.ShareSheetBusy
import com.garfiec.librechat.core.model.share.ShareSheetError
import com.garfiec.librechat.core.model.share.ShareSheetState
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveSwitch
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.PlatformBackHandler
import com.garfiec.librechat.core.ui.components.acl.AclSharingActions
import com.garfiec.librechat.core.ui.components.acl.AclSharingSection
import com.garfiec.librechat.core.ui.media.rememberShareText
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.acl_back
import com.garfiec.librechat.core.ui.resources.acl_public_anyone
import com.garfiec.librechat.core.ui.resources.acl_public_signed_in
import com.garfiec.librechat.core.ui.resources.cd_loading
import com.garfiec.librechat.core.ui.resources.error_banner_retry
import com.garfiec.librechat.core.ui.resources.share_sheet_audience_anyone
import com.garfiec.librechat.core.ui.resources.share_sheet_audience_signed_in
import com.garfiec.librechat.core.ui.resources.share_sheet_cancel
import com.garfiec.librechat.core.ui.resources.share_sheet_cannot_create
import com.garfiec.librechat.core.ui.resources.share_sheet_confirm_delete_message
import com.garfiec.librechat.core.ui.resources.share_sheet_confirm_delete_title
import com.garfiec.librechat.core.ui.resources.share_sheet_confirm_update_keeps_url
import com.garfiec.librechat.core.ui.resources.share_sheet_confirm_update_new_url
import com.garfiec.librechat.core.ui.resources.share_sheet_confirm_update_title
import com.garfiec.librechat.core.ui.resources.share_sheet_copied
import com.garfiec.librechat.core.ui.resources.share_sheet_copy
import com.garfiec.librechat.core.ui.resources.share_sheet_create
import com.garfiec.librechat.core.ui.resources.share_sheet_delete
import com.garfiec.librechat.core.ui.resources.share_sheet_error_content_blocked
import com.garfiec.librechat.core.ui.resources.share_sheet_error_forbidden
import com.garfiec.librechat.core.ui.resources.share_sheet_error_generic
import com.garfiec.librechat.core.ui.resources.share_sheet_error_network
import com.garfiec.librechat.core.ui.resources.share_sheet_error_no_messages
import com.garfiec.librechat.core.ui.resources.share_sheet_error_not_found
import com.garfiec.librechat.core.ui.resources.share_sheet_error_rate_limited
import com.garfiec.librechat.core.ui.resources.share_sheet_error_target_not_saved
import com.garfiec.librechat.core.ui.resources.share_sheet_hide_qr
import com.garfiec.librechat.core.ui.resources.share_sheet_include_files
import com.garfiec.librechat.core.ui.resources.share_sheet_include_files_description
import com.garfiec.librechat.core.ui.resources.share_sheet_include_files_on_update
import com.garfiec.librechat.core.ui.resources.share_sheet_manage_access
import com.garfiec.librechat.core.ui.resources.share_sheet_preview
import com.garfiec.librechat.core.ui.resources.share_sheet_qr_description
import com.garfiec.librechat.core.ui.resources.share_sheet_reduced_note
import com.garfiec.librechat.core.ui.resources.share_sheet_share
import com.garfiec.librechat.core.ui.resources.share_sheet_show_qr
import com.garfiec.librechat.core.ui.resources.share_sheet_snapshot_note
import com.garfiec.librechat.core.ui.resources.share_sheet_title
import com.garfiec.librechat.core.ui.resources.share_sheet_update
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** How long "Copied" replaces the copy icon after a tap. */
private const val COPIED_FEEDBACK_MS = 2_000L

private val QrSize = 192.dp
private val QrShape = RoundedCornerShape(12.dp)

/** Duration of the slide between the share page and the Manage access page. */
private const val PAGE_SWAP_MS = 250

/**
 * The one share sheet: every Share entry point and a tapped card on the Shared chats page open it.
 * It renders [state] and reports taps; the host's controller does the lookup and the writes.
 *
 * Copy feedback and errors render inside the sheet, since a snackbar would sit behind its scrim.
 * [onCopy] writes the link to the clipboard (the host owns the clipboard); the sheet shows "Copied".
 * Share… opens the system share sheet with the link. Update and Delete ask first.
 *
 * The sheet has a second page, Manage access, shown while [access] is open on a link that offers
 * it: [onManageAccess] asks the host to open it, [onCloseAccess] to go back. On Android back
 * returns to the share page; iOS has the page's back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLinkSheet(
    state: ShareSheetState,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onIncludeFilesChange: (Boolean) -> Unit,
    onCopy: (url: String) -> Unit,
    onRetry: () -> Unit,
    onPreview: () -> Unit = {},
    onManageAccess: () -> Unit = {},
    access: AclState = AclState.Closed,
    accessActions: AclSharingActions = AclSharingActions.None,
    onCloseAccess: () -> Unit = {},
) {
    if (state == ShareSheetState.Closed) return
    val onAccessPage = access != AclState.Closed && ShareSheetControls.of(state).manageAccess
    AdaptiveModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { LowProfileDragHandle() }) {
        // Inside the sheet: on Android its content is its own window, so an outer handler never
        // sees back. Takes precedence over back-to-dismiss so the access page pops to the share page.
        PlatformBackHandler(enabled = onAccessPage, onBack = onCloseAccess)
        AnimatedContent(
            targetState = onAccessPage,
            transitionSpec = {
                // Forward slides in from the end, back from the start, reading as depth.
                val direction = if (targetState) 1 else -1
                val fade = tween<Float>(PAGE_SWAP_MS)
                (slideInHorizontally(tween(PAGE_SWAP_MS)) { width -> direction * width } + fadeIn(fade))
                    .togetherWith(slideOutHorizontally(tween(PAGE_SWAP_MS)) { width -> -direction * width } + fadeOut(fade))
                    .using(SizeTransform { _, _ -> tween(PAGE_SWAP_MS) })
            },
            label = "shareSheetPage",
        ) { accessPage ->
            if (accessPage) {
                ManageAccessPage(
                    audience = (state as? ShareSheetState.Linked)?.audience ?: ShareAudience.SIGNED_IN,
                    access = access,
                    actions = accessActions,
                    onBack = onCloseAccess,
                )
            } else {
                ShareLinkSheetContent(
                    state = state,
                    onCreate = onCreate,
                    onUpdate = onUpdate,
                    onDelete = onDelete,
                    onIncludeFilesChange = onIncludeFilesChange,
                    onCopy = onCopy,
                    onRetry = onRetry,
                    onPreview = onPreview,
                    onManageAccess = onManageAccess,
                )
            }
        }
    }
}

/**
 * The Manage access page: a back row, then who can open the link. The public switch is worded as
 * the server's policy: anyone with the link, or anyone signed in to the server.
 */
@Composable
internal fun ManageAccessPage(
    audience: ShareAudience,
    access: AclState,
    actions: AclSharingActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.acl_back))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(Res.string.share_sheet_manage_access),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        AclSharingSection(
            state = access,
            publicLabel = stringResource(
                when (audience) {
                    ShareAudience.ANYONE -> Res.string.acl_public_anyone
                    ShareAudience.SIGNED_IN -> Res.string.acl_public_signed_in
                },
            ),
            actions = actions,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** The sheet's page, without the sheet: what [ShareLinkSheet] shows, and what previews render. */
@Composable
fun ShareLinkSheetContent(
    state: ShareSheetState,
    onCreate: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onIncludeFilesChange: (Boolean) -> Unit,
    onCopy: (url: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onPreview: () -> Unit = {},
    onManageAccess: () -> Unit = {},
) {
    val controls = ShareSheetControls.of(state)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = stringResource(Res.string.share_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        when {
            controls.progress -> LoadingBody()
            controls.retry -> FailedBody(error = controls.error, onRetry = onRetry)
            controls.create -> NoLinkBody(controls, onCreate = onCreate, onIncludeFilesChange = onIncludeFilesChange)
            controls.url != null -> LinkedBody(
                controls = controls,
                url = controls.url,
                updateKeepsUrl = (state as? ShareSheetState.Linked)?.updateKeepsUrl ?: true,
                actions = LinkedActions(onUpdate, onDelete, onIncludeFilesChange, onCopy, onPreview, onManageAccess),
            )
        }
    }
}

@Immutable
private class LinkedActions(
    val onUpdate: () -> Unit,
    val onDelete: () -> Unit,
    val onIncludeFilesChange: (Boolean) -> Unit,
    val onCopy: (String) -> Unit,
    val onPreview: () -> Unit,
    val onManageAccess: () -> Unit,
)

@Composable
private fun LoadingBody() {
    val description = stringResource(Res.string.cd_loading)
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AdaptiveCircularProgressIndicator()
    }
}

@Composable
private fun FailedBody(error: ShareSheetError?, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = errorText(error ?: ShareSheetError.Generic),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(16.dp))
        AdaptiveOutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.error_banner_retry))
        }
    }
}

@Composable
private fun NoLinkBody(
    controls: ShareSheetControls,
    onCreate: () -> Unit,
    onIncludeFilesChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        controls.audience?.let { AudienceLine(it) }
        controls.error?.let { ErrorLine(it) }
        controls.files?.let { files ->
            FilesSwitchRow(
                files = files,
                appliesOnUpdate = false,
                enabled = controls.enabled,
                onChange = onIncludeFilesChange,
            )
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            AdaptiveButton(onClick = onCreate, enabled = controls.createEnabled, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(stringResource(Res.string.share_sheet_create), busy = controls.busy == ShareSheetBusy.CREATING)
            }
            if (controls.cannotCreateReason) {
                Text(
                    text = stringResource(Res.string.share_sheet_cannot_create),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LinkedBody(
    controls: ShareSheetControls,
    url: String,
    updateKeepsUrl: Boolean,
    actions: LinkedActions,
) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    // Shown by default, as on web; the row hides it to shorten the sheet.
    var showQr by rememberSaveable { mutableStateOf(true) }
    val shareText = rememberShareText()

    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            controls.snapshotNote -> NoteLine(stringResource(Res.string.share_sheet_snapshot_note))
            controls.reducedNote -> NoteLine(stringResource(Res.string.share_sheet_reduced_note))
        }
        controls.error?.let { ErrorLine(it) }
        UrlField(url = url, onCopy = { actions.onCopy(url) })
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdaptiveButton(onClick = { shareText(url, null) }, enabled = controls.enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.share_sheet_share))
            }
            if (controls.update) {
                AdaptiveFilledTonalButton(
                    onClick = { confirm = Confirm.UPDATE },
                    enabled = controls.enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ButtonLabel(stringResource(Res.string.share_sheet_update), busy = controls.busy == ShareSheetBusy.UPDATING)
                }
            }
        }
        SheetRow(
            icon = Icons.Outlined.QrCode2,
            label = stringResource(if (showQr) Res.string.share_sheet_hide_qr else Res.string.share_sheet_show_qr),
            onClick = { showQr = !showQr },
        )
        if (showQr) QrCode(url)
        controls.files?.let { files ->
            FilesSwitchRow(
                files = files,
                appliesOnUpdate = controls.filesAppliesOnUpdate,
                enabled = controls.enabled,
                onChange = actions.onIncludeFilesChange,
            )
        }
        // Opens the shared-chat viewer on the owner's own link; hidden until the viewer exists.
        if (controls.preview) {
            SheetRow(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(Res.string.share_sheet_preview), actions.onPreview)
        }
        // Opens the Manage access page: role SHARE plus the SHARE bit on this link's ACL.
        if (controls.manageAccess) {
            SheetRow(Icons.Outlined.Group, stringResource(Res.string.share_sheet_manage_access), actions.onManageAccess)
        }
        if (controls.delete) {
            SheetRow(
                icon = Icons.Outlined.DeleteOutline,
                label = stringResource(Res.string.share_sheet_delete),
                onClick = { confirm = Confirm.DELETE },
                enabled = controls.enabled,
                busy = controls.busy == ShareSheetBusy.DELETING,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when (confirm) {
            Confirm.UPDATE -> ConfirmDialog(
                title = stringResource(Res.string.share_sheet_confirm_update_title),
                message = stringResource(
                    if (updateKeepsUrl) Res.string.share_sheet_confirm_update_keeps_url else Res.string.share_sheet_confirm_update_new_url,
                ),
                confirmLabel = stringResource(Res.string.share_sheet_update),
                destructive = !updateKeepsUrl,
                onConfirm = {
                    confirm = null
                    actions.onUpdate()
                },
                onDismiss = { confirm = null },
            )
            Confirm.DELETE -> ConfirmDialog(
                title = stringResource(Res.string.share_sheet_confirm_delete_title),
                message = stringResource(Res.string.share_sheet_confirm_delete_message),
                confirmLabel = stringResource(Res.string.share_sheet_delete),
                destructive = true,
                onConfirm = {
                    confirm = null
                    actions.onDelete()
                },
                onDismiss = { confirm = null },
            )
            null -> Unit
        }
    }
}

private enum class Confirm { UPDATE, DELETE }

@Composable
private fun UrlField(url: String, onCopy: () -> Unit) {
    var copyTaps by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copyTaps) {
        if (copyTaps > 0) {
            copied = true
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }
    AdaptiveOutlinedTextField(
        value = url,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        trailingIcon = {
            IconButton(onClick = {
                onCopy()
                copyTaps++
            }) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(Res.string.share_sheet_copy),
                    tint = if (copied) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        },
        supportingText = if (copied) {
            {
                Text(
                    text = stringResource(Res.string.share_sheet_copied),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun QrCode(url: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        // White in both themes on purpose: a scanner needs dark modules on a light quiet zone.
        Surface(color = Color.White, shape = QrShape) {
            Image(
                painter = rememberQrCodePainter(url),
                contentDescription = stringResource(Res.string.share_sheet_qr_description),
                modifier = Modifier.padding(16.dp).size(QrSize),
            )
        }
    }
}

@Composable
private fun AudienceLine(audience: ShareAudience) {
    val (icon, text) = when (audience) {
        ShareAudience.ANYONE -> Icons.Outlined.Public to Res.string.share_sheet_audience_anyone
        ShareAudience.SIGNED_IN -> Icons.Outlined.Lock to Res.string.share_sheet_audience_signed_in
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

@Composable
private fun ErrorLine(error: ShareSheetError) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = errorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun FilesSwitchRow(
    files: FilesOption,
    appliesOnUpdate: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = files.includeFiles, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.share_sheet_include_files),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val description = stringResource(Res.string.share_sheet_include_files_description)
            Text(
                text = if (appliesOnUpdate) {
                    description + " " + stringResource(Res.string.share_sheet_include_files_on_update)
                } else {
                    description
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        AdaptiveSwitch(checked = files.includeFiles, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (color == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.onSurfaceVariant else color,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (busy) AdaptiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun ButtonLabel(text: String, busy: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (busy) {
            AdaptiveCircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.share_sheet_cancel)) }
        },
    )
}

@Composable
private fun errorText(error: ShareSheetError): String = when (error) {
    ShareSheetError.Network -> stringResource(Res.string.share_sheet_error_network)
    ShareSheetError.Forbidden -> stringResource(Res.string.share_sheet_error_forbidden)
    ShareSheetError.NotFound -> stringResource(Res.string.share_sheet_error_not_found)
    ShareSheetError.RateLimited -> stringResource(Res.string.share_sheet_error_rate_limited)
    ShareSheetError.TargetNotSaved -> stringResource(Res.string.share_sheet_error_target_not_saved)
    ShareSheetError.NoMessages -> stringResource(Res.string.share_sheet_error_no_messages)
    is ShareSheetError.ContentBlocked -> listOfNotNull(
        stringResource(Res.string.share_sheet_error_content_blocked),
        error.serverMessage?.takeIf { it.isNotBlank() },
    ).joinToString("\n")
    ShareSheetError.Generic -> stringResource(Res.string.share_sheet_error_generic)
}

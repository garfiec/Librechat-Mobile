package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveSegmentedChoice
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.viewmodel.PendingUploadFile
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Asks which way each picked file should be delivered, before anything is uploaded.
 *
 * A `ModalBottomSheet` rather than an `AlertDialog`: the dialog's text slot has no scroll
 * modifier, so a multi-file pick would clip its last rows out of reach.
 *
 * Files with only one usable mode are shown with their control disabled rather than hidden — the
 * user picked them, and silently omitting them reads as them having been dropped. The sheet is
 * only opened when at least one file genuinely has a choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadRoutingSheet(
    files: List<PendingUploadFile>,
    /** Route change for the row at this index — position, not value: a batch can hold the same
     *  file twice, and matching by value would flip both rows on one tap. */
    onRouteChange: (Int, UploadRoute) -> Unit,
    onApplyToAll: (UploadRoute) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        UploadRoutingContent(
            files = files,
            onRouteChange = onRouteChange,
            onApplyToAll = onApplyToAll,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun UploadRoutingContent(
    files: List<PendingUploadFile>,
    onRouteChange: (Int, UploadRoute) -> Unit,
    onApplyToAll: (UploadRoute) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The picker is `OpenMultipleDocuments`, so the batch is unbounded while
            // `ModalBottomSheet`'s content slot is a plain Column that Material adds no scroll
            // to. Without this a six-file pick pushes Attach/Cancel past the sheet's max height
            // and the only way out is to back out, losing the whole pick.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (files.size == 1) {
                stringResource(Res.string.upload_routing_sheet_title_single)
            } else {
                stringResource(Res.string.upload_routing_sheet_title)
            },
            style = MaterialTheme.typography.titleMedium,
        )

        files.forEachIndexed { index, staged ->
            UploadRoutingRow(
                staged = staged,
                onRouteChange = { onRouteChange(index, it) },
            )
        }

        if (files.count { UploadRoute.PROVIDER in it.routes && UploadRoute.TEXT in it.routes } > 1) {
            // The two choices wrap under the label as a pair, and apart only when even a line of
            // their own is too narrow.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.upload_routing_apply_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onApplyToAll(UploadRoute.PROVIDER) }) {
                        Text(stringResource(Res.string.upload_routing_option_provider))
                    }
                    TextButton(onClick = { onApplyToAll(UploadRoute.TEXT) }) {
                        Text(stringResource(Res.string.upload_routing_option_text))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
            AdaptiveButton(onClick = onConfirm) {
                Text(stringResource(Res.string.upload_routing_attach))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadRoutingRow(
    staged: PendingUploadFile,
    onRouteChange: (UploadRoute) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = staged.file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // A file with one usable mode shows the two delivery modes, disabled, with that one selected.
        val routes = if (staged.choosable) SHEET_ROUTES.filter { it in staged.routes } else DELIVERY_ROUTES
        AdaptiveSegmentedChoice(
            options = routes.map { stringResource(it.sheetLabel()) },
            selectedIndex = routes.indexOf(staged.route).coerceAtLeast(0),
            onSelect = { onRouteChange(routes[it]) },
            modifier = Modifier.fillMaxWidth(),
            enabled = staged.choosable,
        )
        Text(
            text = routeExplanation(staged),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One line saying what will actually happen — the thing "to the model" / "as text" doesn't say.
 * Whether the model can read the file is judged on the two delivery modes alone: file search
 * being on offer doesn't make the other mode readable.
 */
@Composable
private fun routeExplanation(staged: PendingUploadFile): String {
    val deliveryChoice = DELIVERY_ROUTES.all { it in staged.routes }
    return when {
        staged.route == UploadRoute.FILE_SEARCH -> stringResource(Res.string.upload_route_file_search_why)
        !deliveryChoice && staged.route == UploadRoute.PROVIDER ->
            stringResource(Res.string.upload_routing_why_provider_only)
        !deliveryChoice -> stringResource(Res.string.upload_routing_why_text_only)
        staged.route == UploadRoute.PROVIDER -> stringResource(Res.string.upload_routing_why_provider)
        else -> stringResource(Res.string.upload_routing_why_text)
    }
}

private fun UploadRoute.sheetLabel(): StringResource = when (this) {
    UploadRoute.TEXT -> Res.string.upload_routing_option_text
    UploadRoute.FILE_SEARCH -> Res.string.upload_route_file_search
    UploadRoute.PROVIDER, UploadRoute.CODE -> Res.string.upload_routing_option_provider
}

private val DELIVERY_ROUTES = listOf(UploadRoute.PROVIDER, UploadRoute.TEXT)

/**
 * Every route the sheet can offer, in order; each row shows the ones its file may take.
 * [UploadRoute.CODE] is only ever an explicit pick.
 */
private val SHEET_ROUTES = DELIVERY_ROUTES + UploadRoute.FILE_SEARCH

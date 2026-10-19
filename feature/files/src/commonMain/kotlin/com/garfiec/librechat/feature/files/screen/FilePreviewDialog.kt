package com.garfiec.librechat.feature.files.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDialog
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.rememberAbsoluteTimestamp
import com.garfiec.librechat.feature.files.FilePreviewDisplayData
import com.garfiec.librechat.feature.files.platform.PdfPreview
import com.garfiec.librechat.feature.files.resources.*
import com.garfiec.librechat.feature.files.resources.Res
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewDialog(
    file: FilePreviewDisplayData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDownloadFile: (suspend (fileId: String, userId: String?) -> ByteArray?)? = null,
) {
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AdaptiveScaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                AdaptiveTopBar(
                    spec = AdaptiveTopBarSpec(
                        navigation = BarNavigation(
                            BarIcons.Close,
                            stringResource(Res.string.cd_close_preview),
                            onDismiss,
                        ),
                        title = BarTitle(file.filename, maxLines = 1),
                        actions = emptyList(),
                    ),
                )
            },
        ) { padding ->
            when {
                file.type == "application/pdf" -> {
                    PdfPreview(
                        file = file,
                        onDownloadFile = onDownloadFile,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                file.type.startsWith("text/") || file.type == "application/json" ||
                    file.type == "application/xml" || file.type == "application/javascript" -> {
                    TextContentPreview(
                        file = file,
                        onDownloadFile = onDownloadFile,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
                else -> {
                    FileInfoCard(
                        file = file,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    )
                }
            }
        }
    }
}

// -- Text Content Preview --

private sealed interface TextLoadState {
    data object Loading : TextLoadState
    data class Success(val content: String, val truncated: Boolean) : TextLoadState

    /** [detail] adds to the fallback's own "could not load" line; null when there is nothing to add. */
    data class Error(val detail: StringResource?) : TextLoadState
}

@Composable
private fun TextContentPreview(
    file: FilePreviewDisplayData,
    onDownloadFile: (suspend (fileId: String, userId: String?) -> ByteArray?)?,
    modifier: Modifier = Modifier,
) {
    var loadState by remember { mutableStateOf<TextLoadState>(TextLoadState.Loading) }

    LaunchedEffect(file.fileId) {
        loadState = TextLoadState.Loading
        suspendRunCatching {
            val bytes = onDownloadFile?.invoke(file.fileId, file.userId)
            if (bytes == null) {
                loadState = TextLoadState.Error(Res.string.failed_to_download_file)
                return@LaunchedEffect
            }
            val content = bytes.decodeToString()
            val truncated = content.length > 500_000
            loadState = TextLoadState.Success(if (truncated) content.take(500_000) else content, truncated)
        }.onFailure { e ->
            Logger.e(e) { "TextContentPreview: failed to load text content" }
            loadState = TextLoadState.Error(detail = null)
        }
    }

    when (val state = loadState) {
        is TextLoadState.Loading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AdaptiveCircularProgressIndicator()
                    Text(
                        text = stringResource(Res.string.loading_file_content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is TextLoadState.Error -> {
            TextErrorFallback(
                file = file,
                errorMessage = state.detail?.let { stringResource(it) },
                modifier = modifier,
            )
        }
        is TextLoadState.Success -> {
            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            Column(
                modifier = modifier
                    .verticalScroll(verticalScrollState)
                    .padding(16.dp),
            ) {
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${file.type} - ${file.formattedSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                AdaptiveDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState),
                ) {
                    val truncationNote = stringResource(Res.string.content_truncated)
                    Text(
                        text = if (state.truncated) "${state.content}\n\n--- $truncationNote ---" else state.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.widthIn(min = 600.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TextErrorFallback(
    file: FilePreviewDisplayData,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    FileInfoFallback(
        file = file,
        icon = Icons.Default.ErrorOutline,
        iconTint = MaterialTheme.colorScheme.error,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.could_not_load_file_content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// -- Shared Components --

@Composable
internal fun FileInfoCard(
    file: FilePreviewDisplayData,
    modifier: Modifier = Modifier,
) {
    FileInfoFallback(
        file = file,
        modifier = modifier,
    )
}

/**
 * The card a preview shows when it has nothing better: the file's type tile (or [icon]) and name,
 * [messages] saying why, and its details. It scrolls, because a phone on its side leaves it about 300dp.
 */
@Composable
internal fun FileInfoFallback(
    file: FilePreviewDisplayData,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    showDetails: Boolean = true,
    messages: @Composable ColumnScope.() -> Unit = {},
) {
    Box(
        modifier = modifier.verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = iconTint,
                    )
                } else {
                    FileTypeTile(
                        kind = remember(file.filename, file.type) { fileKindOf(file.filename, file.type) },
                        size = 64.dp,
                    )
                }

                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                messages()

                if (showDetails) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InfoRow(stringResource(Res.string.info_type), file.type)
                        InfoRow(stringResource(Res.string.info_size), file.formattedSize)
                        file.createdAt?.let { InfoRow(stringResource(Res.string.info_created), rememberAbsoluteTimestamp(it)) }
                        file.source?.let { InfoRow(stringResource(Res.string.info_source), it) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Two lines, so a long timestamp or type wraps instead of losing its end.
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
        )
    }
}

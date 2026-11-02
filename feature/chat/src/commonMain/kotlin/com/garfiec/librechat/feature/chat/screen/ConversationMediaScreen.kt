package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.common.extensions.formatByteSize
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptivePillChoice
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveSnackbarHost
import com.garfiec.librechat.core.ui.components.ChoiceFallback
import com.garfiec.librechat.core.ui.components.EmptyState
import com.garfiec.librechat.core.ui.components.LibreChatTopBar
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.selectedTabIndex
import com.garfiec.librechat.core.ui.media.FileAction
import com.garfiec.librechat.core.ui.media.MediaActionBar
import com.garfiec.librechat.core.ui.media.MediaItem
import com.garfiec.librechat.core.ui.media.MediaViewerHost
import com.garfiec.librechat.core.ui.media.mediaThumbnail
import com.garfiec.librechat.core.ui.media.rememberSaveImageToGallery
import com.garfiec.librechat.core.ui.media.rememberShareImage
import com.garfiec.librechat.feature.chat.components.AttachmentFileActions
import com.garfiec.librechat.feature.chat.components.LocalAttachmentDownloader
import com.garfiec.librechat.feature.chat.components.LocalFileNotice
import com.garfiec.librechat.feature.chat.components.LocalOpenPdf
import com.garfiec.librechat.feature.chat.components.PdfOverlayHost
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactButton
import com.garfiec.librechat.feature.chat.components.artifact.LocalOpenArtifact
import com.garfiec.librechat.feature.chat.components.rememberAttachmentFileActions
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_close
import com.garfiec.librechat.feature.chat.resources.cd_image
import com.garfiec.librechat.feature.chat.resources.cd_save_to_device
import com.garfiec.librechat.feature.chat.resources.cd_share_image
import com.garfiec.librechat.feature.chat.resources.expired_explainer
import com.garfiec.librechat.feature.chat.resources.media_empty_artifacts
import com.garfiec.librechat.feature.chat.resources.media_empty_files
import com.garfiec.librechat.feature.chat.resources.media_empty_links
import com.garfiec.librechat.feature.chat.resources.media_empty_media
import com.garfiec.librechat.feature.chat.resources.media_gallery_title
import com.garfiec.librechat.feature.chat.resources.media_scope_this_branch
import com.garfiec.librechat.feature.chat.resources.media_scope_whole_chat
import com.garfiec.librechat.feature.chat.resources.media_tab_artifacts
import com.garfiec.librechat.feature.chat.resources.media_tab_files
import com.garfiec.librechat.feature.chat.resources.media_tab_links
import com.garfiec.librechat.feature.chat.resources.media_tab_media
import com.garfiec.librechat.feature.chat.util.ConversationFile
import com.garfiec.librechat.feature.chat.util.ConversationFileTap
import com.garfiec.librechat.feature.chat.util.ConversationLink
import com.garfiec.librechat.feature.chat.util.conversationFileTap
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMediaViewModel
import com.garfiec.librechat.feature.chat.viewmodel.MediaScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class MediaTab { MEDIA, FILES, LINKS, ARTIFACTS }

private val MediaThumbShape = RoundedCornerShape(4.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationMediaScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationMediaViewModel = koinViewModel { parametersOf(conversationId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { MediaTab.entries.size }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showNotice: (String) -> Unit = remember(scope, snackbarHostState) {
        { message ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }
    val download: suspend (Attachment) -> ByteArray? = remember(viewModel) { viewModel::downloadFileBytes }

    val saveImage = rememberSaveImageToGallery()
    val shareImage = rememberShareImage()
    val saveDescription = stringResource(Res.string.cd_save_to_device)
    val shareDescription = stringResource(Res.string.cd_share_image)
    CompositionLocalProvider(
        LocalAttachmentDownloader provides download,
        LocalFileNotice provides showNotice,
    ) {
        // Screen-lifetime, not per pager page: a page swiped away would cancel the open it started.
        val fileActions = rememberAttachmentFileActions()
        PdfOverlayHost(onDownload = download) {
            MediaViewerHost(
                preview = uiState.mediaPreview,
                onDismiss = viewModel::closeMedia,
                closeContentDescription = stringResource(Res.string.cd_close),
                defaultContentDescription = stringResource(Res.string.cd_image),
                actions = { item ->
                    MediaActionBar(
                        item = item,
                        onSave = saveImage,
                        onShare = shareImage,
                        saveContentDescription = saveDescription,
                        shareContentDescription = shareDescription,
                    )
                },
            ) {
                AdaptiveScaffold(
                    modifier = modifier,
                    snackbarHost = { AdaptiveSnackbarHost(snackbarHostState) },
                    topBar = {
                        LibreChatTopBar(
                            title = stringResource(Res.string.media_gallery_title),
                            onNavigateBack = onNavigateBack,
                        )
                    },
                ) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                        AdaptivePillChoice(
                            options = listOf(
                                stringResource(Res.string.media_tab_media),
                                stringResource(Res.string.media_tab_files),
                                stringResource(Res.string.media_tab_links),
                                stringResource(Res.string.media_tab_artifacts),
                            ),
                            selectedIndex = pagerState.selectedTabIndex(),
                            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                            // Tabs over the page: a radio list here would push the gallery down.
                            fallback = ChoiceFallback.TWO_LINES,
                        )

                        ScopeToggle(
                            scope = uiState.scope,
                            onScopeChange = viewModel::setScope,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            when {
                                uiState.isLoading -> LoadingIndicator(modifier = Modifier.fillMaxSize())
                                else -> when (MediaTab.entries[page]) {
                                    MediaTab.MEDIA -> MediaGrid(uiState.media, onOpen = viewModel::openMedia)
                                    MediaTab.FILES -> FilesList(uiState.files, fileActions)
                                    MediaTab.LINKS -> LinksList(uiState.links)
                                    MediaTab.ARTIFACTS -> ArtifactsList(uiState.artifacts)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeToggle(
    scope: MediaScope,
    onScopeChange: (MediaScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scopes = listOf(MediaScope.ACTIVE_BRANCH, MediaScope.ENTIRE)
    AdaptivePillChoice(
        options = listOf(
            stringResource(Res.string.media_scope_this_branch),
            stringResource(Res.string.media_scope_whole_chat),
        ),
        selectedIndex = scopes.indexOf(scope),
        onSelect = { onScopeChange(scopes[it]) },
        modifier = modifier,
        fallback = ChoiceFallback.TWO_LINES,
    )
}

@Composable
private fun MediaGrid(
    media: List<MediaItem>,
    onOpen: (url: String) -> Unit,
) {
    if (media.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.media_empty_media),
            icon = Icons.Outlined.PermMedia,
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(media, key = { it.url }) { item ->
            AsyncImage(
                model = item.url,
                contentDescription = item.contentDescription.ifBlank { null },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .mediaThumbnail(item.url, MediaThumbShape)
                    .clickable { onOpen(item.url) },
            )
        }
    }
}

@Composable
private fun FilesList(files: List<ConversationFile>, fileActions: AttachmentFileActions) {
    if (files.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.media_empty_files),
            icon = Icons.AutoMirrored.Outlined.InsertDriveFile,
        )
        return
    }
    val openPdf = LocalOpenPdf.current
    val openArtifact = LocalOpenArtifact.current
    val notice = LocalFileNotice.current
    val expiredExplainer = stringResource(Res.string.expired_explainer)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(files, key = { it.key }) { file ->
            val busy = fileActions.busyAction(file.attachment) != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) {
                        when (val tap = conversationFileTap(file.attachment, canOpenArtifact = openArtifact != null)) {
                            is ConversationFileTap.Pdf -> openPdf(tap.fileId, file.filename)
                            is ConversationFileTap.OpenArtifact -> openArtifact?.invoke(tap.artifact, listOf(tap.artifact))
                            ConversationFileTap.OpenWith -> fileActions.run(file.attachment, FileAction.OPEN_IN)
                            ConversationFileTap.Expired -> notice(expiredExplainer)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        text = file.filename,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        file.bytes?.let { formatByteSize(it) },
                        file.type,
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (busy) {
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.padding(start = 16.dp).size(24.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinksList(links: List<ConversationLink>) {
    if (links.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.media_empty_links),
            icon = Icons.Outlined.Link,
        )
        return
    }
    val uriHandler = LocalUriHandler.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(links, key = { it.url }) { link ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { runCatching { uriHandler.openUri(link.url) } }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = link.host,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = link.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactsList(artifacts: List<List<Artifact>>) {
    if (artifacts.isEmpty()) {
        EmptyState(
            title = stringResource(Res.string.media_empty_artifacts),
            icon = Icons.Outlined.Dashboard,
        )
        return
    }
    // Tapping a card hands the artifact (with its full version history) to the screen-level
    // opener, which presents it as a bottom sheet or full-screen route per the display pref.
    val openArtifact = LocalOpenArtifact.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(artifacts, key = { it.first().identifier }) { versions ->
            ArtifactButton(
                artifact = versions.last(),
                onClick = { openArtifact?.invoke(versions.last(), versions) },
                versionCount = versions.size,
            )
        }
    }
}

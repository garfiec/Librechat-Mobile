package com.garfiec.librechat.core.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.PredictiveBackHandler
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import com.github.panpf.zoomimage.rememberCoilZoomState
import kotlinx.coroutines.CancellationException

/**
 * A single zoomable/pannable media item displayed by [ZoomableMediaPager].
 *
 * [url] is the resolved, ready-to-load image URL and doubles as the stable pager key,
 * so callers must dedupe by URL before passing the list in.
 */
@Immutable
data class MediaItem(
    val url: String,
    val contentDescription: String,
    val filename: String? = null,
)

/**
 * Open-state for the media viewer, held in a ViewModel's UI state so it survives rotation.
 * `null` (the absence of this object) means the viewer is closed.
 */
@Immutable
data class MediaPreviewState(
    val items: List<MediaItem>,
    val initialIndex: Int,
)

/**
 * Full-screen, Google-Photos-style media viewer, shown by [MediaViewerHost].
 *
 * - Fit-to-screen is the rest state ([ContentScale.Fit]); pinch / double-tap zooms in,
 *   drag pans, and at fit scale a horizontal swipe pages to the previous/next item.
 *   Edge-of-image → pager handoff is handled by ZoomImage's nested-scroll integration.
 * - Subsampling (large-image tiling) is auto-enabled by the Coil integration.
 * - A downward drag at fit scale, the back gesture and the close button all leave through
 *   [MediaDismissTransition]: the image flies back into its thumbnail, then [onDismiss] runs.
 *
 * Images load through the app's Coil singleton ([SingletonImageLoader]); auth lives in that
 * loader's Ktor fetcher, exactly like every other `AsyncImage` call site. The pager therefore
 * takes no `imageLoader`/auth params.
 *
 * Each surface supplies its own toolbar buttons (save / share / download) via [actions];
 * core/ui owns only the close button + page counter.
 *
 * Rendered as an in-composition full-screen overlay (not a `Dialog`), so the screen behind shows
 * through while it is dismissed and its thumbnails share the viewer's coordinate space.
 */
// PredictiveBackHandler is deprecated in favour of NavigationEventHandler, which only lands in a
// later Compose; this stays on the working API until the Compose version is bumped.
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ZoomableMediaPager(
    items: List<MediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    thumbnails: MediaThumbnailRegistry,
    modifier: Modifier = Modifier,
    closeContentDescription: String = "",
    defaultContentDescription: String = "",
    actions: @Composable RowScope.(MediaItem) -> Unit = {},
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { currentOnDismiss() }
        return
    }
    val startIndex = initialIndex.coerceIn(0, items.size - 1)

    val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
    val transition = rememberMediaDismissTransition(thumbnails, onDismiss = { currentOnDismiss() })
    // Each composed page by index, for the back gesture and the close button, which act on the
    // current page from outside it. Read only in those callbacks, never in composition.
    val pages = remember { mutableMapOf<Int, MediaPageSource>() }
    val currentPage = { pages[pagerState.currentPage] }

    PredictiveBackHandler { progress ->
        try {
            transition.track(currentPage())
            progress.collect { event -> transition.back(event.progress) }
            transition.dismiss(currentPage())
        } catch (cancellation: CancellationException) {
            transition.settle()
            throw cancellation
        }
    }

    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { SingletonImageLoader.get(platformContext) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = transition.scrimAlpha) },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].url },
            userScrollEnabled = !transition.isDismissing,
        ) { page ->
            val item = items[page]
            val zoomState = rememberCoilZoomState()
            // Reset zoom on pages that have scrolled off-screen so returning shows the
            // fit-to-screen rest state rather than a stale zoomed view.
            LaunchedEffect(pagerState.settledPage) {
                if (pagerState.settledPage != page) {
                    zoomState.zoomable.reset()
                }
            }
            val source = remember(item.url, zoomState) { MediaPageSource(item.url, zoomState.zoomable) }
            DisposableEffect(source, page) {
                pages[page] = source
                onDispose { if (pages[page] === source) pages.remove(page) }
            }
            val dragHandler = remember(source, transition) {
                transition.dragHandler(source, pagerIdle = { !pagerState.isScrollInProgress })
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPlaced { source.coordinates = it }
                    .verticalDismissDrag(dragHandler)
                    .dismissFrame { transition.frame(source) },
                contentAlignment = Alignment.Center,
            ) {
                if (item.url.isNotBlank()) {
                    var loadState by remember(item.url) { mutableStateOf(MediaLoadState.LOADING) }
                    CoilZoomAsyncImage(
                        model = item.url,
                        contentDescription = item.contentDescription.ifBlank { defaultContentDescription },
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        zoomState = zoomState,
                        onLoading = { loadState = MediaLoadState.LOADING },
                        onSuccess = { loadState = MediaLoadState.SUCCESS },
                        onError = { loadState = MediaLoadState.ERROR },
                    )
                    // Loading spinner / failure placeholder so a slow or failed load isn't an
                    // indefinitely blank black screen (the old viewers had explicit states).
                    when (loadState) {
                        MediaLoadState.LOADING ->
                            AdaptiveCircularProgressIndicator(color = Color.White)
                        MediaLoadState.ERROR -> BrokenImagePlaceholder()
                        MediaLoadState.SUCCESS -> Unit
                    }
                } else {
                    // A blank URL has nothing to load and no callbacks fire, so show the same
                    // failure placeholder rather than an uninterpretable empty black page.
                    BrokenImagePlaceholder()
                }
            }
        }

        val currentItem = items[pagerState.currentPage.coerceIn(0, items.size - 1)]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .graphicsLayer { alpha = transition.chromeAlpha }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { transition.dismiss(currentPage()) }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = closeContentDescription,
                    tint = Color.White,
                )
            }
            if (items.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions(currentItem)
            }
        }
    }
}

private enum class MediaLoadState { LOADING, SUCCESS, ERROR }

/** Shown when a page fails to load or has no URL — the viewer's single failure affordance. */
@Composable
private fun BrokenImagePlaceholder() {
    Icon(
        imageVector = Icons.Filled.BrokenImage,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(64.dp),
    )
}

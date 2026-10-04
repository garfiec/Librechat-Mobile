package com.garfiec.librechat.core.ui.media

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.github.panpf.zoomimage.compose.zoom.ZoomableState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/** One viewer page, as the dismiss transition sees it. */
internal class MediaPageSource(val url: String, private val zoomable: ZoomableState) {
    /** Kept current by the page as it is placed. */
    var coordinates: LayoutCoordinates? = null

    val isZoomedOut: Boolean
        get() = zoomable.transform.scaleX <= zoomable.minScale * ZOOMED_OUT_TOLERANCE

    /** The page's own bounds; null while it isn't placed. */
    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.let { Rect(Offset.Zero, it.size.toSize()) }

    /** Where the image is drawn within [page]; the whole page until the image has a size. */
    fun imageRect(page: Rect): Rect = zoomable.contentDisplayRectF.takeUnless { it.isEmpty } ?: page
}

/**
 * Where the viewer's current page is drawn while it is being dismissed, and how much of the screen
 * behind shows through. The swipe-down drag, the back gesture and the close button all feed this one
 * holder; the pager only draws what it reports.
 *
 * At rest nothing is moved. While tracking, a drag or a back gesture moves and shrinks the page. A
 * dismiss then flies the page into its thumbnail ([MediaThumbnailRegistry]), or shrinks and fades
 * it where it is when no thumbnail is on screen, and only then calls [onDismissed]: the image has
 * landed on the thumbnail by the time the viewer goes.
 */
@Stable
internal class MediaDismissTransition(
    private val scope: CoroutineScope,
    private val thumbnails: MediaThumbnailRegistry,
    density: Density,
    private val onDismissed: () -> Unit,
) {
    private val dismissDistance = with(density) { DismissDistance.toPx() }
    private val dismissVelocity = with(density) { DismissVelocity.toPx() }
    private val backCorner = with(density) { BackCornerRadius.toPx() }

    /** The page being moved; null at rest. */
    private var active by mutableStateOf<MediaPageSource?>(null)
    private var pageHeight by mutableFloatStateOf(0f)
    private var dragOffset by mutableStateOf(Offset.Zero)
    private var backProgress by mutableFloatStateOf(0f)
    private var flight by mutableStateOf<Flight?>(null)
    private var flightProgress by mutableFloatStateOf(0f)

    // Where the scrim and the chrome were when the flight began; written before [flight], which is read first.
    private var flightStartScrim = 1f
    private var flightStartChrome = 1f
    private var animation: Job? = null
    private var landed = false

    val isDismissing: Boolean get() = flight != null

    val scrimAlpha: Float
        get() = if (flight != null) flightStartScrim * (1f - flightProgress) else trackingScrim()

    /** Opacity of the toolbar. */
    val chromeAlpha: Float
        get() = if (flight != null) {
            flightStartChrome * (1f - flightProgress / CHROME_FADE_END).coerceAtLeast(0f)
        } else {
            trackingChrome()
        }

    /** How [source] draws this frame; null leaves it untouched. */
    fun frame(source: MediaPageSource): PageFrame? {
        if (source !== active) return null
        flight?.let { return it.frame(flightProgress) }
        val page = source.bounds() ?: return null
        val transform = trackingTransform(page)
        return PageFrame(transform, transform.map(source.imageRect(page)), trackingCorner())
    }

    /** A drag or a back gesture began on [source]. */
    fun track(source: MediaPageSource?) {
        if (source == null || flight != null) return
        animation?.cancel()
        activate(source)
        thumbnails.hidden = targetFor(source)?.node
    }

    fun dragBy(delta: Offset) {
        if (flight == null) dragOffset += delta
    }

    fun back(progress: Float) {
        if (flight == null) backProgress = progress
    }

    /** The drag ended: dismiss when it went far or fast enough downward, otherwise spring back. */
    fun release(velocityY: Float) {
        val source = active ?: return
        val dismisses = (dragOffset.y > dismissDistance || velocityY > dismissVelocity) && velocityY > -dismissVelocity
        if (dismisses) dismiss(source) else settle()
    }

    /** Springs a tracked page back to rest. */
    fun settle() {
        if (flight != null || active == null) return
        val fromDrag = dragOffset
        val fromBack = backProgress
        animation?.cancel()
        animation = scope.launch {
            animate(1f, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { remaining, _ ->
                dragOffset = fromDrag * remaining
                backProgress = fromBack * remaining
            }
            active = null
            thumbnails.hidden = null
        }
    }

    /** Flies [source] from wherever it is drawn into its thumbnail, then dismisses the viewer. */
    fun dismiss(source: MediaPageSource?) {
        if (flight != null) return
        val page = source?.bounds()
        if (source == null || page == null) {
            onDismissed()
            return
        }
        animation?.cancel()
        activate(source)
        val image = source.imageRect(page)
        val from = trackingTransform(page)
        val target = targetFor(source)
        thumbnails.hidden = target?.node
        flightStartScrim = trackingScrim()
        flightStartChrome = trackingChrome()
        flightProgress = 0f
        flight = Flight(
            image = image,
            from = from,
            fromCorner = trackingCorner(),
            target = target?.bounds ?: from.map(image).scaledAboutCenter(FADE_OUT_SCALE),
            targetCorner = target?.cornerRadius ?: trackingCorner(),
            fade = target == null,
        )
        animation = scope.launch {
            animate(0f, 1f, animationSpec = FlightSpec) { progress, _ -> flightProgress = progress }
            thumbnails.hidden = null
            land()
        }
    }

    /**
     * The viewer is leaving composition. A flight it cuts short (an activity recreated mid-flight
     * cancels [scope]) still dismisses, or the caller's preview would reopen the viewer afterwards.
     */
    fun abandon() {
        thumbnails.hidden = null
        if (flight != null) land()
    }

    private fun land() {
        if (landed) return
        landed = true
        onDismissed()
    }

    fun dragHandler(source: MediaPageSource, pagerIdle: () -> Boolean): DismissDragHandler =
        object : DismissDragHandler {
            override fun canStart() = flight == null && pagerIdle() && source.isZoomedOut
            override fun onStart() = track(source)
            override fun onDrag(delta: Offset) = dragBy(delta)
            override fun onRelease(velocityY: Float) = release(velocityY)
        }

    private fun activate(source: MediaPageSource) {
        if (source !== active) {
            dragOffset = Offset.Zero
            backProgress = 0f
        }
        active = source
        pageHeight = source.bounds()?.height ?: 0f
    }

    private fun targetFor(source: MediaPageSource): ThumbnailTarget? =
        source.coordinates?.let { thumbnails.targetFor(source.url, it) }

    /** How far the drag has gone toward [distance], 0 to 1. */
    private fun dragFraction(distance: Float): Float =
        if (distance > 0f) (abs(dragOffset.y) / distance).coerceIn(0f, 1f) else 0f

    private fun trackingTransform(page: Rect): PageTransform = PageTransform.about(
        pivot = page.center,
        scale = (1f - DRAG_SHRINK * dragFraction(pageHeight * DRAG_SHRINK_DISTANCE)) * (1f - BACK_SHRINK * backProgress),
        offset = dragOffset,
    )

    private fun trackingCorner(): Float = backCorner * backProgress

    // The back gesture dims the scrim only down to MIN_BACK_SCRIM, so the viewer stays distinct from
    // the screen behind until it commits; a drag fades it out entirely, the screen behind the target.
    private fun trackingScrim(): Float =
        (1f - dragFraction(pageHeight * DRAG_SCRIM_DISTANCE)) * (1f - (1f - MIN_BACK_SCRIM) * backProgress)

    // The toolbar is gone by the time a drag would dismiss, or halfway through a back gesture.
    private fun trackingChrome(): Float =
        min(1f - dragFraction(dismissDistance), (1f - backProgress / CHROME_FADE_END).coerceIn(0f, 1f))
}

@Composable
internal fun rememberMediaDismissTransition(
    thumbnails: MediaThumbnailRegistry,
    onDismiss: () -> Unit,
): MediaDismissTransition {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val transition = remember(thumbnails, density) {
        MediaDismissTransition(scope, thumbnails, density) { currentOnDismiss() }
    }
    DisposableEffect(transition) {
        onDispose { transition.abandon() }
    }
    return transition
}

/** Draws this page as [frame] says: moved, seen through a rounded window and faded; untouched while it is null. */
internal fun Modifier.dismissFrame(frame: () -> PageFrame?): Modifier = this
    .graphicsLayer { alpha = frame()?.alpha ?: 1f }
    .drawWithCache {
        val window = Path()
        onDrawWithContent {
            val current = frame()
            if (current == null) {
                drawContent()
                return@onDrawWithContent
            }
            window.reset()
            window.addRoundRect(RoundRect(current.clip, CornerRadius(current.cornerRadius)))
            val (scale, translation) = current.transform
            clipPath(window) {
                translate(translation.x, translation.y) {
                    scale(scale, pivot = Offset.Zero) { this@onDrawWithContent.drawContent() }
                }
            }
        }
    }

/** A drag this far down (or a flick this fast) dismisses on release. */
private val DismissDistance = 96.dp
private val DismissVelocity = 800.dp

/** Corner radius of the page at the end of a back gesture. */
private val BackCornerRadius = 28.dp

/** A drag shrinks the page by up to DRAG_SHRINK, reached at DRAG_SHRINK_DISTANCE of the page height. */
private const val DRAG_SHRINK = 0.25f
private const val DRAG_SHRINK_DISTANCE = 0.5f

/** A drag has faded the scrim out by DRAG_SCRIM_DISTANCE of the page height. */
private const val DRAG_SCRIM_DISTANCE = 1f / 3f

/** A back gesture shrinks the page by up to BACK_SHRINK and dims the scrim to MIN_BACK_SCRIM. */
private const val BACK_SHRINK = 0.15f
private const val MIN_BACK_SCRIM = 0.6f

/** Back-gesture or flight progress by which the toolbar has faded out. */
private const val CHROME_FADE_END = 0.5f

/** Without a thumbnail on screen, the page shrinks to this and fades out where it is. */
private const val FADE_OUT_SCALE = 0.75f

/** Within this factor of the minimum scale, the image counts as fully zoomed out. */
private const val ZOOMED_OUT_TOLERANCE = 1.01f

private val FlightSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f)

package com.garfiec.librechat.core.ui.media

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.toSize

/**
 * Marks this composable as the thumbnail of the image at [url], clipped to [shape] (it replaces the
 * thumbnail's own `clip`). A [MediaViewerHost]'s viewer leaving that image flies back into it.
 * Does nothing outside a host.
 */
fun Modifier.mediaThumbnail(url: String, shape: CornerBasedShape): Modifier =
    clip(shape).then(MediaThumbnailElement(url, shape))

/**
 * The thumbnails attached under one [MediaViewerHost]. Each registers while attached; positions are
 * read only when a dismiss asks for a target, so scrolling a list of them costs nothing.
 */
@Stable
internal class MediaThumbnailRegistry {
    private val thumbnails = mutableListOf<MediaThumbnailNode>()

    /** The thumbnail the viewer is leaving into. It draws nothing, so the image isn't shown twice. */
    var hidden: MediaThumbnailNode? by mutableStateOf(null)

    fun add(node: MediaThumbnailNode) {
        thumbnails += node
    }

    fun remove(node: MediaThumbnailNode) {
        thumbnails -= node
        if (hidden === node) hidden = null
    }

    /** The most visible thumbnail of [url] within [space]'s bounds, in [space]'s coordinates; null when none shows. */
    fun targetFor(url: String, space: LayoutCoordinates): ThumbnailTarget? {
        if (!space.isAttached) return null
        val candidates = thumbnails.mapNotNull { node ->
            node.coordinates?.takeIf { node.url == url }?.let { node to it }
        }
        val (node, coordinates) = mostVisible(candidates, Rect(Offset.Zero, space.size.toSize())) { (_, coordinates) ->
            space.localBoundingBoxOf(coordinates, clipBounds = true)
        } ?: return null
        return ThumbnailTarget(
            node = node,
            bounds = space.localBoundingBoxOf(coordinates, clipBounds = false),
            cornerRadius = node.cornerRadius(coordinates),
        )
    }
}

internal class ThumbnailTarget(val node: MediaThumbnailNode, val bounds: Rect, val cornerRadius: Float)

/** The candidate whose [visibleBounds] cover the most of [viewport]; null when none overlaps it. */
internal fun <T> mostVisible(candidates: List<T>, viewport: Rect, visibleBounds: (T) -> Rect): T? = candidates
    .map { it to visibleBounds(it).intersect(viewport) }
    .filter { (_, visible) -> visible.width > 0f && visible.height > 0f }
    .maxByOrNull { (_, visible) -> visible.width * visible.height }
    ?.first

internal val LocalMediaThumbnails = staticCompositionLocalOf<MediaThumbnailRegistry?> { null }

private data class MediaThumbnailElement(
    val url: String,
    val shape: CornerBasedShape,
) : ModifierNodeElement<MediaThumbnailNode>() {
    override fun create() = MediaThumbnailNode(url, shape)

    override fun update(node: MediaThumbnailNode) {
        node.url = url
        node.shape = shape
    }
}

internal class MediaThumbnailNode(
    var url: String,
    var shape: CornerBasedShape,
) : Modifier.Node(), DrawModifierNode, LayoutAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var registry: MediaThumbnailRegistry? = null

    // A lazy list precomposes the next item before it scrolls in: attached, but never placed, so
    // its coordinates sit at the list's origin. Only a placed thumbnail is a target.
    private var placed = false

    val coordinates: LayoutCoordinates?
        get() = if (isAttached && placed) requireLayoutCoordinates().takeIf { it.isAttached } else null

    fun cornerRadius(coordinates: LayoutCoordinates): Float =
        shape.topStart.toPx(coordinates.size.toSize(), requireDensity())

    override fun onAttach() {
        registry = currentValueOf(LocalMediaThumbnails)?.also { it.add(this) }
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        placed = true
    }

    override fun onDetach() {
        registry?.remove(this)
        registry = null
        placed = false
    }

    override fun ContentDrawScope.draw() {
        if (registry?.hidden !== this@MediaThumbnailNode) drawContent()
    }
}

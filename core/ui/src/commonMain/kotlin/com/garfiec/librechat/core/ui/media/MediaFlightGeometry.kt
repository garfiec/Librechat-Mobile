package com.garfiec.librechat.core.ui.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import kotlin.math.max

/** A uniform scale about the origin, then a translation: a point p draws at `p * scale + translation`. */
internal data class PageTransform(val scale: Float = 1f, val translation: Offset = Offset.Zero) {

    fun map(rect: Rect): Rect = Rect(rect.topLeft * scale + translation, rect.size * scale)

    companion object {
        /** Scale by [scale] about [pivot], then move by [offset]. */
        fun about(pivot: Offset, scale: Float, offset: Offset): PageTransform =
            PageTransform(scale, pivot - pivot * scale + offset)

        /** Makes [image] cover [target], centred: the crop a thumbnail draws. */
        fun cover(image: Rect, target: Rect): PageTransform {
            val scale = max(target.width / image.width, target.height / image.height)
            return PageTransform(scale, target.center - image.center * scale)
        }
    }
}

/** How a viewer page draws on one frame: moved by [transform], seen through a rounded [clip], at [alpha]. */
internal data class PageFrame(
    val transform: PageTransform,
    val clip: Rect,
    val cornerRadius: Float,
    val alpha: Float = 1f,
)

/**
 * The page's flight from where it is drawn now ([from] applied to [image]) into [target].
 *
 * The image scales to cover the target while the window it shows through shrinks from the image to
 * the target, so the last frame draws exactly what a centre-cropped thumbnail does. [fade] is for a
 * target that is no thumbnail: the image fades out on the way.
 */
internal data class Flight(
    val image: Rect,
    val from: PageTransform,
    val fromCorner: Float,
    val target: Rect,
    val targetCorner: Float,
    val fade: Boolean,
) {
    private val to = PageTransform.cover(image, target)
    private val fromClip = from.map(image)

    fun frame(fraction: Float): PageFrame = PageFrame(
        transform = PageTransform(
            scale = from.scale + (to.scale - from.scale) * fraction,
            translation = lerp(from.translation, to.translation, fraction),
        ),
        clip = lerp(fromClip, target, fraction),
        cornerRadius = fromCorner + (targetCorner - fromCorner) * fraction,
        alpha = if (fade) 1f - fraction else 1f,
    )
}

internal fun Rect.scaledAboutCenter(factor: Float): Rect = PageTransform.about(center, factor, Offset.Zero).map(this)

package com.garfiec.librechat.feature.chat.components.sendflight

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import com.garfiec.librechat.core.model.Message
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private val WORD = Regex("\\S+")

/** The character ranges of the words in [text], in reading order. */
internal fun wordRanges(text: String): List<IntRange> = WORD.findAll(text).map { it.range }.toList()

/**
 * Whether [tail], the thread's last user message, is the one a send of [sentText] just added: a user
 * row that wasn't the tail when the send was captured ([knownTailId]) and carries the same words.
 */
internal fun isSentRow(sentText: String, knownTailId: String?, tail: Message?): Boolean =
    tail != null &&
        tail.isCreatedByUser &&
        tail.messageId != knownTailId &&
        tail.text.trim() == sentText.trim()

/**
 * The bridge between the composer box's top edge and a drop rising off it, while they are joined.
 * The sides are cubic curves pulled in to [waist], which narrows as the gap opens; at [snapPx] the
 * neck is gone. Null when the drop's bottom is still inside the box or the neck has snapped.
 */
internal data class Neck(
    val baseCenter: Float,
    val baseWidth: Float,
    val baseY: Float,
    val topCenter: Float,
    val topWidth: Float,
    val topY: Float,
    val waist: Float,
) {
    private val gap get() = baseY - topY

    /** The filled neck, overlapping both shapes by [overlap] so no seam shows. */
    fun fill(overlap: Float): Path = Path().apply {
        moveTo(baseCenter - baseWidth / 2, baseY + overlap)
        cubicTo(
            waistX(-1), baseY - gap * CONTROL,
            waistX(-1), topY + gap * CONTROL,
            topCenter - topWidth / 2, topY - overlap,
        )
        lineTo(topCenter + topWidth / 2, topY - overlap)
        cubicTo(
            waistX(1), topY + gap * CONTROL,
            waistX(1), baseY - gap * CONTROL,
            baseCenter + baseWidth / 2, baseY + overlap,
        )
        close()
    }

    /** The two curved sides alone, for an outline that continues the box's border up the neck. */
    fun sides(): Path = Path().apply {
        moveTo(baseCenter - baseWidth / 2, baseY)
        cubicTo(waistX(-1), baseY - gap * CONTROL, waistX(-1), topY + gap * CONTROL, topCenter - topWidth / 2, topY)
        moveTo(topCenter + topWidth / 2, topY)
        cubicTo(waistX(1), topY + gap * CONTROL, waistX(1), baseY - gap * CONTROL, baseCenter + baseWidth / 2, baseY)
    }

    private fun waistX(side: Int): Float = (baseCenter + topCenter) / 2 + side * waist / 2

    private companion object {
        const val CONTROL = 0.35f
    }
}

/**
 * @param boxTop the composer box's top edge.
 * @param boxLeft, boxRight the box's sides, between which the neck's base stays.
 * @param maxTopWidth the widest the neck may meet the drop; the drop's own corners narrow it further.
 * @param cornerPx the drop's corner radius: the neck meets only its straight bottom edge.
 */
@Suppress("LongParameterList")
internal fun budNeck(
    boxTop: Float,
    boxLeft: Float,
    boxRight: Float,
    drop: Rect,
    snapPx: Float,
    maxTopWidth: Float,
    cornerPx: Float,
): Neck? {
    val gap = boxTop - drop.bottom
    if (gap <= 0f || gap >= snapPx) return null
    val t = gap / snapPx
    val top = min(maxTopWidth, drop.width - 2 * cornerPx).coerceAtLeast(0f) * (1f - TOP_SHRINK * t)
    if (top <= 0f) return null
    val waist = max(top * (1f - t).pow(WAIST_POWER), top * MIN_WAIST)
    val base = top + 2 * min(gap, top / 2)
    val baseCenter = drop.center.x.coerceIn(boxLeft + base / 2, max(boxLeft + base / 2, boxRight - base / 2))
    return Neck(
        baseCenter = baseCenter,
        baseWidth = base,
        baseY = boxTop,
        topCenter = drop.center.x,
        topWidth = top,
        topY = drop.bottom,
        waist = waist,
    )
}

/** How far the box's top edge is pulled up while the drop still holds it. */
internal fun recoilFor(gap: Float, snapPx: Float, maxRecoil: Float): Float =
    if (gap <= 0f || gap >= snapPx) 0f else (gap * RECOIL_PULL).coerceAtMost(maxRecoil)

private const val TOP_SHRINK = 0.35f
private const val WAIST_POWER = 1.4f
private const val MIN_WAIST = 0.12f
private const val RECOIL_PULL = 0.45f

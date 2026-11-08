package com.garfiec.librechat.feature.chat.components.sendflight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Message
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The chat screen's send animation: the message buds off the composer box, floats into its row, and
 * hands off to the reply's waiting dots. Null outside the chat screen (shared chats); in Compare it
 * is provided but [SendFlightState.enabled] is off.
 */
internal val LocalSendFlight = staticCompositionLocalOf<SendFlightState?> { null }

/**
 * Where everything a flight touches sits on screen, and the one flight in the air. The places are
 * plain fields read at draw time, so registering them never recomposes anything.
 */
@Stable
internal class SendFlightState {
    /** Off under reduced motion and in Compare; set by the screen every composition. */
    var enabled: Boolean = true

    internal var layer: LayoutCoordinates? = null
    internal var field: LayoutCoordinates? = null
    internal var box: LayoutCoordinates? = null
    internal var waitDots: LayoutCoordinates? = null
    private var tailUserId: String? = null
    private val bubbles = HashMap<String, Pair<LayoutCoordinates, Boolean>>()
    private val texts = HashMap<String, LayoutCoordinates>()

    var flight: Flight? by mutableStateOf(null)
        private set

    val fieldModifier: Modifier = Modifier.onPlaced { field = it }
    val boxModifier: Modifier = Modifier.onPlaced { box = it }
    val waitDotsModifier: Modifier = Modifier
        .onPlaced { waitDots = it }
        .graphicsLayer { alpha = if (flight?.holdsWaitDots == true) 0f else 1f }

    /** The send button was tapped with [text] in the field: get ready to fly it once its row shows. */
    fun capture(text: String) {
        flight = null
        if (!enabled) return
        val layer = layer?.takeIf { it.isAttached } ?: return
        val field = field?.takeIf { it.isAttached } ?: return
        val box = box?.takeIf { it.isAttached } ?: return
        start(
            text = text,
            fieldRect = layer.localBoundingBoxOf(field, clipBounds = false),
            boxRect = layer.localBoundingBoxOf(box, clipBounds = false),
        )
    }

    internal fun start(text: String, fieldRect: Rect, boxRect: Rect, capturedAt: TimeMark = TimeSource.Monotonic.markNow()) {
        val words = wordRanges(text)
        if (words.isEmpty() || words.size > MAX_WORDS) return
        flight = Flight(text, words, fieldRect, boxRect, knownTailId = tailUserId, capturedAt = capturedAt)
    }

    /** The thread's last user message and its slot, seen after every thread composition. */
    fun observeTail(tail: Message?, slot: String?) {
        val f = flight
        if (f == null) {
            tailUserId = tail?.messageId
            return
        }
        val claimed = f.messageId
        if (claimed != null) {
            // The server's copy can replace the optimistic row under a new id in the same slot.
            if (tail != null && slot == f.slot && tail.messageId != claimed && isSentRow(f.text, claimed, tail)) {
                bubbles.remove(claimed)?.let { bubbles[tail.messageId] = it }
                texts.remove(claimed)?.let { texts[tail.messageId] = it }
                f.messageId = tail.messageId
            }
            return
        }
        if (f.capturedAt.elapsedNow() > CLAIM_WINDOW) {
            end(f)
        } else if (isSentRow(f.text, f.knownTailId, tail)) {
            f.slot = slot
            f.messageId = tail?.messageId
        }
    }

    internal fun end(f: Flight) {
        if (flight !== f) return
        flight = null
        bubbles.clear()
        texts.clear()
    }

    /** Hides the flying message's body until the copy lands on it; its sender line has [headerModifier]. */
    fun bubbleModifier(messageId: String, hasBubble: Boolean): Modifier = Modifier
        .onPlaced { if (flight != null) bubbles[messageId] = it to hasBubble }
        .graphicsLayer { alpha = bodyFraction(messageId) }

    /** Brings the flying message's sender line in while it flies, so the copy lands under it. */
    fun headerModifier(messageId: String): Modifier = Modifier.graphicsLayer {
        val shown = headerFraction(messageId)
        alpha = shown
        translationY = (shown - 1f) * HEADER_DROP.toPx()
    }

    internal fun bodyFraction(messageId: String): Float {
        val f = flight
        return if (f != null && f.messageId == messageId) f.land.value else 1f
    }

    /** Follows the drop, not a clock of its own, so it is in by [HEADER_IN_BY] of the way whatever the drop's speed. */
    internal fun headerFraction(messageId: String): Float {
        val f = flight
        if (f == null || f.messageId != messageId) return 1f
        return FastOutSlowInEasing.transform((f.flyY.value / HEADER_IN_BY).coerceIn(0f, 1f))
    }

    fun textModifier(messageId: String): Modifier = Modifier.onPlaced {
        if (flight != null) texts[messageId] = it
    }

    /** While a message flies, the reply behind it shows only its waiting dots. */
    val holdsReply: Boolean get() = flight != null

    /**
     * Put on the live reply and any row after the sent message: invisible while the message is in
     * the air, then faded and lifted in as it lands. Alpha only, so the list's layout and the tail
     * follower see the real size throughout.
     */
    val revealModifier: Modifier = Modifier.graphicsLayer {
        val reveal = revealFraction()
        alpha = reveal
        translationY = (1f - reveal) * REPLY_RISE.toPx()
    }

    internal fun revealFraction(): Float {
        val f = flight ?: return 1f
        if (f.messageId == null) return 1f
        return if (f.replyShown) f.reveal.value.coerceIn(0f, 1f) else 0f
    }

    /** How far the box's top edge is pulled up, read where the box draws. */
    fun recoilPx(): Float = flight?.let { if (it.snapped) it.recoil.value else it.recoilPx } ?: 0f

    internal fun rectOf(coordinates: LayoutCoordinates?): Rect? {
        val layer = layer?.takeIf { it.isAttached } ?: return null
        val target = coordinates?.takeIf { it.isAttached } ?: return null
        return layer.localBoundingBoxOf(target, clipBounds = false)
    }

    internal fun targetOf(f: Flight): Target? {
        val id = f.messageId ?: return null
        val text = rectOf(texts[id]) ?: return null
        val (bubbleCoordinates, hasBubble) = bubbles[id] ?: return Target(text, text, hasBubble = false)
        val bubble = rectOf(bubbleCoordinates) ?: return null
        return Target(bubble, text, hasBubble)
    }

    internal class Target(val bubble: Rect, val text: Rect, val hasBubble: Boolean)

    companion object {
        /** A row that shows later than this after the tap lands without flying (a held send). */
        val CLAIM_WINDOW = 600.milliseconds

        /** Past this the words would cost more to draw than the flight is worth. */
        const val MAX_WORDS = 150

        private val REPLY_RISE = 16.dp
        private val HEADER_DROP = 4.dp
        private const val HEADER_IN_BY = 0.6f
    }
}

/** One message in the air. The animatables are its timeline; the layouts are measured once claimed. */
@Stable
internal class Flight(
    val text: String,
    val words: List<IntRange>,
    val fieldRect: Rect,
    val boxRect: Rect,
    val knownTailId: String?,
    val capturedAt: TimeMark,
) {
    var messageId: String? by mutableStateOf(null)
    var slot: String? = null

    /** 0 → 1 while the text rises out of the field and takes the bubble's shape. */
    val rise = Animatable(0f)
    val flyX = Animatable(0f)
    val flyY = Animatable(0f)
    val wobble = Animatable(0f)
    val recoil = Animatable(0f)

    /** 0 → 1 as the real message body takes over from the copy. */
    val land = Animatable(0f)
    val sheen = Animatable(0f)
    val dotX = Animatable(0f)
    val dotY = Animatable(0f)
    val split = Animatable(0f)

    var snapped by mutableStateOf(false)
    var replyShown by mutableStateOf(false)

    /** 0 → 1 as the reply's row grows in under the landed message. */
    val reveal = Animatable(0f)
    var recoilPx by mutableFloatStateOf(0f)
    var landed by mutableStateOf(false)
    var dotStart: Offset? by mutableStateOf(null)
    var splitting by mutableStateOf(false)
    var holdsWaitDots by mutableStateOf(true)

    /** State: the layer first draws before the layouts exist, and must draw again once they do. */
    var start: TextLayoutResult? by mutableStateOf(null)
    var end: TextLayoutResult? = null
    var wordLayouts: List<TextLayoutResult> = emptyList()
}

/**
 * Put on a message's bubble (before its background), so a flight can land on it. While the message
 * flies this hides what it wraps, so a user row keeps everything but its [sendFlightHeader] parts inside it.
 */
@Composable
internal fun sendFlightBubble(messageId: String, isUser: Boolean, hasBubble: Boolean): Modifier {
    val flight = LocalSendFlight.current
    return if (flight != null && isUser) flight.bubbleModifier(messageId, hasBubble) else Modifier
}

/** Put on a message's sender line (avatar, name, time) outside its bubble, so it arrives before the copy lands. */
@Composable
internal fun sendFlightHeader(messageId: String, isUser: Boolean): Modifier {
    val flight = LocalSendFlight.current
    return if (flight != null && isUser) flight.headerModifier(messageId) else Modifier
}

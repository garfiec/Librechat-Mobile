package com.garfiec.librechat.feature.chat.components.sendflight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.Message
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The chat screen's send animation. The new turn comes in top to bottom, each part sliding up as it
 * fades in: the user's sender line, their message, the reply's sender line, then the reply itself.
 * Null outside the chat screen (shared chats); in Compare it is provided but
 * [SendFlightState.enabled] is off.
 */
internal val LocalSendFlight = staticCompositionLocalOf<SendFlightState?> { null }

/** The one send being animated. Its fractions are read at draw time, so nothing recomposes per frame. */
@Stable
internal class SendFlightState {
    /** Off under reduced motion and in Compare; set by the screen every composition. */
    var enabled: Boolean = true

    private var tailUserId: String? = null

    var flight: Flight? by mutableStateOf(null)
        private set

    /** The send button was tapped with [text] in the field: animate its turn once its row shows. */
    fun capture(text: String) {
        flight = null
        if (enabled) start(text)
    }

    internal fun start(text: String, capturedAt: TimeMark = TimeSource.Monotonic.markNow()) {
        if (text.isBlank()) return
        flight = Flight(text, knownTailId = tailUserId, capturedAt = capturedAt)
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
        if (flight === f) flight = null
    }

    /**
     * While true the live reply shows only its waiting dots and the rest of the reply stays out of
     * the list, so the turn keeps its height until the reply's own step.
     */
    val holdsReply: Boolean get() = flight?.replyBodyShown == false

    internal fun nameFraction(messageId: String): Float = sentPart(messageId) { it.name }

    internal fun messageFraction(messageId: String): Float = sentPart(messageId) { it.message }

    internal fun replyHeaderFraction(): Float = replyPart { it.replyHeader }

    internal fun replyBodyFraction(): Float = replyPart { it.replyBody }

    private inline fun sentPart(messageId: String, part: (Flight) -> Animatable<Float, *>): Float {
        val f = flight
        return if (f != null && f.messageId == messageId) part(f).value else 1f
    }

    // Hidden from the tap until the row is claimed, so the reply never shows ahead of the message.
    private inline fun replyPart(part: (Flight) -> Animatable<Float, *>): Float = flight?.let(part)?.value ?: 1f

    fun nameModifier(messageId: String): Modifier = Modifier.entering(NAME_RISE) { nameFraction(messageId) }

    fun messageModifier(messageId: String): Modifier = Modifier.entering(MESSAGE_RISE) { messageFraction(messageId) }

    /** The reply's sender line (avatar and name), and any row after the sent message. */
    val replyHeaderModifier: Modifier = Modifier.entering(REPLY_RISE) { replyHeaderFraction() }

    val replyBodyModifier: Modifier = Modifier.entering(REPLY_RISE) { replyBodyFraction() }

    companion object {
        /** A row that shows later than this after the tap appears without the animation (a held send). */
        val CLAIM_WINDOW = 600.milliseconds

        private val NAME_RISE = 12.dp
        private val MESSAGE_RISE = 24.dp
        private val REPLY_RISE = 16.dp
    }
}

/** Alpha and a rise only: the list's layout and the tail follower see the real sizes throughout. */
private fun Modifier.entering(rise: Dp, fraction: () -> Float): Modifier = graphicsLayer {
    val shown = fraction().coerceIn(0f, 1f)
    alpha = shown
    translationY = (1f - shown) * rise.toPx()
}

/** One send being animated. Each part runs 0 → 1 in the order the turn reads. */
@Stable
internal class Flight(
    val text: String,
    val knownTailId: String?,
    val capturedAt: TimeMark,
) {
    var messageId: String? by mutableStateOf(null)
    var slot: String? = null

    val name = Animatable(0f)
    val message = Animatable(0f)
    val replyHeader = Animatable(0f)
    val replyBody = Animatable(0f)

    /** State: the thread lets the reply's content in when its step starts. */
    var replyBodyShown by mutableStateOf(false)
}

/** Runs the animation of each send; put it once in the screen that provides [LocalSendFlight]. */
@Composable
internal fun SendFlightEffect(state: SendFlightState) {
    val flight = state.flight ?: return
    LaunchedEffect(flight) {
        try {
            withTimeoutOrNull(SendFlightState.CLAIM_WINDOW) {
                snapshotFlow { flight.messageId }.first { it != null }
            } ?: return@LaunchedEffect
            play(flight)
        } finally {
            state.end(flight)
        }
    }
}

private suspend fun play(f: Flight) = coroutineScope {
    launch { f.name.animateTo(1f, step()) }
    pause(MESSAGE_AT_MS)
    launch { f.message.animateTo(1f, step()) }
    pause(REPLY_AT_MS - MESSAGE_AT_MS)
    launch { f.replyHeader.animateTo(1f, step()) }
    pause(REPLY_BODY_AT_MS - REPLY_AT_MS)
    f.replyBodyShown = true
    f.replyBody.animateTo(1f, step())
}

private fun step() = tween<Float>(STEP_MS, easing = Emphasized)

/** On the animation clock, not `delay`, so the system's animator scale stretches the waits with the steps. */
private suspend fun pause(ms: Int) {
    Animatable(0f).animateTo(1f, tween(ms, easing = LinearEasing))
}

/** Material 3's emphasized easing. */
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val STEP_MS = 500
private const val MESSAGE_AT_MS = 120
private const val REPLY_AT_MS = 650
private const val REPLY_BODY_AT_MS = 850

/**
 * Whether [tail], the thread's last user message, is the one a send of [sentText] just added: a user
 * row that wasn't the tail when the send was captured ([knownTailId]) and carries the same words.
 */
internal fun isSentRow(sentText: String, knownTailId: String?, tail: Message?): Boolean =
    tail != null &&
        tail.isCreatedByUser &&
        tail.messageId != knownTailId &&
        tail.text.trim() == sentText.trim()

/** Put on a user message's bubble (everything of the row but its sender line). */
@Composable
internal fun sendFlightBubble(messageId: String, isUser: Boolean): Modifier {
    val flight = LocalSendFlight.current
    return if (flight != null && isUser) flight.messageModifier(messageId) else Modifier
}

/** Put on a user message's sender line (avatar, name, time) outside its bubble. */
@Composable
internal fun sendFlightHeader(messageId: String, isUser: Boolean): Modifier {
    val flight = LocalSendFlight.current
    return if (flight != null && isUser) flight.nameModifier(messageId) else Modifier
}

/** Put on the live reply's sender line (avatar and name). */
@Composable
internal fun sendFlightReplyHeader(): Modifier = LocalSendFlight.current?.replyHeaderModifier ?: Modifier

/** Put on the live reply's content under its sender line. */
@Composable
internal fun sendFlightReplyBody(): Modifier = LocalSendFlight.current?.replyBodyModifier ?: Modifier

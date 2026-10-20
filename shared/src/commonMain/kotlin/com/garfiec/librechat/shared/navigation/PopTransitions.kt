package com.garfiec.librechat.shared.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.garfiec.librechat.core.ui.theme.LocalDarkTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

// A port of the system's cross-activity predictive back (AOSP DefaultCrossActivityBackAnimation),
// the animation Pixel Settings shows between its pages.
//
// While dragging, the leaving page shrinks to a centred rounded card, and the previous page, offset
// to the left whichever edge the swipe starts from, shrinks further behind a scrim. Both follow the
// finger vertically. On release the card fades out where it is while the previous page grows back
// to full screen and the scrim clears.
//
// The drag follows the gesture's own progress, read from the dispatcher, not NavDisplay's seek of
// its transition: that seek can sit at zero for a whole gesture, leaving the pages still while the
// finger moves. The commit runs on its own clock from the moment the back stack pops, and a cancel
// eases back on its own clock too. NavDisplay's transition only keeps both pages composed.
private const val PREDICTIVE_DURATION_MS = 400
private const val POP_DURATION_MS = 300
private const val COMMIT_MS = 350

// Matches how long NavDisplay keeps the previous page after a cancel: its seek fraction times the duration.
private const val CANCEL_MS = PREDICTIVE_DURATION_MS

// The front card shrinks to DRAG_SCALE while dragging (the system uses 0.9), and by COMMIT_SCALE
// again as it fades on release.
private const val DRAG_SCALE = 0.85f
private const val COMMIT_SCALE = 0.9f

private const val BACK_DRAG_SCALE = 0.8f

private val BoundsMargin = 8.dp
private val EnteringOffset = 96.dp
private val CardCornerRadius = 32.dp
private const val SCRIM_ALPHA_DARK = 0.8f

// The system uses 0.2 in light theme, which barely shows over a white page.
private const val SCRIM_ALPHA_LIGHT = 0.45f

private const val FRONT_FADE_SHARE = 0.2f
private const val MIN_SPAN = 1e-3f

// AOSP Interpolators.BACK_GESTURE, and EMPHASIZED approximated by the M3 emphasized curve.
private val GestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
private val CommitEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Button / programmatic back: the commit half of the predictive animation, without the drag. */
internal fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.popTransform(): ContentTransform =
    scaleIn(
        initialScale = COMMIT_SCALE,
        animationSpec = tween(POP_DURATION_MS, easing = LinearOutSlowInEasing),
    ) togetherWith fadeOut(tween(POP_DURATION_MS / 2, easing = FastOutLinearInEasing))

/**
 * Predictive back. Identity transforms that only keep both pages composed for the transition;
 * [rememberPredictiveBackCardDecorator] draws the animation.
 */
internal fun <T : Any> AnimatedContentTransitionScope<Scene<T>>.predictivePopTransform(): ContentTransform =
    scaleIn(initialScale = 1f, animationSpec = tween(PREDICTIVE_DURATION_MS)) togetherWith
        scaleOut(targetScale = 1f, animationSpec = tween(PREDICTIVE_DURATION_MS))

/**
 * Draws the predictive back animation described at the top of this file.
 *
 * A back gesture is recognised from the entry's own enter/exit transition while the back stack is
 * still unpopped: the leaving page is still the top entry, the arriving one isn't yet. Each page's
 * role is latched for the whole gesture (on a cancel the transition runs back and the leaving page
 * reads as arriving), and held until its transition settles and its own animations finish. Button
 * back and forward navigation pass through untouched, and the transition animation here snaps so
 * it doesn't stretch them.
 */
@Composable
internal fun <T : NavKey> rememberPredictiveBackCardDecorator(backStack: List<T>): NavEntryDecorator<T> {
    val finger = remember { FingerTracker() }
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    LaunchedEffect(dispatcher) {
        dispatcher?.transitionState?.collect { state ->
            when (state) {
                is NavigationEventTransitionState.InProgress -> finger.onEvent(state.latestEvent)
                NavigationEventTransitionState.Idle -> finger.active = false
            }
        }
    }
    val scrimAlpha = if (LocalDarkTheme.current) SCRIM_ALPHA_DARK else SCRIM_ALPHA_LIGHT
    // Entries keep the default contentKey, the key's toString(). Derived once for every entry.
    val stackKeys = remember(backStack) { derivedStateOf { backStack.map<T, Any> { it.toString() } } }
    return remember(stackKeys, scrimAlpha) {
        NavEntryDecorator { entry ->
            PredictiveBackCard(entry.contentKey, stackKeys, finger, scrimAlpha) { entry.Content() }
        }
    }
}

@Composable
private fun PredictiveBackCard(
    key: Any,
    stackKeys: State<List<Any>>,
    finger: FingerTracker,
    scrimAlpha: Float,
    content: @Composable () -> Unit,
) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val keys = stackKeys.value
    val isTop = keys.lastOrNull() == key
    val inStack = key in keys
    val exiting = transition.targetState == EnterExitState.PostExit

    val commit = remember { Animatable(0f) }
    val settle = remember { Animatable(0f) }
    val latch = remember { RoleLatch() }
    if (transition.currentState == transition.targetState && !commit.isRunning && !settle.isRunning) {
        latch.reset()
    } else {
        // A new gesture that starts while this page is still growing back from the last one makes it
        // the leaving page of that gesture.
        if (latch.role == Role.Back && exiting && isTop) latch.reset()
        // A page that isn't in the stack can't be part of a gesture; this also keeps an entry with a
        // custom contentKey (never matching) out of the Back role.
        if (latch.role == Role.None && inStack && exiting == isTop) {
            latch.role = if (exiting) Role.Front else Role.Back
        }
    }
    val front = latch.role == Role.Front
    // Sticky until the latch resets: a push or pop during the commit must not turn it back into a cancel.
    latch.committed = latch.committed || (front && !inStack) || (latch.role == Role.Back && isTop)
    val committed = latch.committed

    // NavDisplay's run of the transition, used only to know how long the leaving page has left.
    val transitionFraction by transition.animateFloat(
        transitionSpec = {
            if (latch.role != Role.None) tween(PREDICTIVE_DURATION_MS, easing = LinearEasing) else snap()
        },
        label = "backTransition",
    ) { state ->
        when (latch.role) {
            Role.None -> 0f
            Role.Front -> if (state == EnterExitState.PostExit) 1f else 0f
            Role.Back -> if (state == EnterExitState.Visible) 1f else 0f
        }
    }

    LaunchedEffect(committed) {
        if (committed) commit.animateTo(1f, tween(COMMIT_MS, easing = LinearEasing)) else commit.snapTo(0f)
    }
    val cancelled = latch.role != Role.None && !finger.active && !committed
    LaunchedEffect(cancelled) {
        if (cancelled) {
            settle.snapTo(latch.lastPose)
            latch.settling = true
            val duration = (CANCEL_MS * latch.lastPose).toInt().coerceAtLeast(1)
            settle.animateTo(0f, tween(duration, easing = CommitEasing))
        }
    }

    // NavDisplay hands the page tight constraints; keep passing them on to the entry.
    // Clipped so the page behind, offset to the left, stays inside its pane in a two-pane layout.
    Box(
        Modifier.clipToBounds().drawWithContent {
            drawContent()
            // The page behind dims itself, from this unscaled layer so the scrim covers the screen.
            // It outlives the front page, which NavDisplay removes as soon as its transition ends,
            // so the scrim can fade out with the whole commit.
            if (latch.role == Role.Back) {
                val pose = latch.pose(finger, settle.value, committed)
                // Linear across the whole commit: on the commit curve, half of it would go in the first frames.
                val dim = scrimAlpha * GestureEasing.transform(pose) * (1f - commitProgress(committed, commit))
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
        },
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier.graphicsLayer {
                val pose = latch.pose(finger, settle.value, committed)
                val progress = commitProgress(committed, commit)
                when (latch.role) {
                    Role.None -> Unit
                    Role.Front -> drawFront(latch, finger, pose, transitionFraction, committed, progress)
                    Role.Back -> drawBack(latch, finger, pose, committed, progress)
                }
            },
            propagateMinConstraints = true,
        ) { content() }
    }
}

// The commit's LaunchedEffect resets it a frame late; until then a page no longer committing would
// draw the old commit's progress (a faded-out card, or a page grown back mid-gesture).
private fun commitProgress(committed: Boolean, commit: Animatable<Float, *>): Float =
    if (committed) commit.value else 0f

/** Shrinks in place, centred. */
@Suppress("LongParameterList")
private fun GraphicsLayerScope.drawFront(
    latch: RoleLatch,
    finger: FingerTracker,
    pose: Float,
    transitionFraction: Float,
    committed: Boolean,
    commit: Float,
) {
    val p = GestureEasing.transform(pose)
    val margin = BoundsMargin.toPx()
    val scale = lerp(1f, DRAG_SCALE, p)
    val commitScale = lerp(1f, COMMIT_SCALE, CommitEasing.transform(commit))
    scaleX = scale * commitScale
    scaleY = scale * commitScale
    translationY = latch.yOffset(finger, size.height, scale, margin, pose, committed)
    // NavDisplay removes the page when the transition ends, which on a late release can come
    // before the fade does; finish by then either way.
    alpha = 1f - max(min(1f, commit / FRONT_FADE_SHARE), latch.sinceRelease(transitionFraction, committed))
    setCorners(CardCornerRadius.toPx() * p)
}

/**
 * Starts [EnteringOffset] off to the left and shrinks in step with the card; on commit it
 * grows back to full screen.
 */
private fun GraphicsLayerScope.drawBack(
    latch: RoleLatch,
    finger: FingerTracker,
    pose: Float,
    committed: Boolean,
    commit: Float,
) {
    val p = GestureEasing.transform(pose)
    val e = CommitEasing.transform(commit)
    val dragScale = lerp(1f, BACK_DRAG_SCALE, p)
    val scale = lerp(dragScale, 1f, e)
    scaleX = scale
    scaleY = scale
    translationX = -EnteringOffset.toPx() * (1f - e)
    translationY = latch.yOffset(finger, size.height, dragScale, BoundsMargin.toPx(), pose, committed) * (1f - e)
    setCorners(CardCornerRadius.toPx() * p * (1f - e))
}

private fun GraphicsLayerScope.setCorners(px: Float) {
    clip = px > 0f
    shape = RoundedCornerShape(px)
}

/** The live back gesture, read from the dispatcher. */
private class FingerTracker {
    // Read in composition, to tell a cancel from a gesture in progress.
    var active by mutableStateOf(false)

    // Read in draw.
    var progress by mutableFloatStateOf(0f)
    var touchY by mutableFloatStateOf(0f)

    var startY = 0f

    fun onEvent(event: NavigationEvent) {
        if (!active) {
            active = true
            startY = event.touchY
        }
        progress = event.progress
        touchY = event.touchY
    }
}

private enum class Role { None, Front, Back }

// Plain fields, not state: recomputed on every recomposition the transition drives, or in draw.
private class RoleLatch {
    var role = Role.None

    // Set once the stack pops for this gesture, and kept until the latch resets.
    var committed = false

    // Set by the cancel's LaunchedEffect once the settle starts from [lastPose].
    var settling = false

    // The pose while the finger was last down, where a cancel or the commit picks up from.
    var lastPose = 0f
        private set

    // Pose, vertical offset and transition fraction when the stack popped; NaN until then.
    private var releasedPose = Float.NaN
    private var releasedY = Float.NaN
    private var releasedFraction = Float.NaN

    // The last vertical offset while the finger was down, and the pose it was taken at.
    private var lastY = 0f
    private var lastYPose = 0f

    fun reset() {
        role = Role.None
        committed = false
        settling = false
        lastPose = 0f
        releasedPose = Float.NaN
        releasedY = Float.NaN
        releasedFraction = Float.NaN
        lastY = 0f
        lastYPose = 0f
    }

    /** The drag pose: the finger's progress, frozen at release, or easing back after a cancel. */
    fun pose(finger: FingerTracker, settle: Float, committed: Boolean): Float {
        // Recorded in the first frame drawn after the pop; a LaunchedEffect would see it a frame
        // late, and that frame would jump.
        if (committed) {
            if (releasedPose.isNaN()) releasedPose = lastPose
            return releasedPose
        }
        if (finger.active) {
            settling = false
            lastPose = finger.progress
            return lastPose
        }
        // The same for a cancel: hold the last pose until the settle has started from it.
        return if (settling) settle else lastPose
    }

    /** Share of the transition's remaining run elapsed since release. */
    fun sinceRelease(fraction: Float, committed: Boolean): Float {
        if (!committed) return 0f
        if (releasedFraction.isNaN()) releasedFraction = fraction
        val from = releasedFraction
        return ((fraction - from) / (1f - from).coerceAtLeast(MIN_SPAN)).coerceIn(0f, 1f)
    }

    /**
     * Follows the finger's vertical travel, decelerating: half the screen height of travel moves
     * the page as far as it can go without crossing [margin]. After a cancel it eases back with the
     * pose; after a release it holds.
     */
    @Suppress("LongParameterList")
    fun yOffset(
        finger: FingerTracker,
        height: Float,
        scale: Float,
        margin: Float,
        pose: Float,
        committed: Boolean,
    ): Float {
        if (committed) {
            if (releasedY.isNaN()) releasedY = lastY
            return releasedY
        }
        if (finger.active) {
            val half = height / 2f
            val raw = finger.touchY - finger.startY
            val ratio = min(half, abs(raw)) / half
            val decelerated = 1f - (1f - ratio) * (1f - ratio)
            lastY = max(0f, (height - height * scale) / 2f - margin) * decelerated * sign(raw)
            lastYPose = pose
            return lastY
        }
        return if (lastYPose > 0f) lastY * (pose / lastYPose).coerceIn(0f, 1f) else 0f
    }
}

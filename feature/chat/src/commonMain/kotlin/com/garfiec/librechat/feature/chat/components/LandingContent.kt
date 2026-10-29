package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.garfiec.librechat.core.ui.components.EndpointIcon
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.chat.components.veil.rememberVeilTarget
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun LandingContent(
    selectedModel: String?,
    modifier: Modifier = Modifier,
    selectedAgentName: String? = null,
    /** Null when the server offers no picker: the label is then plain text. */
    onSelectModel: (() -> Unit)? = null,
    /**
     * The project the new chat will be filed into. A label, not a control: the way out is a plain
     * New chat.
     */
    projectName: String? = null,
    /** The agent's or spec's own icon, as web's landing shows it. Null keeps the app tile. */
    heroIcon: LandingHeroIcon? = null,
    /** Off while starters are on screen: they already say what the subtitle asks. */
    showSubtitle: Boolean = true,
    /** Temporary chat is on: the greeting says so, and the subtitle says it won't be saved. */
    isTemporaryChat: Boolean = false,
    /** Room for whatever is docked above the composer, so the hero centres in what is left. */
    bottomPadding: Dp = 120.dp,
    /** The agent's or spec's name in place of the time-of-day greeting. A temporary chat keeps its own. */
    title: String? = null,
    /** The agent's or spec's description in place of the subtitle, shown with starters too. */
    description: String? = null,
    /** The agent line once temporary chat has taken the title (its title is fixed, so it names the agent). */
    temporaryAgentName: String? = selectedAgentName,
    /** The model label once temporary chat has taken the title. */
    temporaryModel: String? = selectedModel,
) {
    val timeGreeting = remember { getTimeBasedGreeting() }
    val greeting = title ?: stringResource(timeGreeting)
    val temporaryTitle = stringResource(Res.string.temporary_chat_title)
    val temporarySubtitle = stringResource(Res.string.temporary_chat_subtitle)
    val normalSubtitle = description ?: if (showSubtitle) stringResource(Res.string.how_can_i_help) else null
    // With no label under it, a title naming the model opens the picker itself.
    val titleOpensPicker = title != null && selectedModel == null && selectedAgentName.isNullOrBlank()
    // Each line swaps when the temporary-chat band reaches it, not when the flag flips.
    val headingVeil = rememberVeilTarget(isTemporaryChat)
    val subtitleVeil = rememberVeilTarget(isTemporaryChat)
    val glass = isLiquidGlass
    val reduceMotion = rememberReduceMotion()
    val driftPx = with(LocalDensity.current) { SwapDrift.roundToPx() }
    val rollPx = with(LocalDensity.current) { SwapRoll.roundToPx() }
    // The lines under the heading change with it, as the band crosses the heading. A temporary chat
    // is never filed (SendDispatchDelegate), so it claims no project.
    val showsTemporary = headingVeil.crossed
    val agentLine = if (showsTemporary) temporaryAgentName else selectedAgentName
    val modelLine = if (showsTemporary) temporaryModel else selectedModel
    val shownProject = projectName?.takeIf { !showsTemporary }
    val selectModelLabel = stringResource(Res.string.cd_select_model)
    val selectableModifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .then(
            if (onSelectModel != null) {
                Modifier.clickable(onClickLabel = selectModelLabel, role = Role.Button, onClick = onSelectModel)
            } else {
                Modifier
            },
        )
        .padding(horizontal = 12.dp, vertical = 8.dp)

    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // With the keyboard up and starters docked above the composer, the full hero no longer fits
        // between the top bar and them: drop the icon and shrink the greeting rather than overlap.
        val compact = maxHeight - bottomPadding < CompactHeroBelow
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scrolls only once it overflows (large text with the keyboard up): a scrollable takes every
                // vertical drag, and on a landing that fits the drag has to reach the pull-up tools sheet.
                .verticalScroll(scroll, enabled = scroll.canScrollForward || scroll.canScrollBackward)
                .padding(start = 24.dp, end = 24.dp, bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (compact) {
                // No icon: the greeting leads.
            } else if (heroIcon != null) {
                EndpointIcon(
                    endpointName = heroIcon.endpointName,
                    iconUrl = heroIcon.iconUrl,
                    size = 56.dp,
                    glyphTint = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = stringResource(Res.string.cd_librechat),
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            if (!compact) Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = headingVeil.crossed,
                transitionSpec = { veilSwap(glass, reduceMotion, driftPx, rollPx) },
                contentAlignment = Alignment.Center,
                modifier = headingVeil.modifier,
                label = "landingHeading",
            ) { temporary ->
                Text(
                    text = if (temporary) temporaryTitle else greeting,
                    style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = veilBlur(glass && !reduceMotion)
                        .semantics { heading() }
                        .then(if (titleOpensPicker && !temporary) selectableModifier else Modifier),
                )
            }

            // Starters don't say the chat won't be saved, so the temporary subtitle stays with them.
            // A polite live region, so a screen reader says what turning temporary chat on means.
            AnimatedContent(
                targetState = subtitleVeil.crossed,
                transitionSpec = { veilSwap(glass, reduceMotion, driftPx, rollPx) },
                contentAlignment = Alignment.Center,
                modifier = subtitleVeil.modifier.semantics { liveRegion = LiveRegionMode.Polite },
                label = "landingSubtitle",
            ) { temporary ->
                val text = if (temporary) temporarySubtitle else normalSubtitle
                if (text != null) {
                    val isDescription = !temporary && description != null
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = if (isDescription) DescriptionMaxLines else Int.MAX_VALUE,
                        overflow = if (isDescription) TextOverflow.Ellipsis else TextOverflow.Clip,
                        modifier = veilBlur(glass && !reduceMotion).padding(top = 8.dp),
                    )
                }
            }

            // Agent name display (prominent when an agent is selected)
            if (!agentLine.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = agentLine,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = selectableModifier,
                )
            } else if (modelLine != null) {
                Text(
                    text = modelLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = selectableModifier,
                )
            }

            if (shownProject != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.chat_in_project, shownProject),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The landing's line swap as the temporary-chat band crosses it. Material rolls the old line out
 * and the new one in, upward turning on and downward turning off; Liquid Glass dissolves it with a
 * short drift (and [veilBlur]). Reduced motion keeps only a crossfade.
 *
 * Nothing is clipped: the roll travels [rollPx] (a fraction of a line) under a fade rather than a
 * full line inside a clipped slot, and settles without overshoot, so no glyph is ever cut off.
 */
private fun AnimatedContentTransitionScope<Boolean>.veilSwap(
    glass: Boolean,
    reduceMotion: Boolean,
    driftPx: Int,
    rollPx: Int,
): ContentTransform {
    if (reduceMotion) {
        return fadeIn(tween(REDUCED_SWAP_MS)) togetherWith fadeOut(tween(REDUCED_SWAP_MS))
    }
    val on = targetState
    if (glass) {
        // On, the old line rises away and the new one settles up from below; off, both stay put.
        val enter = fadeIn(tween(GLASS_IN_MS, delayMillis = GLASS_IN_DELAY_MS)) +
            slideInVertically(tween(GLASS_IN_MS, delayMillis = GLASS_IN_DELAY_MS)) { if (on) driftPx else 0 }
        val exit = fadeOut(tween(GLASS_OUT_MS)) +
            slideOutVertically(tween(GLASS_OUT_MS)) { if (on) -driftPx else 0 }
        return enter togetherWith exit using SizeTransform(clip = false)
    }
    val enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) {
        if (on) rollPx else -rollPx
    } + fadeIn(tween(ROLL_FADE_IN_MS, delayMillis = ROLL_FADE_IN_DELAY_MS))
    val exit = slideOutVertically(tween(ROLL_OUT_MS, easing = FastOutLinearInEasing)) { if (on) -rollPx else rollPx } +
        fadeOut(tween(ROLL_OUT_MS))
    return enter togetherWith exit using SizeTransform(clip = false)
}

/**
 * Liquid Glass's swap blurs the outgoing and incoming line. [Modifier.blur] does nothing below
 * Android 12, which leaves the fade and drift.
 *
 * A blur's layer is the size of the node it is on, and the platform cuts the blur off at that
 * layer's edge. So the blurred layer is grown by [BlurRoom] on every side and the text placed in
 * its middle, while the outer node still reports the text's own size: the halo has room to fade
 * out and the landing's layout doesn't move.
 */
@Composable
private fun AnimatedVisibilityScope.veilBlur(enabled: Boolean): Modifier {
    if (!enabled) return Modifier
    val radius by transition.animateDp(label = "veilBlur") { state ->
        if (state == EnterExitState.Visible) 0.dp else SwapBlur
    }
    return Modifier
        .layout { measurable, constraints ->
            val room = BlurRoom.roundToPx()
            val grown = measurable.measure(constraints.offset(2 * room, 2 * room))
            layout(grown.width - 2 * room, grown.height - 2 * room) { grown.place(-room, -room) }
        }
        .blur(radius, BlurredEdgeTreatment.Unbounded)
        .layout { measurable, constraints ->
            val room = BlurRoom.roundToPx()
            val text = measurable.measure(constraints.offset(-2 * room, -2 * room))
            layout(text.width + 2 * room, text.height + 2 * room) { text.place(room, room) }
        }
}

private const val REDUCED_SWAP_MS = 150
private const val GLASS_IN_MS = 240
private const val GLASS_IN_DELAY_MS = 80
private const val GLASS_OUT_MS = 140
private const val ROLL_FADE_IN_MS = 200
private const val ROLL_FADE_IN_DELAY_MS = 60
private const val ROLL_OUT_MS = 160
private val SwapBlur = 6.dp
private val SwapDrift = 8.dp
private val SwapRoll = 16.dp

/** Room around a blurred line for its halo: about three times the blur radius. */
private val BlurRoom = 20.dp

/** The full hero's height with the subtitle hidden: icon, gap, greeting and the model label. */
private val CompactHeroBelow = 200.dp

/** Web renders the description in full; here it is plain text, held to a few lines. */
private const val DescriptionMaxLines = 4

/**
 * The landing's hero icon: [iconUrl] when it loads, else [endpointName]'s glyph. A spec's `iconURL`
 * may name an endpoint rather than an image, which is why both exist.
 */
data class LandingHeroIcon(val endpointName: String?, val iconUrl: String?)

/**
 * Returns a time-based greeting with 6 periods matching the web frontend:
 * early morning, morning, afternoon, evening, late night, and weekend variants.
 */
private fun getTimeBasedGreeting(): StringResource {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = now.hour
    val dayOfWeek = now.dayOfWeek
    val isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    if (isWeekend) {
        return when {
            hour < 5 -> Res.string.greeting_late_night
            hour < 12 -> Res.string.greeting_weekend_morning
            hour < 17 -> Res.string.greeting_weekend_afternoon
            else -> Res.string.greeting_weekend_evening
        }
    }

    return when {
        hour < 5 -> Res.string.greeting_late_night
        hour < 9 -> Res.string.greeting_early_morning
        hour < 12 -> Res.string.greeting_morning
        hour < 17 -> Res.string.greeting_afternoon
        else -> Res.string.greeting_evening
    }
}

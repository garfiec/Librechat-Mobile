package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.feature.chat.components.veil.VeilReaction
import com.garfiec.librechat.feature.chat.components.veil.rememberVeilTarget
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val RowShape = RoundedCornerShape(12.dp)
private const val STAGGER_MS = 40L
private const val ROW_FADE_IN_MS = 300
private const val ROW_FADE_OUT_MS = 180
private val ENTER_OFFSET = 12.dp
private val EXIT_OFFSET = 4.dp

/**
 * The new chat's conversation starters, docked on top of the composer and as wide as it: a tap sends
 * one, a hold puts it in the composer to edit first.
 *
 * Shown only while [visible] (the composer is empty), so a tap never has to decide what happens to
 * a draft. Hidden rows fade out but keep their space, so the hero above, which centres in what the
 * composer's box leaves, doesn't move as the user starts and stops typing. They appear bottom-up,
 * nearest the composer first.
 */
@Composable
internal fun LandingStarters(
    starters: List<String>,
    visible: Boolean,
    onSend: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (starters.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        // The label sits on top, so it comes in last.
        Text(
            text = stringResource(Res.string.starters_try_asking),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .cascade(visible, fromComposer = starters.size)
                .padding(start = 12.dp, bottom = 4.dp),
        )
        starters.forEachIndexed { index, starter ->
            val fromComposer = starters.lastIndex - index
            if (index > 0) {
                AdaptiveDivider(modifier = Modifier.cascade(visible, fromComposer).padding(start = 42.dp, end = 12.dp))
            }
            val rowVeil = rememberVeilTarget(isTemporary = false, reaction = VeilReaction.NUDGE)
            StarterRow(
                text = starter,
                enabled = visible,
                modifier = Modifier.cascade(visible, fromComposer).then(rowVeil.modifier),
                onSend = { onSend(starter) },
                onEdit = { onEdit(starter) },
            )
        }
    }
}

/**
 * Comes in after [fromComposer] steps of the stagger, so the list builds upward from the composer.
 * The rise and the fade run separately: a critically damped, low-stiffness spring carries the row
 * up so it decelerates all the way into place, while the fade finishes early. Plain show/hide under
 * reduced motion.
 */
@Composable
private fun Modifier.cascade(visible: Boolean, fromComposer: Int): Modifier {
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val enterOffset = with(density) { ENTER_OFFSET.toPx() }
    val exitOffset = with(density) { EXIT_OFFSET.toPx() }
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(enterOffset) }
    LaunchedEffect(visible) {
        when {
            reduceMotion -> {
                alpha.snapTo(if (visible) 1f else 0f)
                offset.snapTo(0f)
            }
            visible -> {
                delay(fromComposer * STAGGER_MS)
                // A row hidden mid-flight comes back from where it is; one hidden at rest starts low.
                if (alpha.value == 0f) offset.snapTo(enterOffset)
                launch { alpha.animateTo(1f, tween(ROW_FADE_IN_MS, easing = LinearOutSlowInEasing)) }
                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
            }
            else -> {
                launch { alpha.animateTo(0f, tween(ROW_FADE_OUT_MS, easing = FastOutLinearInEasing)) }
                offset.animateTo(exitOffset, tween(ROW_FADE_OUT_MS, easing = FastOutLinearInEasing))
            }
        }
    }
    return graphicsLayer {
        this.alpha = alpha.value
        translationY = offset.value
    }
}

@Composable
private fun StarterRow(
    text: String,
    enabled: Boolean,
    onSend: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .combinedClickable(
                enabled = enabled,
                onClickLabel = stringResource(Res.string.cd_starter_send),
                onLongClickLabel = stringResource(Res.string.cd_starter_edit),
                role = Role.Button,
                // combinedClickable buzzes on the hold itself.
                onLongClick = onEdit,
                onClick = onSend,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.CallMade,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

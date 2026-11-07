package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_cancel_steer
import com.garfiec.librechat.feature.chat.resources.cd_interrupt_steer_now
import com.garfiec.librechat.feature.chat.resources.steer_interrupt_requested
import com.garfiec.librechat.feature.chat.resources.steer_interrupting_hint
import com.garfiec.librechat.feature.chat.resources.steer_pending_hint
import com.garfiec.librechat.feature.chat.viewmodel.PendingSteerChip
import com.garfiec.librechat.feature.chat.viewmodel.SteerChipStatus
import com.garfiec.librechat.feature.chat.viewmodel.interruptPending
import org.jetbrains.compose.resources.stringResource

/**
 * Steers waiting to go into the reply that is currently streaming (v0.8.8), pinned above the
 * composer.
 *
 * Sits directly above [QueuedMessagesSection] and is deliberately styled apart from it: a queued
 * message becomes the *next* turn, a steer changes the one being written right now. The bolt
 * marks the difference, the primary tint gives it the more-urgent read, and there is no drag
 * handle — the run injects steers in the order they were accepted, and the client cannot reorder
 * what the server has already queued.
 *
 * A row disappears on its own the moment the run injects it (`on_steer_applied`); the × withdraws
 * it before that happens. A steer the run never takes moves to the follow-up queue, which the
 * caption warns of and `SteerQueuedSnackbarEffect` confirms.
 *
 * A waiting chip offers "Interrupt now" (`/chat/steer/arm`). One interrupt at a time, as on web: the
 * button is disabled, not hidden, while any chip is interrupting or an arm is in flight, so the
 * rows don't reflow. An interrupting chip takes the tertiary tint and says "Interrupt requested";
 * its look comes from the server's answer, never from the request.
 */
@Composable
fun PendingSteerChipsSection(
    pendingSteers: List<PendingSteerChip>,
    onCancel: (steerId: String) -> Unit,
    modifier: Modifier = Modifier,
    onInterrupt: (steerId: String) -> Unit = {},
    fontSizeMultiplier: Float = 1f,
) {
    if (pendingSteers.isEmpty()) return

    // Right-aligned as a user-side turn (upstream 7694428c): the persisted steer part lands
    // in-thread on the user's side, so a left-anchored in-flight bubble would jump across the
    // screen on `on_steer_applied`. Alignment only — the broader message-row reorg of that PR
    // is deliberately not ported.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        val interruptPending = pendingSteers.interruptPending
        // Not while every chip is still SENDING: the server may yet refuse it. In a mixed column
        // the interrupting caption wins.
        if (pendingSteers.any { it.status == SteerChipStatus.PENDING }) {
            Text(
                text = stringResource(
                    if (pendingSteers.any { it.isInterrupting }) {
                        Res.string.steer_interrupting_hint
                    } else {
                        Res.string.steer_pending_hint
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
        pendingSteers.forEach { chip ->
            PendingSteerRow(
                chip = chip,
                fontSizeMultiplier = fontSizeMultiplier,
                canInterrupt = !interruptPending,
                onCancel = { onCancel(chip.steerId) },
                onInterrupt = { onInterrupt(chip.steerId) },
            )
        }
    }
}

@Composable
private fun PendingSteerRow(
    chip: PendingSteerChip,
    fontSizeMultiplier: Float,
    canInterrupt: Boolean,
    onCancel: () -> Unit,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interrupting = chip.isInterrupting
    val container = if (interrupting) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (interrupting) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    // A glass panel under Liquid Glass (the tint is Material only); the content keeps its colour.
    // Opaque in Material: the composer's top is transparent, so a translucent card would show the
    // thread through the steer.
    AdaptiveCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            // The POST is still out, so the row shows a spinner instead of the bolt: until the
            // server accepts it there is nothing to cancel server-side and nothing to inject.
            if (chip.status == SteerChipStatus.SENDING) {
                AdaptiveCircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else {
                Icon(
                    imageVector = if (interrupting) Icons.Default.FlashOff else Icons.Default.Bolt,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))

            // One semantics node for the words and the state, so TalkBack/VoiceOver read
            // "Interrupt requested" with the message.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(
                    text = chip.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = content,
                    fontSize = 14.sp * fontSizeMultiplier,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (interrupting) {
                    Text(
                        text = stringResource(Res.string.steer_interrupt_requested),
                        style = MaterialTheme.typography.labelSmall,
                        color = content,
                    )
                }
            }

            if (chip.offersInterrupt) {
                if (chip.isArming) {
                    // Same 40dp footprint as the button it replaces, so the row doesn't reflow.
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        AdaptiveCircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = content,
                        )
                    }
                } else {
                    IconButton(
                        onClick = onInterrupt,
                        enabled = canInterrupt,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOff,
                            contentDescription = stringResource(
                                Res.string.cd_interrupt_steer_now,
                                chip.text.take(INTERRUPT_CD_MAX_CHARS),
                            ),
                            tint = if (canInterrupt) content else content.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            IconButton(onClick = onCancel, modifier = Modifier.alpha(0.8f)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.cd_cancel_steer, chip.text.take(INTERRUPT_CD_MAX_CHARS)),
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The chip's "Interrupt now" and × name the message, like web: its first 80 characters. */
private const val INTERRUPT_CD_MAX_CHARS = 80

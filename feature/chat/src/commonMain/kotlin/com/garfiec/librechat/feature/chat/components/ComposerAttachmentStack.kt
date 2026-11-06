package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_collapse
import com.garfiec.librechat.feature.chat.resources.cd_draft_stack
import com.garfiec.librechat.feature.chat.resources.cd_expand
import com.garfiec.librechat.feature.chat.resources.draft_item_attached_announcement
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.util.DraftItems
import com.garfiec.librechat.feature.chat.util.DraftUploadProgress
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What goes with the message, stacked in the field's leading slot: up to two overlapping tiles
 * (one when [compact]) and a count, a progress ring while anything uploads, and a red "!" when an
 * upload failed. A tap opens the list ([ComposerDraftListPanel]); [expanded] is whether it is open.
 * A null [onClick] (a tool-approval pause holds the list's space) draws the same stack with no click
 * action, for a touch or a screen reader. Draws nothing for an empty [items]; the caller leaves the
 * slot out then, which is why what was added is tracked above it ([additions]).
 *
 * The slot is M3's leading icon, which holds 48dp whatever it draws, so the stack fills that and
 * the compact form only changes what is inside it.
 */
@Composable
internal fun ComposerAttachmentStack(
    items: DraftItems,
    expanded: Boolean,
    compact: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** The ring's progress, read as it draws. */
    uploadProgress: DraftUploadProgress = DraftUploadProgress.NONE,
    /** What was just added, for the pop and the announcement; null draws neither (previews). */
    additions: DraftAdditions? = null,
    /** A screen reader's click (TalkBack, VoiceOver), where it differs from a touch's [onClick]. */
    onScreenReaderClick: (() -> Unit)? = onClick,
) {
    if (items.isEmpty) return
    val count = items.items.size
    val shown = items.items.take(if (compact) 1 else 2)

    // A pop as something is added; a removal settles without one.
    val scale = remember { Animatable(1f) }
    LaunchedEffect(additions?.seq) {
        if (additions?.takePop() == true) {
            scale.snapTo(POP_SCALE)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val lastAdded = additions?.lastAdded

    val countDescription = pluralStringResource(Res.plurals.cd_draft_stack, count, count)
    // Announced as the live region changes: what was just added, then how many go with the message.
    val description = lastAdded?.let { pluralStringResource(Res.plurals.draft_item_attached_announcement, count, it, count) }
        ?: countDescription
    val clickLabel = stringResource(if (expanded) Res.string.cd_collapse else Res.string.cd_expand)
    Box(
        modifier = modifier
            .size(width = STACK_SLOT, height = STACK_SLOT)
            .clip(RoundedCornerShape(12.dp))
            // Ahead of the clickable: a node's semantics are applied from the inner modifier out,
            // so only an outer block's OnClick replaces the clickable's touch one.
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
                val screenReaderClick = onScreenReaderClick ?: onClick
                if (onClick != null && screenReaderClick != null) {
                    role = Role.Button
                    this.onClick(label = clickLabel) {
                        screenReaderClick()
                        true
                    }
                }
            }
            .then(if (onClick != null) Modifier.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(TILE + TILE_STEP * (shown.size - 1))
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        ) {
            val ring = MaterialTheme.colorScheme.surface
            shown.forEachIndexed { index, item ->
                DraftItemTile(
                    item = item,
                    size = TILE,
                    modifier = Modifier
                        .offset(x = TILE_STEP * index)
                        .border(2.dp, ring, TileShape),
                )
            }
            // On the front tile, as each list row draws its own.
            if (items.hasUploads) {
                Box(
                    modifier = Modifier
                        .offset(x = TILE_STEP * (shown.size - 1))
                        .size(TILE)
                        .clip(TileShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    UploadRing(uploadProgress::overall, RING_SIZE, MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
        if (count > 1) {
            StackBadge(
                text = count.toString(),
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        if (items.hasFailedUploads) {
            StackBadge(
                text = "!",
                container = MaterialTheme.colorScheme.error,
                content = MaterialTheme.colorScheme.onError,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * An upload's ring: determinate while [progress] reports one, else indeterminate. [progress] is read
 * as the ring draws, so a tick redraws it and recomposes nothing; only a switch between reported and
 * unreported recomposes it.
 */
@Composable
internal fun UploadRing(progress: () -> Float?, size: Dp, color: Color) {
    val current by rememberUpdatedState(progress)
    val determinate by remember { derivedStateOf { current() != null } }
    if (determinate) {
        AdaptiveCircularProgressIndicator(
            progress = { current() ?: 0f },
            modifier = Modifier.size(size),
            strokeWidth = 2.dp,
            color = color,
        )
    } else {
        AdaptiveCircularProgressIndicator(modifier = Modifier.size(size), strokeWidth = 2.dp, color = color)
    }
}

/** A 16dp corner badge on the stack; its text ignores font scale so it stays inside the circle. */
@Composable
private fun StackBadge(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = BADGE, minHeight = BADGE)
            .background(container, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Text(
                text = text,
                color = content,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * One draft item's tile, shared by the stack and the list rows: an image's thumbnail, a file's type
 * tile, a skill's or a quote's glyph.
 */
@Composable
internal fun DraftItemTile(item: DraftItem, size: Dp, modifier: Modifier = Modifier) {
    when (item) {
        is DraftItem.Image -> Box(
            modifier = modifier
                .size(size)
                .clip(TileShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 2),
            )
            if (item.thumbnail != null) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
            }
        }

        is DraftItem.File -> {
            val kind = remember(item.file.name, item.file.type) { fileKindOf(item.file.name, item.file.type) }
            FileTypeTile(kind = kind, size = size, shape = TileShape, modifier = modifier)
        }

        is DraftItem.Skill -> GlyphTile(
            icon = SkillGlyph,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = size,
            modifier = modifier,
        )

        is DraftItem.Quote -> GlyphTile(
            icon = Icons.Default.FormatQuote,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            size = size,
            modifier = modifier,
        )
    }
}

@Composable
private fun GlyphTile(
    icon: ImageVector,
    container: Color,
    content: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(TileShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 4 / 7))
    }
}

/** What a row and an announcement call the item: a file's name, a skill's name, a quote's text. */
internal fun DraftItem.label(): String = when (this) {
    is DraftItem.Attachment -> file.name
    is DraftItem.Skill -> name
    is DraftItem.Quote -> text
}

private val TileShape = RoundedCornerShape(8.dp)

/** M3's leading-icon slot, which the stack fills. */
internal val STACK_SLOT = 48.dp
private val TILE = 28.dp
private val TILE_STEP = 12.dp
private val RING_SIZE = 16.dp
private val BADGE = 16.dp
private const val POP_SCALE = 1.16f

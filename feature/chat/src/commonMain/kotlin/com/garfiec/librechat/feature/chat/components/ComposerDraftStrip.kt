package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.model.files.fileKindOf
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.core.ui.components.AdaptiveChipDefaults
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.FileTypeTile
import com.garfiec.librechat.core.ui.components.fileTypeSubtitle
import com.garfiec.librechat.core.ui.components.scrollFadingEdges
import com.garfiec.librechat.core.ui.media.mediaThumbnail
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_cancel_upload
import com.garfiec.librechat.feature.chat.resources.cd_draft_stack
import com.garfiec.librechat.feature.chat.resources.cd_open_file
import com.garfiec.librechat.feature.chat.resources.cd_remove_file
import com.garfiec.librechat.feature.chat.resources.cd_remove_quote_named
import com.garfiec.librechat.feature.chat.resources.cd_remove_skill
import com.garfiec.librechat.feature.chat.resources.cd_retry_upload
import com.garfiec.librechat.feature.chat.resources.cd_upload_route_code
import com.garfiec.librechat.feature.chat.resources.cd_upload_route_text
import com.garfiec.librechat.feature.chat.resources.draft_item_attached_announcement
import com.garfiec.librechat.feature.chat.resources.draft_row_failed
import com.garfiec.librechat.feature.chat.resources.draft_row_quote
import com.garfiec.librechat.feature.chat.resources.draft_row_skill
import com.garfiec.librechat.feature.chat.resources.draft_row_uploading
import com.garfiec.librechat.feature.chat.resources.draft_subtitle_join
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.util.DraftItemTap
import com.garfiec.librechat.feature.chat.util.DraftItems
import com.garfiec.librechat.feature.chat.util.DraftUploadProgress
import com.garfiec.librechat.feature.chat.util.taps
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ComposerDraftStrip(chrome: ComposerChrome) {
    // The items it last showed, so a strip leaving because it emptied doesn't shrink away blank.
    val kept = remember { KeptDraftItems() }
    val items = kept.of(chrome.draftItems)
    AnimatedVisibility(
        visible = !chrome.draftItems.isEmpty,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        DraftStrip(
            items = items,
            onRemove = chrome.onRemoveDraftItem,
            onTap = chrome.onDraftItemTap,
            uploadProgress = chrome.draftUploadProgress,
            additions = chrome.draftAdditions,
            // While it shrinks away the items it shows are gone (sent or removed): nothing acts on them.
            enabled = !chrome.draftItems.isEmpty,
            modifier = Modifier.passVerticalDragsToThread(),
        )
    }
}

private class KeptDraftItems {
    private var last = DraftItems.EMPTY

    fun of(items: DraftItems): DraftItems {
        if (!items.isEmpty) last = items
        return last
    }
}

@Composable
internal fun DraftStrip(
    items: DraftItems,
    onRemove: (DraftItem) -> Unit,
    onTap: (DraftItemTap) -> Unit,
    modifier: Modifier = Modifier,
    uploadProgress: DraftUploadProgress = DraftUploadProgress.NONE,
    /** What was just added, for the pop and the announcement; null draws neither (previews). */
    additions: DraftAdditions? = null,
    /** False: drawn only (taps and × do nothing). */
    enabled: Boolean = true,
) {
    val count = items.items.size
    val taps = remember(items) { items.taps() }
    val countDescription = pluralStringResource(Res.plurals.cd_draft_stack, count, count)
    val description = additions?.lastAdded
        ?.let { pluralStringResource(Res.plurals.draft_item_attached_announcement, count, it, count) }
        ?: countDescription
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .scrollFadingEdges(scroll, Orientation.Horizontal)
                .horizontalScroll(scroll)
                // Inside the scroll, so the corner × of the first and last items isn't clipped.
                .padding(horizontal = BADGE_OVERHANG, vertical = BADGE_OVERHANG),
            horizontalArrangement = Arrangement.spacedBy(ITEM_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.items.forEach { item ->
                key(item.key) {
                    DraftStripItem(
                        item = item,
                        tap = taps[item.key]?.takeIf { enabled },
                        onRemove = { if (enabled) onRemove(item) },
                        onTap = onTap,
                        uploadProgress = uploadProgress,
                        additions = additions,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraftStripItem(
    item: DraftItem,
    tap: DraftItemTap?,
    onRemove: () -> Unit,
    onTap: (DraftItemTap) -> Unit,
    uploadProgress: DraftUploadProgress,
    additions: DraftAdditions?,
) {
    val attachment = item as? DraftItem.Attachment
    val failed = attachment?.isFailed == true
    val uploading = attachment?.isUploading == true
    val tapLabel = when (tap) {
        null -> null
        is DraftItemTap.Retry -> stringResource(Res.string.cd_retry_upload, tap.file.name)
        else -> stringResource(Res.string.cd_open_file, item.label())
    }
    // Scrolled into view before the pop: the strip is sorted, so the newest item may land anywhere.
    val scale = remember { Animatable(1f) }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(additions?.seq) {
        if (additions != null && additions.lastAddedKey == item.key && additions.takePop()) {
            bringIntoView.bringIntoView()
            scale.snapTo(POP_SCALE)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    val subtitle = draftItemSubtitle(item)
    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    ) {
        val body = Modifier
            .clip(ItemShape)
            .then(
                if (tap != null) {
                    Modifier.clickable(onClickLabel = tapLabel, role = Role.Button) { onTap(tap) }
                } else {
                    Modifier
                },
            )
        if (item is DraftItem.Image) {
            val label = item.label()
            Box(
                modifier = body
                    .size(IMAGE_TILE)
                    .semantics { contentDescription = "$label, $subtitle" },
            ) {
                DraftItemTile(item = item, size = IMAGE_TILE, shape = ItemShape)
                if (uploading) UploadOverlay({ uploadProgress.of(item.file) }, IMAGE_TILE)
                if (failed) FailedCover()
            }
        } else {
            DraftCard(item = item, subtitle = subtitle, failed = failed, modifier = body) {
                if (uploading && attachment != null) {
                    UploadOverlay({ uploadProgress.of(attachment.file) }, CARD_TILE)
                }
            }
        }
        RemoveBadge(
            description = removeDescription(item),
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = BADGE_OVERHANG, y = -BADGE_OVERHANG),
        )
    }
}

@Composable
private fun DraftCard(
    item: DraftItem,
    subtitle: String,
    failed: Boolean,
    modifier: Modifier = Modifier,
    tileOverlay: @Composable () -> Unit = {},
) {
    // Glass's fill is the box's own, so a glass card takes the outline as its edge.
    val glass = isLiquidGlass
    val container = if (glass) AdaptiveChipDefaults.overlayContainerColor() else MaterialTheme.colorScheme.surfaceContainerHighest
    val edge = when {
        failed -> Modifier.border(2.dp, MaterialTheme.colorScheme.error, ItemShape)
        glass -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, ItemShape)
        else -> Modifier
    }
    Row(
        modifier = modifier
            .heightIn(min = IMAGE_TILE)
            .widthIn(min = CARD_MIN_WIDTH, max = CARD_MAX_WIDTH)
            .background(container)
            .then(edge)
            .semantics(mergeDescendants = true) {}
            .padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            DraftItemTile(item = item, size = CARD_TILE)
            tileOverlay()
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = item.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FailedCover() {
    val error = MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .size(IMAGE_TILE)
            .background(error.copy(alpha = FAILED_SCRIM_ALPHA), ItemShape)
            .border(2.dp, error, ItemShape),
        contentAlignment = Alignment.Center,
    ) {
        FixedScaleText(text = "!", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * Drawn small on purpose: Compose widens its touch target only where no other target is hit, so a tap
 * on the item opens it and a drag along the strip removes nothing.
 */
@Composable
private fun RemoveBadge(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BADGE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** Text that ignores font scale, so it stays inside the cover drawn around it. */
@Composable
private fun FixedScaleText(text: String, color: Color, style: androidx.compose.ui.text.TextStyle) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
        Text(text = text, color = color, style = style, fontWeight = FontWeight.Bold)
    }
}

/** [progress] is read as the ring draws, so a tick redraws it without recomposing. */
@Composable
internal fun UploadOverlay(progress: () -> Float?, tileSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(tileSize).background(UploadScrim, TileShape),
        contentAlignment = Alignment.Center,
    ) {
        val current by rememberUpdatedState(progress)
        val determinate by remember { derivedStateOf { current() != null } }
        val ringModifier = Modifier.size(tileSize * RING_FRACTION)
        if (determinate) {
            AdaptiveCircularProgressIndicator(
                progress = { current() ?: 0f },
                modifier = ringModifier,
                strokeWidth = RING_STROKE,
                color = Color.White,
                trackColor = UploadTrack,
            )
        } else {
            AdaptiveCircularProgressIndicator(
                modifier = ringModifier,
                strokeWidth = RING_STROKE,
                color = Color.White,
                trackColor = UploadTrack,
            )
        }
    }
}

/** An image's [shape] is what the media viewer flies back into, so it must match the clip around the tile. */
@Composable
internal fun DraftItemTile(item: DraftItem, size: Dp, modifier: Modifier = Modifier, shape: CornerBasedShape = TileShape) {
    when (item) {
        is DraftItem.Image -> Box(
            modifier = modifier
                .size(size)
                .mediaThumbnail(item.mediaKey, shape)
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
            FileTypeTile(kind = kind, size = size, shape = shape, modifier = modifier)
        }

        is DraftItem.Skill -> GlyphTile(
            icon = SkillGlyph,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = size,
            shape = shape,
            modifier = modifier,
        )

        is DraftItem.Quote -> GlyphTile(
            icon = Icons.Default.FormatQuote,
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            size = size,
            shape = shape,
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
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 4 / 7))
    }
}

internal fun DraftItem.label(): String = when (this) {
    is DraftItem.Attachment -> file.name
    is DraftItem.Skill -> name
    is DraftItem.Quote -> text
}

@Composable
private fun draftItemSubtitle(item: DraftItem): String = when (item) {
    is DraftItem.Attachment -> when {
        item.isFailed -> stringResource(Res.string.draft_row_failed)
        item.isUploading -> stringResource(Res.string.draft_row_uploading)
        else -> {
            val file = item.file
            val kind = remember(file.name, file.type) { fileKindOf(file.name, file.type) }
            val type = fileTypeSubtitle(kind, file.name, null)
            when (file.route) {
                UploadRoute.TEXT -> stringResource(Res.string.draft_subtitle_join, type, stringResource(Res.string.cd_upload_route_text))
                UploadRoute.CODE -> stringResource(Res.string.draft_subtitle_join, type, stringResource(Res.string.cd_upload_route_code))
                UploadRoute.PROVIDER, null -> type
            }
        }
    }

    is DraftItem.Skill -> stringResource(Res.string.draft_row_skill)
    is DraftItem.Quote -> stringResource(Res.string.draft_row_quote)
}

@Composable
private fun removeDescription(item: DraftItem): String = when (item) {
    is DraftItem.Attachment -> if (item.isUploading) {
        stringResource(Res.string.cd_cancel_upload, item.file.name)
    } else {
        stringResource(Res.string.cd_remove_file, item.file.name)
    }

    is DraftItem.Skill -> stringResource(Res.string.cd_remove_skill, item.name)
    is DraftItem.Quote -> stringResource(Res.string.cd_remove_quote_named, item.text.take(QUOTE_CD_CHARS))
}

private val TileShape = RoundedCornerShape(8.dp)
private val ItemShape = RoundedCornerShape(12.dp)

private val IMAGE_TILE = 56.dp
private val CARD_TILE = 32.dp
private val CARD_MIN_WIDTH = 120.dp
private val CARD_MAX_WIDTH = 220.dp
private val ITEM_GAP = 8.dp
private val BADGE = 20.dp

/** How far the corner × reaches past its item, and the strip's padding that keeps it unclipped. */
private val BADGE_OVERHANG = 6.dp
private const val RING_FRACTION = 0.72f
private val RING_STROKE = 3.dp
private const val QUOTE_CD_CHARS = 60
private const val POP_SCALE = 1.08f
private const val FAILED_SCRIM_ALPHA = 0.45f

// Fixed, not theme colours: the overlay sits on a photo, whose colours no theme knows.
private val UploadScrim = Color(0x8C000000)
private val UploadTrack = Color(0x4DFFFFFF)

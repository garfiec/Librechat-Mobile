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
import com.garfiec.librechat.feature.chat.util.tapFor
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The strip at the top of the composer box: everything that goes with the message, in [DraftItems]
 * order, on one row that scrolls sideways. It grows in when the first item is added and shrinks away
 * with the last.
 */
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
        )
    }
}

/** Remembers the last non-empty [DraftItems] it was given. */
private class KeptDraftItems {
    private var last = DraftItems.EMPTY

    fun of(items: DraftItems): DraftItems {
        if (!items.isEmpty) last = items
        return last
    }
}

/**
 * The strip's row: an image as a 56dp thumbnail, a file, skill or quote as a card with its name and
 * kind, each with a corner × ([removeDescription]: cancels a file still uploading, else removes the
 * item with an Undo). An uploading item carries the upload ring ([UploadOverlay]), a failed one a red
 * cover. A tap is [tapFor]'s: a failed file retries, an image opens in the media viewer (and flies
 * back into its tile as the viewer closes), an uploaded file opens in the PDF preview or the file
 * actions; a file still uploading, a skill and a quote aren't tappable.
 *
 * The × is drawn small on the item's corner: Compose widens its touch target to 48dp only where no
 * other target is hit, so a tap on the item opens it and a drag along the strip, which cancels both
 * taps past touch slop, removes nothing.
 *
 * An addition pops its item and scrolls it into view; the row's live region announces what was added
 * and how many items go with the message. Past the edge the row fades.
 */
@Composable
internal fun DraftStrip(
    items: DraftItems,
    onRemove: (DraftItem) -> Unit,
    onTap: (DraftItemTap) -> Unit,
    modifier: Modifier = Modifier,
    /** The upload rings' progress, read as they draw. */
    uploadProgress: DraftUploadProgress = DraftUploadProgress.NONE,
    /** What was just added, for the pop and the announcement; null draws neither (previews). */
    additions: DraftAdditions? = null,
) {
    val count = items.items.size
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
                    val tap = remember(items, item) { items.tapFor(item) }
                    DraftStripItem(
                        item = item,
                        tap = tap,
                        onRemove = { onRemove(item) },
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
    // A pop as this item is added, scrolled into view first: the strip is sorted (failed first,
    // quotes last), so the newest item may land anywhere along it.
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
                DraftItemTile(item = item, size = IMAGE_TILE)
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

/** A file's, skill's or quote's card: its tile, then its name over what it is. */
@Composable
private fun DraftCard(
    item: DraftItem,
    subtitle: String,
    failed: Boolean,
    modifier: Modifier = Modifier,
    tileOverlay: @Composable () -> Unit = {},
) {
    val container = if (isLiquidGlass) {
        AdaptiveChipDefaults.overlayContainerColor()
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    Row(
        modifier = modifier
            .heightIn(min = IMAGE_TILE)
            .widthIn(min = CARD_MIN_WIDTH, max = CARD_MAX_WIDTH)
            .background(container)
            .then(if (failed) Modifier.border(2.dp, MaterialTheme.colorScheme.error, ItemShape) else Modifier)
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

/** A failed image's cover: the error colour over the photo and a "!", so it reads on any photo. */
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

/** The corner ×: drawn 20dp, its touch target widened by Compose where nothing else is hit. */
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

/**
 * An uploading tile's cover: a dark scrim over the [tileSize] tile and a white ring on it, so it
 * reads on any photo in either theme. The ring is determinate while [progress] reports one, else
 * indeterminate. [progress] is read as the ring draws, so a tick redraws it and recomposes nothing;
 * only a switch between reported and unreported recomposes it.
 */
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

/** One draft item's tile: an image's thumbnail, a file's type tile, a skill's or a quote's glyph. */
@Composable
internal fun DraftItemTile(item: DraftItem, size: Dp, modifier: Modifier = Modifier) {
    when (item) {
        is DraftItem.Image -> Box(
            modifier = modifier
                .size(size)
                // Where the media viewer flies back to as it closes.
                .mediaThumbnail(item.mediaKey, TileShape)
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

/** What an item and an announcement call it: a file's name, a skill's name, a quote's text. */
internal fun DraftItem.label(): String = when (this) {
    is DraftItem.Attachment -> file.name
    is DraftItem.Skill -> name
    is DraftItem.Quote -> text
}

/** An item's second line: the file's type and route, its upload state, or what kind of item it is. */
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

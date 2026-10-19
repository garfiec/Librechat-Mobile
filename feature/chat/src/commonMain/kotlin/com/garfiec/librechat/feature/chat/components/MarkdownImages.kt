package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.isExpired
import com.garfiec.librechat.core.model.media.InlineMediaTarget
import com.garfiec.librechat.core.model.media.buildAttachmentsByName
import com.garfiec.librechat.core.model.media.resolveInlineMediaTarget
import com.garfiec.librechat.core.ui.media.mediaThumbnail
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_embedded_image
import com.garfiec.librechat.feature.chat.resources.cd_failed_to_load_image
import com.garfiec.librechat.feature.chat.resources.cd_image
import com.garfiec.librechat.feature.chat.resources.load_image_from_host
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.LocalImageTransformer
import com.mikepenz.markdown.compose.LocalReferenceLinkHandler
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageWidth
import com.mikepenz.markdown.model.PlaceholderConfig
import com.mikepenz.markdown.model.ReferenceLinkHandler
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import io.ktor.http.Url
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil

// Images in reply text. The renderer draws `![alt](src)` first as inline content and promotes it to
// the `image` component once the transformer reports a size taller than 1.5 lines (MarkdownText's
// `buildImageInlineContent`), so the transformer has to report `intrinsicSize` or nothing is ever
// promoted. An image that never loads keeps its inline slot, which shows the alt text instead.

/** A markdown image's source once resolved: load it, or ask before contacting the site it names. */
internal sealed interface MarkdownImageSource {
    val url: String

    data class Ready(override val url: String) : MarkdownImageSource

    data class AskFirst(override val url: String, val host: String) : MarkdownImageSource
}

/**
 * The remote image URLs the user tapped to load, per server: a tap under one account says nothing
 * about the same reply text under another. In memory only: a tap holds until the process ends.
 */
@Stable
internal class RemoteImageApprovals {
    private val approved = mutableStateSetOf<Pair<String, String>>()

    fun contains(baseUrl: String, url: String): Boolean = (baseUrl to url) in approved

    fun approve(baseUrl: String, url: String) {
        approved += baseUrl to url
    }
}

private val ProcessRemoteImageApprovals = RemoteImageApprovals()

/**
 * What a reply's markdown images resolve against: the turn's image attachments by name, the server,
 * and which third-party images the user agreed to load.
 */
@Stable
internal class MarkdownMedia(
    val byName: Map<String, Attachment>,
    val baseUrl: String,
    private val approvals: RemoteImageApprovals,
) {
    /**
     * The gate every image in a reply loads through: [MarkdownImageSource.Ready] is the only result
     * that may be requested. Null when [src] names nothing loadable.
     */
    fun sourceFor(src: String): MarkdownImageSource? {
        val target = targetFor(src) ?: return null
        val host = target.foreignHost
        return if (host == null || approvals.contains(baseUrl, target.url)) {
            MarkdownImageSource.Ready(target.url)
        } else {
            MarkdownImageSource.AskFirst(target.url, host)
        }
    }

    // Pure in [src] for one holder, and asked several times per image per composition (transform,
    // placeholder, block, inline), so each source is resolved — including the Ktor parse — once.
    // Composition-thread only. The approval check stays outside: it changes on a tap.
    private val targets = HashMap<String, InlineMediaTarget?>()

    private fun targetFor(src: String): InlineMediaTarget? = targets.getOrPut(src) {
        resolveInlineMediaTarget(src, byName, baseUrl)?.takeIf { target ->
            // The prompt names a host; the request goes wherever the HTTP client's parser says. A
            // disagreement means the chip would name somewhere the tap doesn't go.
            target.foreignHost?.let { host -> requestHostMatches(target.url, host) } ?: true
        }
    }

    /** The URL to request for [src], or null when nothing may be requested yet. */
    fun urlFor(src: String): String? = (sourceFor(src) as? MarkdownImageSource.Ready)?.url

    fun approve(source: MarkdownImageSource.AskFirst) = approvals.approve(baseUrl, source.url)
}

/** Whether Ktor, which makes the request, parses [url]'s host as the one [label] names. */
internal fun requestHostMatches(url: String, label: String): Boolean {
    val requestHost = runCatching { Url(url).host }.getOrNull()?.lowercase()?.removeSurrounding("[", "]")
    if (requestHost.isNullOrEmpty()) return false
    val hostPort = label.substringAfter("://")
    val shown = if (hostPort.startsWith('[')) hostPort.substringBefore(']').removePrefix("[") else hostPort.substringBefore(':')
    return requestHost == shown
}

/** Unprovided (markdown outside a message), nothing resolves by name and every web image asks first. */
internal val LocalMarkdownMedia = compositionLocalOf { MarkdownMedia(emptyMap(), "", ProcessRemoteImageApprovals) }

/**
 * The source text of the markdown being drawn. The inline image slot is handed the image's link and
 * its paragraph node, not the source, so it needs this to read the alt text.
 */
internal val LocalMarkdownSource = compositionLocalOf<String?> { null }

@Composable
internal fun ProvideMarkdownMedia(attachments: List<Attachment>?, baseUrl: String, content: @Composable () -> Unit) {
    val media = remember(attachments, baseUrl) {
        // An expired sandbox file would only 404: its name falls through to the alt text instead.
        val nowMs = currentTimeMs()
        val live = attachments?.filterNot { it.isExpired(nowMs) }
        MarkdownMedia(buildAttachmentsByName(live), baseUrl, ProcessRemoteImageApprovals)
    }
    CompositionLocalProvider(LocalMarkdownMedia provides media, content = content)
}

/** Loads through Coil (the app's authenticated Ktor client) after resolving the source by name. */
@Stable
internal class ChatImageTransformer(private val media: MarkdownMedia) : ImageTransformer {

    // Null for a source that cannot load (a name this turn did not produce): no request goes out.
    // An image on another site gets a stand-in painter and no request until the user taps it.
    @Composable
    override fun transform(link: String): ImageData? = when (val source = media.sourceFor(link)) {
        null -> null
        is MarkdownImageSource.AskFirst -> AskFirstImageData
        is MarkdownImageSource.Ready -> Coil3ImageTransformerImpl.transform(source.url)
    }

    // The stand-in reports a height no line of text reaches, so the renderer promotes it to the
    // block image component, which has room for a full-size tap target.
    @Composable
    override fun intrinsicSize(painter: Painter): Size =
        if (painter === AskFirstPainter) ASK_FIRST_PROMOTED_SIZE else Coil3ImageTransformerImpl.intrinsicSize(painter)

    // Until a size is known the library reserves a 200dp square, and a failed load never learns one,
    // so the alt chip would sit in a 200dp hole in the paragraph. Hold a chip-sized slot instead.
    override fun placeholderConfig(
        link: String,
        density: Density,
        containerSize: Size,
        imageWidth: ImageWidth,
        imageSize: Size,
        imageSizeChanged: ((link: String, Size) -> Unit)?,
    ): PlaceholderConfig {
        // Where the renderer does not promote (a table cell), the stand-in's size must not open a
        // hole the height of ASK_FIRST_PROMOTED_SIZE: it keeps the chip slot.
        if (imageSize.isSpecified && media.sourceFor(link) !is MarkdownImageSource.AskFirst) {
            return super.placeholderConfig(link, density, containerSize, imageWidth, imageSize, imageSizeChanged)
        }
        val width = if (containerSize.isUnspecified) {
            ImageTransformer.DEFAULT_IMAGE_SIDE_LENGTH_DP
        } else {
            with(density) { containerSize.width.toDp().value }
        }
        return PlaceholderConfig(Size(width, altChipHeightDp(density)), PlaceholderVerticalAlign.TextCenter)
    }
}

/** A promoted image: full width at its own aspect ratio, capped in height. Tap opens the viewer. */
internal val MarkdownBlockImageComponent: MarkdownComponent = { model -> MarkdownBlockImage(model) }

/** The inline slot: a small image that was never promoted, or the alt text while loading or failed. */
internal val MarkdownInlineImageComponent: MarkdownComponent = { model -> MarkdownInlineImage(model) }

@Composable
private fun MarkdownBlockImage(model: MarkdownComponentModel) {
    val link = model.node.imageLink(model.content, LocalReferenceLinkHandler.current) ?: return
    val media = LocalMarkdownMedia.current
    val alt = remember(model.node, model.content) { model.node.imageAlt(model.content) }
    val source = media.sourceFor(link)
    if (source is MarkdownImageSource.AskFirst) {
        LoadRemoteImageChip(
            alt = alt ?: link.substringAfterLast('/').substringBefore('?').ifBlank { null },
            host = source.host,
            onLoad = { media.approve(source) },
        )
        return
    }
    val url = media.urlFor(link) ?: return
    val transformer = LocalImageTransformer.current
    val imageData = transformer.transform(link) ?: return
    val size = transformer.intrinsicSize(imageData.painter)
    val openMedia = LocalChatMediaViewer.current
    val sized = size.isSpecified && size.width > 0f && size.height > 0f
    Image(
        painter = imageData.painter,
        contentDescription = alt ?: stringResource(Res.string.cd_embedded_image),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        modifier = Modifier
            .padding(vertical = 4.dp)
            // An image pixel per dp, as a browser lays out an <img> at its CSS size: a small image
            // is not blown up past its own width. A chart is wider than any phone, so it fills.
            .then(if (sized) Modifier.widthIn(max = size.width.dp) else Modifier.fillMaxWidth())
            // Unsized only while an image the user just agreed to load is on its way: hold the
            // chip's height rather than collapsing the paragraph under it.
            .heightIn(min = if (sized) 0.dp else LOAD_CHIP_MIN_HEIGHT, max = BLOCK_IMAGE_MAX_HEIGHT)
            .then(if (sized) Modifier.aspectRatio(size.width / size.height) else Modifier)
            .mediaThumbnail(url, BlockImageShape)
            .clickable { openMedia(url) }
            .semantics { role = Role.Image },
    )
}

@Composable
private fun MarkdownInlineImage(model: MarkdownComponentModel) {
    val link = model.content
    val media = LocalMarkdownMedia.current
    val imageData = LocalImageTransformer.current.transform(link)
    val painter = imageData?.painter
    val state = (painter as? AsyncImagePainter)?.state?.collectAsState()?.value
    val source = LocalMarkdownSource.current
    val referenceLinks = LocalReferenceLinkHandler.current
    val alt = remember(model.node, source, link) {
        source?.let { model.node.inlineImageAlt(it, link, referenceLinks) }
    }
    val askFirst = media.sourceFor(link) as? MarkdownImageSource.AskFirst
    if (askFirst != null) {
        // Normally promoted to a block on the next frame; stays inline only where the renderer
        // never promotes, and is still tappable there.
        AltTextChip(
            label = stringResource(Res.string.load_image_from_host, askFirst.host),
            failed = false,
            onClick = { media.approve(askFirst) },
        )
    } else if (painter != null && state is AsyncImagePainter.State.Success) {
        Image(
            painter = painter,
            contentDescription = alt ?: stringResource(Res.string.cd_embedded_image),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    } else {
        AltTextChip(
            label = alt ?: link.substringAfterLast('/').ifBlank { stringResource(Res.string.cd_image) },
            failed = imageData == null || state is AsyncImagePainter.State.Error,
        )
    }
}

/** A stand-in for an image that is loading or could not load, or with [onClick], asks to load it. */
@Composable
internal fun AltTextChip(label: String, failed: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val failedDescription = stringResource(Res.string.cd_failed_to_load_image)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clip(AltChipShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AltChipShape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp)
                .semantics { if (failed) stateDescription = failedDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (failed) Icons.Outlined.BrokenImage else Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(lineHeight = ALT_CHIP_LINE_HEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * An image on another site, not yet requested: its alt text, and the site a tap would contact. A
 * tap loads that URL for the rest of the app process.
 */
@Composable
private fun LoadRemoteImageChip(alt: String?, host: String, onLoad: () -> Unit) {
    val action = stringResource(Res.string.load_image_from_host, host)
    DisableSelection {
        Row(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .heightIn(min = LOAD_CHIP_MIN_HEIGHT)
                .clip(LoadChipShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LoadChipShape)
                .clickable(role = Role.Button, onClickLabel = action, onClick = onLoad)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                if (alt != null) {
                    Text(
                        text = alt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Wraps rather than cutting off the host, the one thing the user is deciding on.
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Draws nothing and fetches nothing: what [ChatImageTransformer] hands out for an image that has to ask first. */
private object AskFirstPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() = Unit
}

private val AskFirstImageData = ImageData(painter = AskFirstPainter, contentDescription = null)

/** Taller than any line of text at any font scale, so the renderer always promotes the stand-in. */
private val ASK_FIRST_PROMOTED_SIZE = Size(1f, 100_000f)

/**
 * Chip height for the user's font scale: one label line plus a little padding, on the 4dp grid.
 * It has to fit inside one line of body text: a placeholder taller than the paragraph's fixed line
 * height is not given room, so it overlaps the line above and is clipped below.
 */
private fun altChipHeightDp(density: Density): Float {
    val lineDp = with(density) { ALT_CHIP_LINE_HEIGHT.toDp().value }
    return ceil((lineDp + 2 * ALT_CHIP_VERTICAL_PADDING_DP) / GRID_DP) * GRID_DP
}

// `resolveImageLink` / `resolveImageAlt` are internal to the library; these follow them.

private fun ASTNode.imageLink(source: String, referenceLinks: ReferenceLinkHandler): String? {
    findRecursive(MarkdownElementTypes.LINK_DESTINATION)?.let { return it.getUnescapedTextInNode(source) }
    val reference = findRecursive(MarkdownElementTypes.FULL_REFERENCE_LINK)
        ?: findRecursive(MarkdownElementTypes.SHORT_REFERENCE_LINK)
        ?: return null
    val label = reference.findChildOfType(MarkdownElementTypes.LINK_LABEL)?.getUnescapedTextInNode(source) ?: return null
    return referenceLinks.find(label).takeIf { it.isNotEmpty() }
}

private fun ASTNode.imageAlt(source: String): String? =
    listOf(MarkdownElementTypes.LINK_TEXT, MarkdownElementTypes.LINK_LABEL).firstNotNullOfOrNull { type ->
        findRecursive(type)?.getUnescapedTextInNode(source)?.trim('[', ']')?.trim()?.takeIf { it.isNotEmpty() }
    }

/** The alt text of the image in this paragraph whose link is [link]. */
private fun ASTNode.inlineImageAlt(source: String, link: String, referenceLinks: ReferenceLinkHandler): String? {
    if (type == MarkdownElementTypes.IMAGE) {
        return if (imageLink(source, referenceLinks) == link) imageAlt(source) else null
    }
    return children.firstNotNullOfOrNull { it.inlineImageAlt(source, link, referenceLinks) }
}

private fun ASTNode.findRecursive(type: IElementType): ASTNode? {
    children.forEach { child ->
        if (child.type == type) return child
        child.findRecursive(type)?.let { return it }
    }
    return null
}

private val BLOCK_IMAGE_MAX_HEIGHT = 300.dp
private val BlockImageShape = RoundedCornerShape(12.dp)
private val AltChipShape = RoundedCornerShape(8.dp)
private val LoadChipShape = RoundedCornerShape(12.dp)
private val LOAD_CHIP_MIN_HEIGHT = 48.dp
private val ALT_CHIP_LINE_HEIGHT = 16.sp
private const val ALT_CHIP_VERTICAL_PADDING_DP = 2f
private const val GRID_DP = 4f

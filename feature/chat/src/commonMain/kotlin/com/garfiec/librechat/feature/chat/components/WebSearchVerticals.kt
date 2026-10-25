package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.WebSearchData
import com.garfiec.librechat.core.model.WebSearchPlace
import com.garfiec.librechat.core.model.WebSearchShoppingItem
import com.garfiec.librechat.core.model.WebSearchSource
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.web_search_images
import com.garfiec.librechat.feature.chat.resources.web_search_load_images
import com.garfiec.librechat.feature.chat.resources.web_search_places
import com.garfiec.librechat.feature.chat.resources.web_search_shopping
import com.garfiec.librechat.feature.chat.util.ContentGroup
import io.ktor.http.encodeURLQueryComponent
import org.jetbrains.compose.resources.stringResource

/**
 * The provider's image, shopping and place results for a search, shown under its card (upstream
 * `verticals.tsx`). Attachment-only: verticals never stream.
 *
 * Each list keeps only entries a row can act on, as upstream does: an image needs both a picture
 * and somewhere to go, a product a link and a title, a place a name or an address. Every URL a row
 * turns into a request or a link must be http(s): the provider payload is copied into the
 * attachment unvalidated, so a `javascript:` target is otherwise one tap away.
 */
@Immutable
internal data class SearchVerticals(
    val images: List<WebSearchSource>,
    val shopping: List<WebSearchShoppingItem>,
    val places: List<WebSearchPlace>,
) {
    val isEmpty: Boolean get() = images.isEmpty() && shopping.isEmpty() && places.isEmpty()

    companion object {
        val Empty = SearchVerticals(emptyList(), emptyList(), emptyList())
    }
}

internal fun collectSearchVerticals(data: List<WebSearchData>): SearchVerticals {
    if (data.isEmpty()) return SearchVerticals.Empty
    return SearchVerticals(
        images = data.flatMap { it.images.orEmpty() }.filter { imageSource(it) != null && imageTarget(it) != null },
        shopping = data.flatMap { it.shopping.orEmpty() }.filter { safeWebUrl(it.link) != null && !it.title.isNullOrBlank() },
        places = data.flatMap { it.places.orEmpty() }.filter { !it.name.isNullOrBlank() || !it.address.isNullOrBlank() },
    )
}

/** The verticals of every `web_search` attachment in [attachments] — a group's, hoisted below it. */
internal fun collectAttachmentSearchVerticals(attachments: List<Attachment>): SearchVerticals =
    collectSearchVerticals(webSearchData(attachments, toolCallId = null))

/** [url] when it is an http(s) link, the only targets a search result may open or load. */
internal fun safeWebUrl(url: String?): String? {
    val trimmed = url?.trim() ?: return null
    val scheme = trimmed.substringBefore("://", "").lowercase()
    return trimmed.takeIf { (scheme == "https" || scheme == "http") && trimmed.length > scheme.length + 3 }
}

private fun imageSource(image: WebSearchSource): String? = safeWebUrl(image.thumbnailUrl) ?: safeWebUrl(image.imageUrl)

private fun imageTarget(image: WebSearchSource): String? =
    safeWebUrl(image.link) ?: safeWebUrl(image.googleUrl) ?: safeWebUrl(image.imageUrl)

private const val MAX_VERTICAL_ITEMS = 8
private const val MAX_PLACES = 5

/** True on iOS. */
internal expect val prefersAppleMaps: Boolean

/**
 * A map search for [place]: Apple Maps where it is the platform's own, Google Maps elsewhere
 * (upstream `mapLink`). Both are keyless https links that fall back to a web map.
 */
internal fun mapLink(place: WebSearchPlace, appleMaps: Boolean): String {
    val query = listOfNotNull(place.name, place.address).filter { it.isNotBlank() }.joinToString(", ")
    val lat = place.latitudeValue
    val lng = place.longitudeValue
    val coords = if (lat != null && lng != null) "$lat,$lng" else ""
    return if (appleMaps) {
        val params = buildList {
            if (query.isNotEmpty()) add("q=${query.encodeURLQueryComponent()}")
            if (coords.isNotEmpty()) add("ll=$coords")
        }
        "https://maps.apple.com/?" + params.joinToString("&")
    } else {
        "https://www.google.com/maps/search/?api=1&query=" + query.ifEmpty { coords }.encodeURLQueryComponent()
    }
}

/**
 * The strips themselves. Pictures come from third-party hosts, so like a reply's remote images
 * (`MarkdownImages.kt`) they load only after a tap, remembered for the card: fetching them tells
 * those hosts who is reading. Until then a tile shows its title over a placeholder.
 */
@Composable
internal fun SearchVerticalsSection(
    verticals: SearchVerticals,
    stateKey: String,
    modifier: Modifier = Modifier,
) {
    if (verticals.isEmpty) return
    var loadImages by rememberSaveable(key = "search-verticals-images:$stateKey") { mutableStateOf(false) }
    val hasPictures = verticals.images.isNotEmpty() || verticals.shopping.any { safeWebUrl(it.imageUrl) != null }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasPictures && !loadImages) {
            LoadImagesChip(onClick = { loadImages = true })
        }
        if (verticals.images.isNotEmpty()) ImageStrip(verticals.images, loadImages)
        if (verticals.shopping.isNotEmpty()) ShoppingStrip(verticals.shopping, loadImages)
        if (verticals.places.isNotEmpty()) PlaceList(verticals.places)
    }
}

@Composable
internal fun LoadImagesChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Image,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.web_search_load_images),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImageStrip(images: List<WebSearchSource>, load: Boolean) {
    val uriHandler = LocalUriHandler.current
    val label = stringResource(Res.string.web_search_images)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.take(MAX_VERTICAL_ITEMS).forEach { image ->
            val target = imageTarget(image) ?: return@forEach
            val name = image.title?.takeIf { it.isNotBlank() } ?: image.domain ?: label
            Box(
                modifier = Modifier
                    .height(112.dp)
                    .aspectRatio(image.thumbnailAspectRatio?.coerceIn(0.5f, 2.5f) ?: 1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(role = Role.Button, onClickLabel = name) { runCatching { uriHandler.openUri(target) } }
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                if (load) {
                    AsyncImage(
                        model = imageSource(image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShoppingStrip(items: List<WebSearchShoppingItem>, load: Boolean) {
    val uriHandler = LocalUriHandler.current
    val label = stringResource(Res.string.web_search_shopping)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.take(MAX_VERTICAL_ITEMS).forEach { item ->
            val link = safeWebUrl(item.link) ?: return@forEach
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { runCatching { uriHandler.openUri(link) } }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val picture = safeWebUrl(item.imageUrl)
                if (picture != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (load) {
                            AsyncImage(
                                model = picture,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = item.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val priceLine = listOfNotNull(item.price, item.source).filter { it.isNotBlank() }.joinToString(" · ")
                if (priceLine.isNotEmpty()) {
                    Text(
                        text = priceLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                item.delivery?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RatingBadge(item.ratingValue, item.ratingCountValue)
            }
        }
    }
}

@Composable
private fun PlaceList(places: List<WebSearchPlace>) {
    val uriHandler = LocalUriHandler.current
    val label = stringResource(Res.string.web_search_places)
    val appleMaps = prefersAppleMaps
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .semantics { contentDescription = label },
    ) {
        places.take(MAX_PLACES).forEach { place ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { runCatching { uriHandler.openUri(mapLink(place, appleMaps)) } }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name?.takeIf { it.isNotBlank() } ?: place.address.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val detail = listOfNotNull(place.category, place.address).filter { it.isNotBlank() }.joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                RatingBadge(place.ratingValue, place.ratingCountValue)
            }
        }
    }
}

@Composable
private fun RatingBadge(rating: Double?, ratingCount: Long?) {
    if (rating == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(2.dp))
        val text = if (ratingCount != null) "${formatRating(rating)} ($ratingCount)" else formatRating(rating)
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A rating as the provider wrote it: `4.5`, or `4` for a whole number. */
internal fun formatRating(rating: Double): String =
    if (rating == rating.toLong().toDouble()) rating.toLong().toString() else rating.toString()

/** What an activity group of searches only is about (upstream `ToolCallGroup.resolveGroupLabel`). */
internal enum class SearchGroupKind { WEB, FILES, WEB_AND_FILES }

/** The header of a group made only of searches: its kind and the sites its web searches read. */
@Immutable
internal data class SearchGroupHeader(val kind: SearchGroupKind, val hosts: List<String>)

/**
 * The search header for [group], or null when the group is not all searches or the server wrote
 * its own label (which always wins). Its favicons are the first three distinct sites across the
 * group's web searches, each search's attachments scoped to its own call (upstream
 * `getSourceDomains`).
 */
internal fun searchGroupHeader(
    group: ContentGroup.Activity,
    attachments: List<Attachment>,
): SearchGroupHeader? {
    if (group.labelText.isNotEmpty()) return null
    val calls = group.entries.mapNotNull { it.part.toolCall }
    if (calls.isEmpty() || calls.size != group.toolCount) return null
    val names = calls.map { (it.name ?: it.function?.name).orEmpty().lowercase() }
    val web = names.count { it == ToolConstants.WEB_SEARCH }
    val files = names.count { isFileSearchToolCall(it) }
    if (web + files != calls.size) return null
    val kind = when {
        files == 0 -> SearchGroupKind.WEB
        web == 0 -> SearchGroupKind.FILES
        else -> SearchGroupKind.WEB_AND_FILES
    }
    val hosts = calls.filter { (it.name ?: it.function?.name).orEmpty().lowercase() == ToolConstants.WEB_SEARCH }
        .flatMap { call -> collectWebSearchSources(attachments, call.id?.takeIf { it.isNotEmpty() }) }
        .mapNotNull { hostOf(it.url) }
        .distinct()
        .take(MAX_STACKED_FAVICONS)
    return SearchGroupHeader(kind, hosts)
}

private val MD_IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
private val MD_LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val MD_LINE_PREFIX = Regex("""(?m)^\s{0,3}(?:#{1,6}\s+|>\s?|[-*+]\s+(?=\S))""")
private val MD_MARKS = Regex("""\*{1,3}|_{2,3}|`+|\|""")
private val MD_UNDERSCORE_EMPHASIS = Regex("""(?<!\w)_(\S(?:[^_]*\S)?)_(?!\w)""")
private val MD_RULE = Regex("""(?m)^\s*[-=:|\s]{3,}$""")
private val WHITESPACE = Regex("""\s+""")

/**
 * A provider snippet as plain text. Scrapers return page markdown ("#### [Race](/f1/…)",
 * "**Apple** …", table pipes), and a snippet is drawn as a few plain lines, where the syntax only
 * gets in the way of the words.
 */
internal fun plainSnippet(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val plain = text
        .replace(MD_IMAGE, "$1")
        .replace(MD_LINK, "$1")
        .replace(MD_RULE, " ")
        .replace(MD_LINE_PREFIX, "")
        .replace(MD_MARKS, "")
        .replace(MD_UNDERSCORE_EMPHASIS, "$1")
        .replace(WHITESPACE, " ")
        .trim()
    return plain.ifEmpty { null }
}

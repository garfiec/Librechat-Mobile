package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.SubcomposeAsyncImage
import com.garfiec.librechat.core.common.datetime.parseIsoInstantOrNull
import com.garfiec.librechat.core.model.WebSearchAnswerBox
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_search_result
import com.garfiec.librechat.feature.chat.resources.state_collapsed
import com.garfiec.librechat.feature.chat.resources.state_expanded
import com.garfiec.librechat.feature.chat.resources.web_search_details
import com.garfiec.librechat.feature.chat.resources.web_search_processing
import com.garfiec.librechat.feature.chat.resources.web_search_query
import com.garfiec.librechat.feature.chat.resources.web_search_reading
import com.garfiec.librechat.feature.chat.resources.web_search_sources
import com.garfiec.librechat.feature.chat.resources.web_searched
import com.garfiec.librechat.feature.chat.resources.web_searching
import com.garfiec.librechat.feature.chat.resources.web_searching_again
import com.garfiec.librechat.feature.chat.viewmodel.SiteIconsState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A single web search source parsed from a message's `web_search` attachment (or, as a
 * fallback, the tool-call output). [url] is the canonical dedup key.
 */
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val favicon: String? = null,
    /** The provider's date: a short local date when it is ISO, else as given (upstream renders it raw). */
    val date: String? = null,
)

internal const val MAX_STACKED_FAVICONS = 3

/** Google's favicon service. Sends [host] to Google, so only [Favicon] calls it, and only when icons are on. */
internal fun faviconUrl(host: String): String =
    "https://www.google.com/s2/favicons?domain=$host&sz=48"

/** Globe placeholder shown while a favicon loads or when it fails to load. */
@Composable
private fun GlobeIcon(size: Dp) {
    Icon(
        imageVector = Icons.Default.Language,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A favicon image with a globe fallback while loading / on error. [model] is a fully-resolved
 * image URL — an explicit per-source favicon when the payload carries one, otherwise the
 * host-derived [faviconUrl].
 */
@Composable
internal fun Favicon(model: String, size: Dp, modifier: Modifier = Modifier) {
    // The one place an external icon is requested, so the opt-in gate lives here: any caller is
    // off until the user turns site icons on.
    if (!LocalSiteIcons.current.state.showIcons) {
        GlobeIcon(size)
        return
    }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        modifier = modifier.size(size).clip(CircleShape),
        error = { GlobeIcon(size) },
        loading = { GlobeIcon(size) },
    )
}

/** Overlapping stack of up to [MAX_STACKED_FAVICONS] domain favicons (first drawn on top). */
@Composable
internal fun SourceFaviconStack(hosts: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { },
        horizontalArrangement = Arrangement.spacedBy((-7).dp),
    ) {
        hosts.forEachIndexed { i, host ->
            Box(
                modifier = Modifier
                    .zIndex((hosts.size - i).toFloat())
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Favicon(model = faviconUrl(host), size = 14.dp)
            }
        }
    }
}

/** Where a web search is, as its card says it (upstream `WebSearch.tsx`). */
internal enum class WebSearchPhase {
    /** Running, with no results yet: "Searching the web" ("…again" for a later search turn). */
    SEARCHING,

    /** Running, with results arriving: "Processing results". */
    PROCESSING,

    /** Done, and the reply has not moved past it yet: "Reading results". */
    READING,

    /** Settled: "Searched the web", with its sources behind a chevron. */
    COMPLETE,
}

private const val MAX_STREAMING_FAVICONS = 5
private val SOURCE_LIST_MAX_HEIGHT = 280.dp

/**
 * A web search as the web client draws it. While it runs, a shimmering phase line with the latest
 * sources' favicons. Once settled, a "Searched the web" row: the first three sites' favicons, a
 * chevron opening every source (favicon, title, two lines of snippet, domain and date), and an info
 * button for the query, the source count and the provider's direct answer. The image, shopping and
 * place results sit underneath ([verticals]; the caller passes [SearchVerticals.Empty] when a group
 * shows them below itself instead).
 *
 * [intent] is the model's own label for the call; it replaces the phase text and the settled label.
 */
@Suppress("LongParameterList")
@Composable
internal fun WebSearchCard(
    sources: List<WebSearchResult>,
    phase: WebSearchPhase,
    modifier: Modifier = Modifier,
    turn: Int = 0,
    intent: String? = null,
    query: String? = null,
    answerBox: WebSearchAnswerBox? = null,
    verticals: SearchVerticals = SearchVerticals.Empty,
    stateKey: String? = null,
    /** Inside an activity group, where the group already says "Searched the web": label by the query. */
    inGroup: Boolean = false,
) {
    // Icons come from external servers, so they're off until the user opts in; the first card
    // shown while that's undecided raises the one-time prompt. Composed only while asking, so a
    // card costs nothing once the user has chosen.
    val siteIcons = LocalSiteIcons.current
    if (siteIcons.state == SiteIconsState.ASK && sources.isNotEmpty()) {
        LaunchedEffect(Unit) { siteIcons.requestChoice() }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (phase == WebSearchPhase.COMPLETE) {
            SettledWebSearch(sources, intent, query, answerBox, stateKey, inGroup)
            if (!verticals.isEmpty) {
                SearchVerticalsSection(
                    verticals = verticals,
                    stateKey = stateKey ?: "web-search",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            RunningWebSearch(sources, phase, turn, intent)
        }
    }
}

@Composable
private fun RunningWebSearch(sources: List<WebSearchResult>, phase: WebSearchPhase, turn: Int, intent: String?) {
    val phaseText = stringResource(
        when (phase) {
            WebSearchPhase.READING -> Res.string.web_search_reading
            WebSearchPhase.PROCESSING -> Res.string.web_search_processing
            else -> if (turn > 0) Res.string.web_searching_again else Res.string.web_searching
        },
    )
    // The latest sources, as they arrive (upstream `StackedFavicons start={-5}`).
    val hosts = remember(sources) {
        sources.takeLast(MAX_STREAMING_FAVICONS).mapNotNull { hostOf(it.url) }.distinct()
    }
    Row(
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 2.dp)
            // The phase, not the growing intent: a live region re-announces every change.
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = phaseText },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hosts.isNotEmpty()) {
            SourceFaviconStack(hosts)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        ShimmeringLabel(text = intent ?: phaseText)
    }
}

/** A label that pulses while its work is running, as upstream's shimmer does. */
@Composable
internal fun ShimmeringLabel(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "shimmer-alpha",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
}

@Composable
private fun SettledWebSearch(
    sources: List<WebSearchResult>,
    intent: String?,
    query: String?,
    answerBox: WebSearchAnswerBox?,
    stateKey: String?,
    inGroup: Boolean,
) {
    var isExpanded by rememberSaveable(key = stateKey?.let { "web-search:$it" }) { mutableStateOf(false) }
    var showDetails by rememberSaveable(key = stateKey?.let { "web-search-details:$it" }) { mutableStateOf(false) }
    val hasSources = sources.isNotEmpty()
    val hasDetails = query != null || answerBox != null
    // Grouped searches would otherwise read "Searched the web" row after row; the query tells them apart.
    val label = intent ?: query?.takeIf { inGroup } ?: stringResource(Res.string.web_searched)
    val count = pluralStringResource(Res.plurals.web_search_sources, sources.size, sources.size)
    val rowDescription = if (hasSources) "$label - $count" else label
    val expansionState = stringResource(if (isExpanded) Res.string.state_expanded else Res.string.state_collapsed)
    val previewHosts = remember(sources) {
        sources.mapNotNull { hostOf(it.url) }.distinct().take(MAX_STACKED_FAVICONS)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(999.dp))
                .then(if (hasSources) Modifier.clickable { isExpanded = !isExpanded } else Modifier)
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                    if (hasSources) {
                        role = Role.Button
                        stateDescription = expansionState
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (previewHosts.isNotEmpty()) {
                SourceFaviconStack(previewHosts)
            } else {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasSources) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasDetails) {
            // Upstream reveals this on hover; touch has no hover, so it is always there.
            IconButton(onClick = { showDetails = !showDetails }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(Res.string.web_search_details),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    AnimatedVisibility(visible = showDetails && hasDetails, enter = expandVertically(), exit = shrinkVertically()) {
        WebSearchDetails(query, count, answerBox)
    }

    if (hasSources) {
        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            SourceList(sources)
        }
    }
}

/** The query, how many sources it found, and the provider's direct answer (upstream's hover card). */
@Composable
private fun WebSearchDetails(query: String?, count: String, answerBox: WebSearchAnswerBox?) {
    Column(
        modifier = Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (query != null) {
            Column {
                Text(
                    text = stringResource(Res.string.web_search_query).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = query, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(text = count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val answer = answerBox?.text
        if (answerBox != null && (answerBox.title != null || answer != null)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(8.dp),
            ) {
                answerBox.title?.let {
                    Text(text = it, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                answer?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/** Every source, scrolling past [SOURCE_LIST_MAX_HEIGHT]; a row opens its page. */
@Composable
private fun SourceList(sources: List<WebSearchResult>) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .heightIn(max = SOURCE_LIST_MAX_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState()),
    ) {
        sources.forEachIndexed { index, result ->
            val domain = hostOf(result.url) ?: result.url
            val rowCd = stringResource(Res.string.cd_search_result, result.title)
            val snippet = remember(result.snippet) { plainSnippet(result.snippet) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { runCatching { uriHandler.openUri(result.url) } }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = rowCd
                    },
                verticalAlignment = if (snippet != null) Alignment.Top else Alignment.CenterVertically,
            ) {
                Favicon(model = result.favicon ?: faviconUrl(domain), size = 16.dp, modifier = Modifier.padding(top = 2.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    snippet?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthInFraction(max = 0.35f)) {
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    result.date?.let { raw ->
                        val fmt = LocalDateTimeFormat.current
                        Text(
                            text = parseIsoInstantOrNull(raw)?.let { fmt.format(it, fmt.patterns.dateWithYear) } ?: raw,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (index < sources.lastIndex) {
                AdaptiveDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

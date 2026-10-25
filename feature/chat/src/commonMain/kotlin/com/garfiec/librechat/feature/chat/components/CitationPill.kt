package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.model.content.CITATION_ANCHOR
import com.garfiec.librechat.core.model.content.CitationIndex
import com.garfiec.librechat.core.model.content.CitationPiece
import com.garfiec.librechat.core.model.content.CitationSource
import com.garfiec.librechat.core.model.content.citationGroupsIn
import com.garfiec.librechat.core.model.content.citationPieces
import com.garfiec.librechat.core.model.content.normalizeCitations
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.action_open
import com.garfiec.librechat.feature.chat.resources.cd_citation
import com.garfiec.librechat.feature.chat.resources.cd_citation_more
import com.garfiec.librechat.feature.chat.resources.citation_sources_title
import com.garfiec.librechat.feature.chat.resources.file_search_pages
import com.garfiec.librechat.feature.chat.resources.file_search_relevance
import com.mikepenz.markdown.model.MarkdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The renderer's per-node hook for citations: claims a text leaf holding a canonical group (see
 * [normalizeCitations]) and re-emits it as text plus one inline-content placeholder per group, which
 * [rememberCitationInlineContent] resolves to a pill.
 *
 * A group always lands in a single text leaf: its characters are neither whitespace nor markdown
 * punctuation, so the lexer never splits one. A leaf can also hold the streaming cursor's sentinel,
 * which this emits as the cursor itself, since a claimed leaf is never offered to the cursor's hook.
 *
 * Exposed as a plain lambda so `CitationRenderingTest` can run it against a real parse.
 */
internal val citationAnnotate: AnnotatedString.Builder.(String, ASTNode) -> Boolean = { content, child ->
    if (child.type != MarkdownTokenTypes.TEXT || child.isInCode()) {
        false
    } else {
        val text = child.getUnescapedTextInNode(content)
        if (CITATION_ANCHOR !in text) {
            false
        } else {
            for (piece in citationPieces(text)) {
                when (piece) {
                    is CitationPiece.Text -> appendWithCursor(piece.text)
                    // A space, not the default U+FFFD: it is what a copy of the selection carries.
                    is CitationPiece.Group -> appendInlineContent(piece.group.inlineId, " ")
                }
            }
            true
        }
    }
}

private fun AnnotatedString.Builder.appendWithCursor(text: String) {
    val at = text.indexOf(STREAMING_CURSOR_SENTINEL)
    if (at < 0) {
        append(text)
    } else {
        append(text.substring(0, at))
        appendInlineContent(STREAMING_CURSOR_INLINE_ID, " ")
        append(text.substring(at + STREAMING_CURSOR_SENTINEL.length))
    }
}

private fun ASTNode.isInCode(): Boolean {
    var node = parent
    while (node != null) {
        if (node.type == MarkdownElementTypes.CODE_SPAN ||
            node.type == MarkdownElementTypes.CODE_FENCE ||
            node.type == MarkdownElementTypes.CODE_BLOCK
        ) {
            return true
        }
        node = node.parent
    }
    return false
}

/** One process-wide instance each: the renderer keys internal `remember`s on the annotator. */
internal val CitationAnnotator: MarkdownAnnotator = markdownAnnotator(annotate = citationAnnotate)

internal val CursorAndCitationAnnotator: MarkdownAnnotator = markdownAnnotator { content, child ->
    citationAnnotate(this, content, child) || streamingCursorAnnotate(this, content, child)
}

/** The pill's text: the first source's label, and how many more the group cites. */
internal fun citationPillLabel(sources: List<CitationSource>): String {
    val first = sources.firstOrNull()?.label ?: return ""
    return if (sources.size > 1) "$first +${sources.size - 1}" else first
}

private val PillMaxWidth = 140.dp
private val PillPadding = 6.dp
private val PillMargin = 2.dp

/**
 * Resolves the groups in [content] to pills sized to their labels. A placeholder's size must be
 * known before layout, so each label is measured here, capped at [PillMaxWidth] and ellipsized
 * past it, as the web's chip is. Remembered on its inputs: the renderer keys an internal `remember`
 * on the map, and a fresh one per composition would rebuild it on every streaming delta.
 */
@Composable
internal fun rememberCitationInlineContent(
    content: String,
    index: CitationIndex,
    textStyle: TextStyle,
): Map<String, InlineTextContent> {
    val groups = remember(content) { if (CITATION_ANCHOR in content) citationGroupsIn(content) else emptyList() }
    if (groups.isEmpty()) return emptyMap()
    val sheet = LocalCitationSheet.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = remember(textStyle) { citationLabelStyle(textStyle) }
    return remember(groups, index, labelStyle, density, sheet) {
        val maxLabelPx = with(density) { (PillMaxWidth - (PillPadding + PillMargin) * 2).roundToPx() }
        // Plus slack: the width round-trips px -> sp -> px, and a label laid out a fraction of a
        // pixel narrower than it measured ellipsizes.
        val chromePx = with(density) { ((PillPadding + PillMargin) * 2 + 1.dp).toPx() }
        groups.associate { group ->
            val sources = index.resolve(group)
            val label = sources.firstOrNull()?.label.orEmpty()
            // The count is measured apart and never ellipsized: a long first label would cut it off.
            val more = if (sources.size > 1) " +${sources.size - 1}" else null
            val moreWidth = more?.let { measurer.measure(text = it, style = labelStyle, maxLines = 1).size.width } ?: 0
            val measured = measurer.measure(
                text = label,
                style = labelStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = (maxLabelPx - moreWidth).coerceAtLeast(0)),
            )
            val width = with(density) { (measured.size.width + moreWidth + chromePx).toSp() }
            group.inlineId to InlineTextContent(
                Placeholder(
                    width = if (sources.isEmpty()) 0.01.sp else width,
                    height = PILL_HEIGHT_EM.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                if (sources.isNotEmpty()) {
                    CitationPill(label = label, more = more, sources = sources, style = labelStyle, onClick = { sheet.open(sources) })
                }
            }
        }
    }
}

private const val PILL_HEIGHT_EM = 1.3f
private const val PILL_FONT_SCALE = 0.75f

private fun citationLabelStyle(textStyle: TextStyle): TextStyle {
    val size = if (textStyle.fontSize.isSpecified) textStyle.fontSize * PILL_FONT_SCALE else 12.sp
    return textStyle.copy(fontSize = size, lineHeight = size * 1.2f)
}

@Composable
private fun CitationPill(
    label: String,
    more: String?,
    sources: List<CitationSource>,
    style: TextStyle,
    onClick: () -> Unit,
) {
    val description = if (sources.size > 1) {
        pluralStringResource(Res.plurals.cd_citation_more, sources.size - 1, sources.first().label, sources.size - 1)
    } else {
        stringResource(Res.string.cd_citation, label)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = PillMargin)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = PillPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (more != null) {
                Text(text = more, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Which group's sources the sheet is showing; empty while it is closed. */
@Stable
internal class CitationSheetState {
    var sources: List<CitationSource> by mutableStateOf(emptyList())
        private set

    fun open(sources: List<CitationSource>) {
        this.sources = sources
    }

    fun close() {
        sources = emptyList()
    }
}

/** Unprovided (markdown outside a message), nothing resolves, so no pill exists to open it. */
internal val LocalCitationSheet = staticCompositionLocalOf { CitationSheetState() }

/** The sources a tapped pill cites, as the web's hover card shows them, with each link openable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CitationSourcesSheet(state: CitationSheetState) {
    val sources = state.sources
    if (sources.isEmpty()) return
    AdaptiveModalBottomSheet(onDismissRequest = state::close) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(Res.string.citation_sources_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            sources.forEachIndexed { i, source ->
                if (i > 0) AdaptiveDivider(modifier = Modifier.padding(horizontal = 24.dp))
                when (source) {
                    is CitationSource.Web -> WebSourceRow(source)
                    is CitationSource.File -> FileSourceRow(source)
                }
            }
        }
    }
}

@Composable
private fun WebSourceRow(source: CitationSource.Web) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(Res.string.action_open)) {
                runCatching { uriHandler.openUri(source.link) }
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Favicon(model = faviconUrl(source.domain), size = 20.dp, modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = source.domain.ifBlank { source.link },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = source.title?.takeIf { it.isNotBlank() } ?: source.link,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            source.snippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileSourceRow(source: CitationSource.File) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = source.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                source.pages.takeIf { it.isNotEmpty() }
                    ?.let { stringResource(Res.string.file_search_pages, it.joinToString(", ")) },
                source.relevance?.let { stringResource(Res.string.file_search_relevance, (it * 100).roundToInt()) },
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            source.snippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.model.content.stripCitations
import com.garfiec.librechat.core.ui.components.AdaptiveDialog
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.jetbrains.compose.resources.stringResource

/**
 * Markdown content rendering with full CommonMark support. Uses a hybrid approach:
 * - 3-phase extraction preserves code blocks, LaTeX blocks, and inline LaTeX
 * - Custom CodeBlock.kt syntax highlighting and MermaidDiagram WebView for fenced code
 * - KaTeX (WebView) for LaTeX rendering (LatexBlock / LatexInline)
 * - multiplatform-markdown-renderer-m3 for rich text segments (headings, tables,
 *   lists, blockquotes, links, strikethrough, horizontal rules, etc.)
 * - Citation detection preserved for annotated source references
 *
 * @param searchQuery When non-null and non-blank, all occurrences of this string
 *   are highlighted in yellow within text segments. During active search, text
 *   segments use a plain [Text] composable instead of the full markdown renderer
 *   to ensure accurate highlighting.
 * @param searchFocusedOccurrence When >= 0, the occurrence at this index (counted
 *   across all text segments in this message, 0-based) is highlighted in orange
 *   instead of yellow to indicate the currently focused search result.
 * @param onFocusedOccurrencePosition Callback invoked with the [LayoutCoordinates]
 *   of the text segment containing the focused search occurrence, after it has been
 *   laid out. Used by the parent to fine-tune scroll position within a long message.
 */
@Composable
actual fun MarkdownContent(
    text: String,
    modifier: Modifier,
    fontSizeMultiplier: Float,
    useKatex: Boolean,
    searchQuery: String?,
    searchFocusedOccurrence: Int,
    onFocusedOccurrencePosition: ((LayoutCoordinates, Rect) -> Unit)?,
    immediate: Boolean,
    streaming: Boolean,
    trailingCursor: Boolean,
) {
    val isSearchActive = !searchQuery.isNullOrBlank()
    val segments = rememberCitedMarkdownSegments(text, streaming, withoutCitations = isSearchActive)
    val spokenText = remember(text) { stripCitations(text) }

    // Search draws text segments with its own components (SearchableMarkdown), which don't run the
    // cursor's annotator — so an active query forces the block-level cursor too.
    val inlineCursor = trailingCursor && !isSearchActive &&
        segments.lastOrNull()?.let { canHostInlineCursor(it) } == true

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = spokenText
            },
    ) {
        // Per-segment occurrence base offsets, advanced via countSegmentOccurrences so they stay
        // identical to the ViewModel-side numbering in SearchMatchEnumeration (mermaid blocks
        // contribute nothing). Computed once per (segments, query), not every recomposition.
        val segmentOffsets = remember(segments, searchQuery) {
            IntArray(segments.size).also { offsets ->
                if (!searchQuery.isNullOrBlank()) {
                    var acc = 0
                    segments.forEachIndexed { i, segment ->
                        offsets[i] = acc
                        acc += countSegmentOccurrences(segment, searchQuery)
                    }
                }
            }
        }

        segments.forEachIndexed { index, segment ->
            val focusedInSegment = if (isSearchActive) searchFocusedOccurrence - segmentOffsets[index] else -1

            when (segment) {
                is MarkdownSegment.CodeBlock -> {
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    if (segment.language?.lowercase() == "mermaid") {
                        MermaidDiagram(
                            code = segment.code,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        CodeBlock(
                            code = segment.code,
                            language = segment.language,
                            modifier = Modifier.padding(vertical = 4.dp),
                            searchQuery = if (isSearchActive) searchQuery else null,
                            searchFocusedOccurrence = focusedInSegment,
                            onFocusedMatchPosition = onFocusedOccurrencePosition,
                            streaming = streaming,
                        )
                    }
                    if (index < segments.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
                is MarkdownSegment.LatexBlock -> {
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    LatexBlock(
                        latex = segment.latex,
                        modifier = Modifier.padding(vertical = 4.dp),
                        useKatex = useKatex,
                    )
                    if (index < segments.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
                is MarkdownSegment.InlineLatexText -> {
                    Column {
                        // Rebase again per Text run within the segment.
                        var inlineOffset = 0
                        segment.segments.forEachIndexed { inlineIndex, inlineSegment ->
                            when (inlineSegment) {
                                is InlineSegment.Text -> {
                                    if (inlineSegment.text.isNotBlank()) {
                                        if (isSearchActive) {
                                            val runOccurrences =
                                                countDisplayedOccurrences(inlineSegment.text, searchQuery)
                                            MarkdownTextSegment(
                                                content = inlineSegment.text,
                                                fontSizeMultiplier = fontSizeMultiplier,
                                                immediate = immediate,
                                                search = rememberMarkdownSearch(
                                                    inlineSegment.text,
                                                    searchQuery,
                                                    focusedInSegment - inlineOffset,
                                                    onFocusedOccurrencePosition,
                                                ),
                                            )
                                            inlineOffset += runOccurrences
                                        } else {
                                            MarkdownTextSegment(
                                                content = inlineSegment.text,
                                                fontSizeMultiplier = fontSizeMultiplier,
                                                immediate = immediate,
                                                streaming = streaming,
                                                trailingCursor = inlineCursor &&
                                                    index == segments.lastIndex &&
                                                    inlineIndex == segment.segments.lastIndex,
                                            )
                                        }
                                    }
                                }
                                is InlineSegment.Latex -> {
                                    LatexInline(
                                        latex = inlineSegment.latex,
                                        useKatex = useKatex,
                                    )
                                }
                            }
                        }
                    }
                }
                is MarkdownSegment.Table -> {
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    MarkdownTableWithFullscreen(
                        headers = segment.headers,
                        alignments = segment.alignments,
                        rows = segment.rows,
                        fontSizeMultiplier = fontSizeMultiplier,
                        modifier = Modifier.padding(vertical = 4.dp),
                        searchQuery = if (isSearchActive) searchQuery else null,
                        searchFocusedOccurrence = focusedInSegment,
                        onFocusedMatchPosition = onFocusedOccurrencePosition,
                    )
                    if (index < segments.lastIndex) Spacer(modifier = Modifier.height(8.dp))
                }
                is MarkdownSegment.TextBlock -> {
                    if (isSearchActive) {
                        // Markdown stays rendered with find open; matches are marked in what it
                        // displays. Citation pills are left out while finding.
                        MarkdownTextSegment(
                            content = segment.text,
                            fontSizeMultiplier = fontSizeMultiplier,
                            immediate = immediate,
                            search = rememberMarkdownSearch(segment.text, searchQuery, focusedInSegment, onFocusedOccurrencePosition),
                        )
                    } else {
                        MarkdownTextSegment(
                            content = segment.text,
                            fontSizeMultiplier = fontSizeMultiplier,
                            immediate = immediate,
                            streaming = streaming,
                            trailingCursor = inlineCursor && index == segments.lastIndex,
                        )
                    }
                }
            }
        }

        if (trailingCursor && !inlineCursor) {
            StandaloneStreamingCursor(fontSizeMultiplier = fontSizeMultiplier)
        }
    }
}

/**
 * Renders a text segment using the multiplatform-markdown-renderer library for
 * full CommonMark support (headings, tables, lists, blockquotes, links, etc.).
 * Links are clickable via the ambient [LocalUriHandler].
 */
@Composable
private fun MarkdownTextSegment(
    content: String,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1.0f,
    immediate: Boolean = false,
    streaming: Boolean = false,
    trailingCursor: Boolean = false,
    search: MarkdownSearch? = null,
) {
    val components = remember(search) { search?.let(::searchMarkdownComponents) ?: ChatMarkdownComponents }
    val colors = markdownColor(
        text = MaterialTheme.colorScheme.onSurface,
        codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        inlineCodeBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
    )
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val bodyMedium = MaterialTheme.typography.bodyMedium
    val typography = markdownTypography(
        h1 = MaterialTheme.typography.headlineLarge.scaleFontSize(fontSizeMultiplier),
        h2 = MaterialTheme.typography.headlineMedium.scaleFontSize(fontSizeMultiplier),
        h3 = MaterialTheme.typography.headlineSmall.scaleFontSize(fontSizeMultiplier),
        h4 = MaterialTheme.typography.titleLarge.scaleFontSize(fontSizeMultiplier),
        h5 = MaterialTheme.typography.titleMedium.scaleFontSize(fontSizeMultiplier),
        h6 = MaterialTheme.typography.titleSmall.scaleFontSize(fontSizeMultiplier),
        text = bodyLarge.scaleFontSize(fontSizeMultiplier),
        paragraph = bodyLarge.scaleFontSize(fontSizeMultiplier),
        quote = bodyLarge.copy(fontStyle = FontStyle.Italic).scaleFontSize(fontSizeMultiplier),
        code = bodyMedium.copy(fontFamily = FontFamily.Monospace).scaleFontSize(fontSizeMultiplier),
        inlineCode = bodyLarge.copy(fontFamily = FontFamily.Monospace).scaleFontSize(fontSizeMultiplier),
        ordered = bodyLarge.scaleFontSize(fontSizeMultiplier),
        bullet = bodyLarge.scaleFontSize(fontSizeMultiplier),
        list = bodyLarge.scaleFontSize(fontSizeMultiplier),
    )

    // CachedMarkdown reads the ParsedMarkdownCache hoisted on ChatViewModel and
    // renders directly from the cached State.Success on re-entry, which skips the
    // library's async Loading→Success transition. That transition is what produces
    // the 0-px → final-height cascade on LazyColumn item recycle.
    //
    // key(fontSizeMultiplier) forces a fresh composition when the user changes
    // the font-size setting so the new typography takes effect.
    key(fontSizeMultiplier) {
        CachedMarkdown(
            content = content,
            colors = colors,
            typography = typography,
            modifier = modifier.fillMaxWidth(),
            immediate = immediate,
            streaming = streaming,
            trailingCursor = trailingCursor,
            components = components,
        )
    }
}

/**
 * A [MarkdownTable] under a fullscreen expand button, which sits above the table's end rather than
 * over its corner, where a panning table would scroll its headers beneath it. Tapping the button
 * opens a [FullscreenTableDialog].
 */
@Composable
private fun MarkdownTableWithFullscreen(
    headers: List<String>,
    alignments: List<TableCellAlignment>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1.0f,
    searchQuery: String? = null,
    searchFocusedOccurrence: Int = -1,
    onFocusedMatchPosition: ((LayoutCoordinates, Rect) -> Unit)? = null,
) {
    var showFullscreen by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        IconButton(
            onClick = { showFullscreen = true },
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 4.dp)
                .size(32.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = stringResource(Res.string.cd_expand_table),
                modifier = Modifier.size(18.dp),
            )
        }
        MarkdownTable(
            headers = headers,
            alignments = alignments,
            rows = rows,
            fontSizeMultiplier = fontSizeMultiplier,
            searchQuery = searchQuery,
            searchFocusedOccurrence = searchFocusedOccurrence,
            onFocusedMatchPosition = onFocusedMatchPosition,
        )
    }

    if (showFullscreen) {
        FullscreenTableDialog(
            headers = headers,
            alignments = alignments,
            rows = rows,
            fontSizeMultiplier = fontSizeMultiplier,
            onDismiss = { showFullscreen = false },
        )
    }
}

/**
 * Fullscreen dialog that renders a markdown table inside a scrollable container
 * (both horizontal and vertical) with an adaptive top bar for the title and close button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullscreenTableDialog(
    headers: List<String>,
    alignments: List<TableCellAlignment>,
    rows: List<List<String>>,
    fontSizeMultiplier: Float,
    onDismiss: () -> Unit,
) {
    AdaptiveDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(BarIcons.Close, stringResource(Res.string.cd_close), onDismiss),
                    title = BarTitle(stringResource(Res.string.dialog_table)),
                    actions = emptyList(),
                ),
                materialStyle = MaterialBarStyle(containerColor = MaterialTheme.colorScheme.surface),
            )

            val verticalScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .padding(16.dp),
            ) {
                SubwindowSelectionContainer {
                    MarkdownTable(
                        headers = headers,
                        alignments = alignments,
                        rows = rows,
                        pan = true,
                        fontSizeMultiplier = fontSizeMultiplier,
                    )
                }
            }
        }
    }
}

/**
 * Renders a markdown table with a header row and data rows. Header cells are bold
 * with a bottom divider. Subtle row dividers and column dividers provide structure.
 *
 * All cells in a column share one width, and the columns are fitted to the available
 * width by [fitTableColumns], wrapping their text. The table pans horizontally only when
 * it has too many columns to fit readably, or always when [pan] (the fullscreen view).
 */
@Composable
private fun MarkdownTable(
    headers: List<String>,
    alignments: List<TableCellAlignment>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    pan: Boolean = false,
    fontSizeMultiplier: Float = 1.0f,
    searchQuery: String? = null,
    searchFocusedOccurrence: Int = -1,
    onFocusedMatchPosition: ((LayoutCoordinates, Rect) -> Unit)? = null,
) {
    val bodyStyle = MaterialTheme.typography.bodyMedium.scaleFontSize(fontSizeMultiplier)
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)
    val cellPaddingH = 12.dp
    val cells = rememberRenderedTableCells(headers, rows)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columnWidths =
            rememberTableColumnWidths(cells, headerStyle, bodyStyle, cellPaddingH, constraints.maxWidth, pan)
        MarkdownTableGrid(
            headers = cells.headers,
            alignments = alignments,
            rows = cells.rows,
            columnWidths = columnWidths,
            bodyStyle = bodyStyle,
            headerStyle = headerStyle,
            cellPaddingH = cellPaddingH,
            searchQuery = searchQuery,
            searchFocusedOccurrence = searchFocusedOccurrence,
            onFocusedMatchPosition = onFocusedMatchPosition,
        )
    }
}

@Composable
private fun MarkdownTableGrid(
    headers: List<AnnotatedString>,
    alignments: List<TableCellAlignment>,
    rows: List<List<AnnotatedString>>,
    columnWidths: List<Dp>,
    bodyStyle: TextStyle,
    headerStyle: TextStyle,
    cellPaddingH: Dp,
    searchQuery: String?,
    searchFocusedOccurrence: Int,
    onFocusedMatchPosition: ((LayoutCoordinates, Rect) -> Unit)?,
) {
    val isSearchActive = !searchQuery.isNullOrBlank()
    val textColor = MaterialTheme.colorScheme.onSurface
    val headerBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val cellPaddingV = 8.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(MaterialTheme.shapes.small),
    ) {
        Column {
            // Per-cell occurrence base offsets, headers-first then rows row-major — the same order
            // as SearchMatchEnumeration's tableCellTexts. Computed once per (headers, rows, query).
            val (headerOffsets, rowOffsets) = remember(headers, rows, searchQuery) {
                val hOffsets = IntArray(headers.size)
                val rOffsets = rows.map { IntArray(it.size) }
                if (!searchQuery.isNullOrBlank()) {
                    var acc = 0
                    headers.forEachIndexed { i, header ->
                        hOffsets[i] = acc
                        acc += countOccurrences(header.text, searchQuery)
                    }
                    rows.forEachIndexed { r, row ->
                        row.forEachIndexed { c, cell ->
                            rOffsets[r][c] = acc
                            acc += countOccurrences(cell.text, searchQuery)
                        }
                    }
                }
                hOffsets to rOffsets
            }

            // Header row
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .background(headerBackground),
            ) {
                headers.forEachIndexed { colIndex, header ->
                    if (colIndex > 0) {
                        VerticalDivider(
                            color = dividerColor,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                    TableCell(
                        text = header,
                        style = headerStyle,
                        color = textColor,
                        alignment = alignments.getOrElse(colIndex) { TableCellAlignment.LEFT },
                        cellWidth = columnWidths[colIndex],
                        paddingH = cellPaddingH,
                        paddingV = cellPaddingV,
                        searchQuery = searchQuery,
                        searchFocusedOccurrence = if (isSearchActive) searchFocusedOccurrence - headerOffsets[colIndex] else -1,
                        onFocusedMatchPosition = onFocusedMatchPosition,
                    )
                }
            }

            AdaptiveDivider(color = dividerColor, thickness = 1.dp)

            // Data rows
            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    row.forEachIndexed { colIndex, cell ->
                        if (colIndex > 0) {
                            VerticalDivider(
                                color = dividerColor,
                                modifier = Modifier.fillMaxHeight(),
                            )
                        }
                        TableCell(
                            text = cell,
                            style = bodyStyle,
                            color = textColor,
                            alignment = alignments.getOrElse(colIndex) { TableCellAlignment.LEFT },
                            cellWidth = columnWidths.getOrElse(colIndex) { columnWidths.last() },
                            paddingH = cellPaddingH,
                            paddingV = cellPaddingV,
                            searchQuery = searchQuery,
                            searchFocusedOccurrence = if (isSearchActive) searchFocusedOccurrence - rowOffsets[rowIndex][colIndex] else -1,
                            onFocusedMatchPosition = onFocusedMatchPosition,
                        )
                    }
                }
                if (rowIndex < rows.lastIndex) {
                    AdaptiveDivider(color = dividerColor, thickness = Dp.Hairline)
                }
            }
        }
    }
}

/**
 * A single table cell with fixed [cellWidth] for uniform column alignment.
 * Text is aligned according to the column's [alignment] setting.
 */
@Composable
private fun TableCell(
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    alignment: TableCellAlignment,
    cellWidth: Dp,
    paddingH: Dp,
    paddingV: Dp,
    modifier: Modifier = Modifier,
    searchQuery: String? = null,
    searchFocusedOccurrence: Int = -1,
    onFocusedMatchPosition: ((LayoutCoordinates, Rect) -> Unit)? = null,
) {
    val textAlign = when (alignment) {
        TableCellAlignment.LEFT -> TextAlign.Start
        TableCellAlignment.CENTER -> TextAlign.Center
        TableCellAlignment.RIGHT -> TextAlign.End
    }

    val isDarkTheme = isSystemInDarkTheme()
    val display = remember(text, searchQuery, searchFocusedOccurrence, isDarkTheme) {
        if (searchQuery.isNullOrBlank()) {
            text
        } else {
            addSearchSpans(text, searchQuery, searchFocusedOccurrence, isDarkTheme)
        }
    }
    val focusedRange = remember(text, searchQuery, searchFocusedOccurrence) {
        if (searchQuery.isNullOrBlank()) null else findOccurrenceRange(text.text, searchQuery, searchFocusedOccurrence)
    }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(
        modifier = modifier
            .width(cellWidth)
            .padding(horizontal = paddingH, vertical = paddingV),
        contentAlignment = when (alignment) {
            TableCellAlignment.LEFT -> Alignment.CenterStart
            TableCellAlignment.CENTER -> Alignment.Center
            TableCellAlignment.RIGHT -> Alignment.CenterEnd
        },
    ) {
        Text(
            text = display,
            style = style,
            color = color,
            textAlign = textAlign,
            onTextLayout = { layoutResult = it },
            modifier = Modifier.reportFocusedMatchPosition(layoutResult, focusedRange, onFocusedMatchPosition),
        )
    }
}

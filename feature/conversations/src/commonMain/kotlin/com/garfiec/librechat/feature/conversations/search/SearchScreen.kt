package com.garfiec.librechat.feature.conversations.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.datetime.listLabel
import com.garfiec.librechat.core.data.search.MessageSearchHit
import com.garfiec.librechat.core.data.search.RegexProblem
import com.garfiec.librechat.core.data.search.RemoteSearchStatus
import com.garfiec.librechat.core.data.search.ScanProgress
import com.garfiec.librechat.core.data.search.SearchSource
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveScaffold
import com.garfiec.librechat.core.ui.components.AdaptiveTabRow
import com.garfiec.librechat.core.ui.components.AdaptiveTabRowKind
import com.garfiec.librechat.core.ui.components.ErrorBanner
import com.garfiec.librechat.core.ui.components.selectedTabIndex
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.datetime.LocalRelativeTimeReference
import com.garfiec.librechat.core.ui.datetime.resolve
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.back
import com.garfiec.librechat.feature.conversations.resources.cd_clear_search
import com.garfiec.librechat.feature.conversations.resources.cd_collapse_group
import com.garfiec.librechat.feature.conversations.resources.cd_expand_group
import com.garfiec.librechat.feature.conversations.resources.cd_from_server
import com.garfiec.librechat.feature.conversations.resources.search_footer_cached
import com.garfiec.librechat.feature.conversations.resources.search_footer_cached_server
import com.garfiec.librechat.feature.conversations.resources.search_footer_server
import com.garfiec.librechat.feature.conversations.resources.search_from_server
import com.garfiec.librechat.feature.conversations.resources.search_idle_hint
import com.garfiec.librechat.feature.conversations.resources.search_index_building
import com.garfiec.librechat.feature.conversations.resources.search_no_results
import com.garfiec.librechat.feature.conversations.resources.search_placeholder
import com.garfiec.librechat.feature.conversations.resources.search_regex_invalid
import com.garfiec.librechat.feature.conversations.resources.search_regex_too_complex
import com.garfiec.librechat.feature.conversations.resources.search_scan_more
import com.garfiec.librechat.feature.conversations.resources.search_scan_partial
import com.garfiec.librechat.feature.conversations.resources.search_scope_all
import com.garfiec.librechat.feature.conversations.resources.search_scope_chats
import com.garfiec.librechat.feature.conversations.resources.search_scope_messages
import com.garfiec.librechat.feature.conversations.resources.search_searching_server
import com.garfiec.librechat.feature.conversations.resources.search_server_body_match
import com.garfiec.librechat.feature.conversations.resources.search_server_capped
import com.garfiec.librechat.feature.conversations.resources.search_server_model_caveat
import com.garfiec.librechat.feature.conversations.resources.search_server_unavailable
import com.garfiec.librechat.feature.conversations.resources.search_short_prefix
import com.garfiec.librechat.feature.conversations.resources.search_showing_first
import com.garfiec.librechat.feature.conversations.resources.search_time_server
import com.garfiec.librechat.feature.conversations.resources.search_title_match
import com.garfiec.librechat.feature.conversations.resources.search_untitled_conversation
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

private val RowShape = RoundedCornerShape(8.dp)

/**
 * Full message search: every hit grouped by conversation, title matches, and on a wide layout a
 * preview of the selected hit. With a hardware keyboard, ↑/↓ move the selection and Enter opens it.
 */
@Composable
fun SearchScreen(
    initialQuery: String,
    onNavigateBack: () -> Unit,
    onOpenMessage: (conversationId: String, messageId: String, query: String, inReasoning: Boolean) -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = koinViewModel { parametersOf(initialQuery) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Open a hit only while the account it was found under is still active. A pattern's hit is found
    // in the chat by the text it matched, since the chat's find can't run a pattern.
    val open: (MessageSearchHit) -> Unit = { hit ->
        viewModel.openable(hit)?.let {
            // A regex hit carries the text its pattern matched, quoted so find takes it as one phrase.
            onOpenMessage(it.conversationId, it.messageId, it.findText?.let { text -> "\"$text\"" } ?: state.query.trim(), it.inReasoning)
        }
    }
    val openConversation: (String) -> Unit = { id -> viewModel.openableConversation(id)?.let(onOpenConversation) }
    val actions = remember(viewModel) {
        AdvancedActions(
            onOptionsChange = viewModel::onOptionsChange,
            onDateRangeChange = viewModel::onDateRangeChange,
            onGroupingChange = viewModel::onGroupingChange,
            onOpenFilters = viewModel::openFilters,
            onResetFilters = viewModel::resetFilters,
        )
    }
    // The pager owns the scope: a swipe or a tab tap moves it, and the view model follows once it
    // settles, so the preview pane and the arrow keys stay on the page in view.
    val scopes = SearchScope.entries
    val pagerState = rememberPagerState(initialPage = scopes.indexOf(state.scope)) { scopes.size }
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.onScopeChange(scopes[it]) }
    }
    val onSelectScope: (Int) -> Unit = { coroutineScope.launch { pagerState.animateScrollToPage(it) } }
    // One per page, above the one- and two-pane branches, so the scroll survives a fold or unfold
    // switching them.
    val listStates = List(scopes.size) { rememberLazyListState() }
    BoxWithConstraints(modifier = modifier) {
        // Wide: the text toggles sit beside the field and the filters beside the tabs. Narrow: one
        // row of toggles, Filters and chips under the tabs, with the rest in a sheet. It goes wide at the
        // two-pane width, so the tabs, sized like the list pane, line up over it; a window too short
        // for two panes keeps the wide header, which leaves the list more height.
        val wide = maxWidth >= TwoPaneMinWidth
        val listWidth = listPaneWidth(maxWidth)
        val advanced = state.advancedEnabled
        AdaptiveScaffold(
            // The footer and the end of the list stay above the keyboard while the query is edited.
            modifier = Modifier.imePadding(),
            topBar = {
                // The tabs sit in the bar slot, as in Settings: the scaffold draws it over the body, so the
                // glass tab's bubble, which swells past the strip, stays above the field instead of under it.
                Column {
                    SearchTopRow(query = state.query, onQueryChange = viewModel::onQueryChange, onBack = onNavigateBack) {
                        if (advanced && wide) {
                            Spacer(modifier = Modifier.width(8.dp))
                            TextToggleGroup(state.options, viewModel::onOptionsChange)
                        }
                    }
                    if (advanced && wide) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ScopeTabs(
                                state = state,
                                selectedIndex = pagerState.selectedTabIndex(),
                                onSelect = onSelectScope,
                                modifier = Modifier.width(listWidth),
                            )
                            WideFilterRow(state, actions, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        }
                    } else {
                        ScopeTabs(
                            state = state,
                            selectedIndex = pagerState.selectedTabIndex(),
                            onSelect = onSelectScope,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            bottomBar = { SearchFooter(state) },
        ) { padding ->
            if (advanced && state.filtersOpen) {
                SearchFiltersSheet(state = state, actions = actions, onDismiss = viewModel::closeFilters)
            }
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (advanced && !wide) {
                    AdvancedSearchRow(state = state, actions = actions, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                SearchPanes(
                    list = { twoPane, listModifier ->
                        HorizontalPager(state = pagerState, modifier = listModifier, beyondViewportPageCount = 1) { page ->
                            SearchResultsList(
                                state = state.copy(scope = scopes[page]),
                                twoPane = twoPane,
                                // A phone opens what's tapped, but still selects it: unfolding to two panes
                                // previews the chat the user last went into.
                                onHitClick = { hit ->
                                    viewModel.select(hit.messageId)
                                    if (!twoPane) open(hit)
                                },
                                onOpenSelection = {
                                    state.preview?.hit?.let(open) ?: state.selectedConversationId?.let(openConversation)
                                },
                                onMoveSelection = viewModel::moveSelection,
                                // Two-pane previews a conversation like a hit; a phone has no pane, so it opens.
                                onConversationClick = { id ->
                                    viewModel.selectConversation(id)
                                    if (!twoPane) openConversation(id)
                                },
                                onToggleGroup = viewModel::toggleCollapsed,
                                onScanMore = viewModel::scanMore,
                                listState = listStates[page],
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
                    preview = { previewModifier ->
                        LaunchedEffect(state.groups, state.scope, state.grouping) { viewModel.ensureSelection() }
                        SearchPreviewPane(
                            preview = state.preview,
                            chatPreview = state.chatPreview,
                            agentNames = state.agentNames,
                            onStep = viewModel::stepInConversation,
                            onOpen = open,
                            onOpenConversation = openConversation,
                            modifier = previewModifier,
                        )
                    },
                )
            }
        }
    }
}

// The preview gets at least a phone's width, the list grows into what's left up to ListPaneWidth.
private val ListPaneMinWidth = 320.dp
private val ListPaneWidth = 420.dp
private val PreviewMinWidth = 360.dp
private val TwoPaneMinWidth = ListPaneMinWidth + PreviewMinWidth

// Enough height for the preview's header and footer with a few lines of thread between them.
private val TwoPaneMinHeight = 320.dp

private fun listPaneWidth(width: Dp): Dp = (width - PreviewMinWidth).coerceIn(ListPaneMinWidth, ListPaneWidth)

/** The results [list] with the [preview] beside it when there's room, or the list alone. */
@Composable
internal fun SearchPanes(
    list: @Composable (twoPane: Boolean, Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TwoPaneMinWidth && maxHeight >= TwoPaneMinHeight
        val listWidth = listPaneWidth(maxWidth)
        Row(modifier = Modifier.fillMaxSize()) {
            list(twoPane, if (twoPane) Modifier.width(listWidth).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight())
            if (twoPane) {
                AdaptiveDivider(modifier = Modifier.width(1.dp).fillMaxHeight())
                preview(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/**
 * The screen's header: the query in a filled pill. On iOS (see [searchShowsBackButton]) it sits
 * under the platform bar, which carries only the back button so iOS draws its own (Liquid Glass on
 * iOS 26). The bar has no title: the field says what the screen is.
 */
@Composable
private fun SearchTopRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    // A query brought in from the drawer was already committed there, so the field waits for a tap;
    // only an empty Search screen starts typing.
    // Saved, so a field that had focus gets it back when a fold or unfold recreates the screen. Only
    // a change while resumed counts: the teardown before that recreation takes focus away first.
    var focused by rememberSaveable { mutableStateOf(query.isEmpty()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { if (focused) runCatching { focus.requestFocus() } }
    // The field keeps its own value so a query it opens with puts the cursor at the end, not the
    // start. Only a reset from outside (an account switch empties the query) is copied back in:
    // any other difference is the screen's state still catching up with what was typed. Saveable,
    // so the cursor survives the activity being recreated on a fold or unfold.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, TextRange(query.length)))
    }
    LaunchedEffect(query) {
        if (query.isEmpty() && field.text.isNotEmpty()) field = TextFieldValue()
    }
    val change: (TextFieldValue) -> Unit = { value ->
        val textChanged = value.text != field.text
        field = value
        if (textChanged) onQueryChange(value.text)
    }
    val fieldRow: @Composable (Modifier) -> Unit = { rowModifier ->
        Row(
            modifier = rowModifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                field = field,
                onChange = change,
                focus = focus,
                onFocusChange = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) focused = it },
            )
            trailing()
        }
    }
    if (searchShowsBackButton) {
        AdaptiveTopBar(
            spec = AdaptiveTopBarSpec(
                navigation = BarNavigation(BarIcons.Back, stringResource(Res.string.back), onBack),
                title = BarTitle(text = null),
            ),
            belowBar = { fieldRow(Modifier) },
        )
    } else {
        fieldRow(Modifier.statusBarsPadding())
    }
}

@Composable
private fun RowScope.SearchField(
    field: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    focus: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
) {
    AdaptiveOutlinedTextField(
        value = field,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (field.text.isNotEmpty()) {
                // Focus goes back to the field: with a hardware keyboard the tap would otherwise
                // leave it on this button, and the next key wouldn't type.
                IconButton(
                    onClick = {
                        onChange(TextFieldValue())
                        runCatching { focus.requestFocus() }
                    },
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.cd_clear_search),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        placeholder = {
            Text(stringResource(Res.string.search_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = CircleShape,
        colors = filledPillColors(),
        modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { onFocusChange(it.isFocused) },
    )
}

/** A filled field with no outline until it's focused. */
@Composable
internal fun filledPillColors(): TextFieldColors {
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        unfocusedBorderColor = Color.Transparent,
    )
}

/** All / Messages / Chats with their counts, shown from the first keystroke so the layout holds. */
@Composable
private fun ScopeTabs(state: SearchUiState, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    AdaptiveTabRow(
        titles = listOf(
            stringResource(Res.string.search_scope_all, state.messageTabCount + state.chatCount),
            stringResource(Res.string.search_scope_messages, state.messageTabCount),
            stringResource(Res.string.search_scope_chats, state.chatCount),
        ),
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier,
        kind = AdaptiveTabRowKind.SECONDARY,
        // Drawn in the bar slot, so the glass strip may sample the backdrop.
        backdrop = LocalGlassBackdrop.current,
        // Sized for three-digit counts, so typing doesn't flip the row between fixed and scrolling.
        fitTitles = listOf(
            stringResource(Res.string.search_scope_all, FIT_COUNT),
            stringResource(Res.string.search_scope_messages, FIT_COUNT),
            stringResource(Res.string.search_scope_chats, FIT_COUNT),
        ),
    )
}

private const val FIT_COUNT = 999

@Composable
internal fun SearchResultsList(
    state: SearchUiState,
    twoPane: Boolean,
    onHitClick: (MessageSearchHit) -> Unit,
    onOpenSelection: () -> Unit,
    onMoveSelection: (Int) -> Unit,
    onConversationClick: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
    onScanMore: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    // Arrow keys and Enter work once the list has focus (tab into it, or after a click).
    val keys = Modifier.onPreviewKeyEvent { event ->
        if (!twoPane || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> { onMoveSelection(1); true }
            Key.DirectionUp -> { onMoveSelection(-1); true }
            Key.Enter, Key.NumPadEnter -> { onOpenSelection(); true }
            else -> false
        }
    }
    LazyColumn(state = listState, modifier = modifier.then(keys).focusable()) {
        statusRows(state, onScanMore)
        if (!state.searched && state.regexProblem == null) {
            item(key = "idle") { CenteredHint(stringResource(Res.string.search_idle_hint)) }
        }
        if (state.searched && state.visibleGroups.isEmpty() &&
            state.remoteStatus != RemoteSearchStatus.Searching
        ) {
            item(key = "empty") { CenteredHint(stringResource(Res.string.search_no_results)) }
        }
        // The selection is what the preview pane shows; with no pane it's kept, but not marked.
        if (state.grouping == SearchGrouping.None && state.showsHits) {
            flatHits(state, twoPane, onHitClick, onConversationClick)
        } else {
            messageGroups(state, twoPane, onHitClick, onConversationClick, onToggleGroup)
        }
        // After the local results it's waiting to add to, not above them.
        if (state.remoteStatus == RemoteSearchStatus.Searching) {
            item(key = "status_server") { StatusLine(stringResource(Res.string.search_searching_server), progress = true) }
        }
    }
}

private fun LazyListScope.statusRows(state: SearchUiState, onScanMore: () -> Unit) {
    state.regexProblem?.let { problem ->
        item(key = "status_regex") {
            ErrorBanner(
                message = stringResource(
                    if (problem == RegexProblem.TooComplex) Res.string.search_regex_too_complex else Res.string.search_regex_invalid,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
    if (state.serverModelCaveat) {
        item(key = "status_server_model") { StatusLine(stringResource(Res.string.search_server_model_caveat)) }
    }
    state.cappedAt?.let { shown ->
        item(key = "status_capped") { StatusLine(stringResource(Res.string.search_showing_first, shown)) }
    }
    if (state.remoteCapped) {
        item(key = "status_server_capped") { StatusLine(stringResource(Res.string.search_server_capped)) }
    }
    state.shortPrefix?.let { word ->
        item(key = "status_short_prefix") { StatusLine(stringResource(Res.string.search_short_prefix, word)) }
    }
    if (state.indexIncomplete) {
        item(key = "status_index") { StatusLine(stringResource(Res.string.search_index_building), progress = true) }
    }
    if (state.serverUnavailable) {
        item(key = "status_server_unavailable") { StatusLine(stringResource(Res.string.search_server_unavailable)) }
    }
    val scan = state.scan
    if (scan?.resumeFrom != null) {
        item(key = "status_scan") { ScanMoreRow(state.messageCount, scan, onScanMore) }
    }
}

@Composable
internal fun ScanMoreRow(messageCount: Int, scan: ScanProgress, onScanMore: () -> Unit) {
    // Scan more drops under the note rather than squeeze it narrower than MinScanNoteWidth.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.search_scan_partial, messageCount, scan.scanned, scan.total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).widthIn(min = MinScanNoteWidth),
        )
        Text(
            text = stringResource(Res.string.search_scan_more),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RowShape).clickable(role = Role.Button, onClick = onScanMore).padding(8.dp),
        )
    }
}

private val MinScanNoteWidth = 160.dp

@Composable
private fun CenteredHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
    )
}

/** Why results are cached-only, and where they came from, pinned under the list. */
@Composable
private fun SearchFooter(state: SearchUiState) {
    if (!state.searched) return
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val note = state.note
        note?.let { SearchNoteCard(it, modifier = Modifier.padding(bottom = 6.dp)) }
        val source = when {
            state.options.source == SearchSource.Server -> Res.string.search_footer_server
            state.remoteStatus == RemoteSearchStatus.Done -> Res.string.search_footer_cached_server
            else -> Res.string.search_footer_cached
        }
        // A note already says the results are cached only, and why; the line would repeat it.
        if (note == null || source != Res.string.search_footer_cached) {
            Text(
                text = stringResource(source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(text: String, progress: Boolean = false) {
    Row(
        // The extra room on top keeps a line clear of the tabs above it.
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (progress) {
            AdaptiveCircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.messageGroups(
    state: SearchUiState,
    showSelection: Boolean,
    onHitClick: (MessageSearchHit) -> Unit,
    onConversationClick: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
) {
    var serverDividerShown = false
    state.visibleGroups.forEach { group ->
        if (group.fromServer && !serverDividerShown) {
            serverDividerShown = true
            item(key = "server_divider") { ServerDivider() }
        }
        val collapsed = group.conversationId in state.collapsed
        item(key = "group_${group.conversationId}", contentType = "group_header") {
            SearchGroupHeader(
                group = group,
                selected = showSelection && group.conversationId == state.selectedConversationId,
                onClick = { onConversationClick(group.conversationId) },
                collapsed = collapsed,
                onToggleGroup = if (state.showsHits && group.hits.isNotEmpty()) {
                    { onToggleGroup(group.conversationId) }
                } else {
                    null
                },
            )
        }
        if (state.showsHits && !collapsed) {
            items(group.hits, key = { "msg_${it.messageId}" }, contentType = { "message_hit" }) { hit ->
                SearchHitRow(
                    hit,
                    selected = showSelection && hit.messageId == state.selectedMessageId,
                    onClick = { onHitClick(hit) },
                    modifier = Modifier.padding(start = HitIndent),
                )
            }
        }
    }
}

/** Nested hits line up under their conversation's title, past the chevron. */
private val HitIndent = 32.dp

/**
 * A conversation in the results: its title (marked when the title matched), and under it a line
 * with why it's listed when no message hit follows, where it came from, when, and how many
 * messages matched.
 */
@Composable
private fun SearchGroupHeader(
    group: SearchResultGroup,
    selected: Boolean,
    onClick: () -> Unit,
    collapsed: Boolean = false,
    onToggleGroup: (() -> Unit)? = null,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 1.dp)
            // Clipped so the ripple keeps the row's rounded corners.
            .clip(RowShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (onToggleGroup != null) 0.dp else 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggleGroup != null) {
            IconButton(onClick = onToggleGroup) {
                Icon(
                    imageVector = if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(if (collapsed) Res.string.cd_expand_group else Res.string.cd_collapse_group),
                    tint = variant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            HighlightedTitle(
                title = group.title ?: stringResource(Res.string.search_untitled_conversation),
                highlights = group.titleHighlights,
            )
            // Why it's listed, where it came from, when and how many: under the title, which beside
            // them would be left too little room at a large font.
            val reason = when {
                group.hits.isNotEmpty() -> null
                group.serverBodyMatch -> stringResource(Res.string.search_server_body_match)
                else -> stringResource(Res.string.search_title_match)
            }
            // The server's hits carry no date, and its conversation list doesn't always hold the chat:
            // such a group says where it came from in the date's place, as a server hit row does.
            val date = groupDate(group.newestAt) ?: if (group.fromServer) stringResource(Res.string.search_time_server) else null
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (reason != null) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = variant,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (group.fromServer || date != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (group.fromServer) {
                            Icon(
                                imageVector = Icons.Outlined.Cloud,
                                contentDescription = stringResource(Res.string.cd_from_server),
                                tint = variant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        if (date != null) {
                            Text(text = date, style = MaterialTheme.typography.labelSmall, color = variant, maxLines = 1)
                        }
                    }
                }
                if (group.hits.isNotEmpty()) {
                    Text(
                        text = group.hits.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, CountShape)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private val CountShape = RoundedCornerShape(50)

@Composable
private fun groupDate(at: Long?): String? {
    val format = LocalDateTimeFormat.current
    val reference = LocalRelativeTimeReference.current
    val label = remember(at, format, reference) {
        at?.let { Instant.fromEpochMilliseconds(it).listLabel(format, reference) }
    }
    return label?.resolve()?.takeIf { it.isNotEmpty() }
}

@Composable
private fun ServerDivider() {
    val color = MaterialTheme.colorScheme.tertiary
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = stringResource(Res.string.search_from_server), style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(modifier = Modifier.width(8.dp))
        AdaptiveDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * One list in the sort's order; each row names its conversation, since there are no headers.
 * Conversations that matched only by title come first, as headers with no hits.
 */
private fun LazyListScope.flatHits(
    state: SearchUiState,
    showSelection: Boolean,
    onHitClick: (MessageSearchHit) -> Unit,
    onConversationClick: (String) -> Unit,
) {
    val visible = state.visibleGroups
    items(visible.filter { it.hits.isEmpty() }, key = { "group_${it.conversationId}" }, contentType = { "group_header" }) { group ->
        SearchGroupHeader(
            group = group,
            selected = showSelection && group.conversationId == state.selectedConversationId,
            onClick = { onConversationClick(group.conversationId) },
        )
    }
    val listed = if (state.scope == SearchScope.All) {
        state.flatHits
    } else {
        val ids = visible.mapTo(HashSet()) { it.conversationId }
        state.flatHits.filter { it.conversationId in ids }
    }
    items(listed, key = { "flat_${it.messageId}" }, contentType = { "message_hit" }) { hit ->
        SearchHitRow(
            hit,
            selected = showSelection && hit.messageId == state.selectedMessageId,
            onClick = { onHitClick(hit) },
            showConversation = true,
        )
    }
}

@Composable
private fun SearchHitRow(
    hit: MessageSearchHit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showConversation: Boolean = false,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 1.dp)
            // Clipped so the ripple keeps the row's rounded corners.
            .clip(RowShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        HitMeta(
            hit = hit,
            title = if (showConversation) hit.conversationTitle ?: stringResource(Res.string.search_untitled_conversation) else null,
        )
        SnippetText(
            snippet = hit.snippet,
            maxLines = 3,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

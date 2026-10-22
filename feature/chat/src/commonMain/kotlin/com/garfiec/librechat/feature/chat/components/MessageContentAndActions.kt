package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.FeedbackRating
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.ui.components.AdaptiveDropdownMenu
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.util.ContentGroup
import com.garfiec.librechat.feature.chat.util.IndexedContentPart
import com.garfiec.librechat.feature.chat.util.activityLabelText
import com.garfiec.librechat.feature.chat.util.findLateBatchLabelsConsumedByPhase
import com.garfiec.librechat.feature.chat.util.groupContentParts
import com.garfiec.librechat.feature.chat.util.groupedToolCallIds
import com.garfiec.librechat.feature.chat.util.hasParallelGroupIds
import com.garfiec.librechat.feature.chat.util.steerText
import com.garfiec.librechat.feature.chat.util.withPreviews
import org.jetbrains.compose.resources.stringResource

/** Material 3's disabled content alpha. */
private const val DISABLED_ALPHA = 0.38f

/**
 * False while a reply is streaming, provided by `MessageList`.
 *
 * `ChatViewModel.submitFeedback` refuses mid-stream because its Room write would un-truncate the
 * streaming anchor — but that guard is the last step of a flow that starts several taps earlier.
 * Left live, the thumbs open a sheet, take a reason and up to 1024 characters of comment, and then
 * drop all of it at the sink with nothing shown. The affordance has to know, so the flow is never
 * entered. Disabled rather than hidden: removing the buttons reflows the action row mid-stream.
 */
internal val LocalFeedbackEnabled = compositionLocalOf { true }

/**
 * False while nothing may start a run (Compare setup). [MessageList] hides the entry points that
 * would; an inline edit already open keeps Save and Cancel but disables Save & Submit, which the
 * ViewModel would refuse with nothing shown.
 */
internal val LocalResubmitEnabled = compositionLocalOf { true }

/**
 * Shared action buttons row for message bubbles (copy, edit, regenerate, feedback, read aloud, fork).
 * Used by both Android and iOS MessageBubble implementations.
 *
 * Takes the width it is given: the actions that don't fit move, from the end, into a "More" menu, so
 * a narrow thread keeps every action reachable instead of pushing the last ones off the row.
 */
@Composable
internal fun ActionButtons(
    isUser: Boolean,
    onFeedback: ((MinimalFeedback?) -> Unit)?,
    currentFeedback: FeedbackRating?,
    onPickFeedbackTag: (FeedbackRating) -> Unit,
    onCopy: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onReadAloud: (() -> Unit)?,
    isReading: Boolean,
    onFork: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val idleTint = MaterialTheme.colorScheme.onSurfaceVariant
    val actions = buildList {
        // Feedback buttons (AI messages only). Tapping the filled thumb clears; either unfilled
        // thumb opens the tag picker, because the route rejects a rating with no tag.
        if (!isUser && onFeedback != null) {
            val isUp = currentFeedback == FeedbackRating.THUMBS_UP
            val isDown = currentFeedback == FeedbackRating.THUMBS_DOWN
            val feedbackEnabled = LocalFeedbackEnabled.current
            // The tint is set explicitly, so IconButton's own disabled content colour never
            // applies — without this the button greys out its ripple but not its icon.
            val disabledTint = idleTint.copy(alpha = DISABLED_ALPHA)
            // "Thumbs up, disabled" says nothing about why; a disabled control with no reason is
            // the accessibility equivalent of a silent no-op.
            val unavailable = stringResource(Res.string.cd_feedback_unavailable)
            val thumbsUp = stringResource(Res.string.cd_thumbs_up)
            val thumbsDown = stringResource(Res.string.cd_thumbs_down)
            add(
                MessageAction(
                    icon = if (isUp) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    label = if (feedbackEnabled) thumbsUp else "$thumbsUp, $unavailable",
                    tint = when {
                        !feedbackEnabled -> disabledTint
                        isUp -> MaterialTheme.colorScheme.primary
                        else -> idleTint
                    },
                    enabled = feedbackEnabled,
                    onClick = { if (isUp) onFeedback(null) else onPickFeedbackTag(FeedbackRating.THUMBS_UP) },
                    menuLabel = thumbsUp,
                ),
            )
            add(
                MessageAction(
                    icon = if (isDown) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    label = if (feedbackEnabled) thumbsDown else "$thumbsDown, $unavailable",
                    tint = when {
                        !feedbackEnabled -> disabledTint
                        isDown -> MaterialTheme.colorScheme.error
                        else -> idleTint
                    },
                    enabled = feedbackEnabled,
                    onClick = { if (isDown) onFeedback(null) else onPickFeedbackTag(FeedbackRating.THUMBS_DOWN) },
                    menuLabel = thumbsDown,
                ),
            )
        }
        if (onCopy != null) {
            add(MessageAction(Icons.Default.ContentCopy, stringResource(Res.string.cd_copy_message), idleTint, onCopy))
        }
        if (onEdit != null) {
            add(MessageAction(Icons.Default.Edit, stringResource(Res.string.cd_edit_message), idleTint, onEdit))
        }
        if (!isUser && onRegenerate != null) {
            add(MessageAction(Icons.Default.Refresh, stringResource(Res.string.cd_regenerate_response), idleTint, onRegenerate))
        }
        if (onReadAloud != null) {
            add(
                MessageAction(
                    icon = if (isReading) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    label = stringResource(if (isReading) Res.string.cd_stop_reading else Res.string.cd_read_aloud),
                    tint = if (isReading) MaterialTheme.colorScheme.primary else idleTint,
                    onClick = onReadAloud,
                ),
            )
        }
        if (onFork != null) {
            add(MessageAction(Icons.AutoMirrored.Filled.CallSplit, stringResource(Res.string.cd_fork_conversation), idleTint, onFork))
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val slots = if (constraints.hasBoundedWidth) (maxWidth / ACTION_BUTTON_SIZE).toInt() else actions.size
        val overflows = actions.size > slots
        // The More button takes a slot of its own.
        val shown = if (overflows) actions.take((slots - 1).coerceAtLeast(0)) else actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            shown.forEach { action ->
                IconButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.size(ACTION_BUTTON_SIZE),
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        modifier = Modifier.size(18.dp),
                        tint = action.tint,
                    )
                }
            }
            if (overflows) ActionOverflowMenu(actions.drop(shown.size))
        }
    }
}

/**
 * One entry of a message's action row: an icon button, or an item of its "More" menu. [label] is
 * what TalkBack reads; [menuLabel] is the menu item's visible text, without any spoken-only reason.
 */
@Immutable
private class MessageAction(
    val icon: ImageVector,
    val label: String,
    val tint: Color,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val menuLabel: String = label,
)

@Composable
private fun ActionOverflowMenu(actions: List<MessageAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(ACTION_BUTTON_SIZE)) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = stringResource(Res.string.cd_more_options),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AdaptiveDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.menuLabel) },
                    leadingIcon = { Icon(action.icon, contentDescription = null, tint = action.tint) },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    // TalkBack still hears why a disabled item is unavailable.
                    modifier = Modifier.semantics { contentDescription = action.label },
                )
            }
        }
    }
}

private val ACTION_BUTTON_SIZE = 40.dp

/**
 * Shared message content rendering and action buttons, used by both layout styles on both platforms.
 * Renders: edit mode OR (files + content parts + markdown fallback) + action row with sibling nav.
 */
@Suppress("LongParameterList")
@Composable
internal fun MessageContentAndActions(
    message: Message,
    isUser: Boolean,
    isEditing: Boolean,
    editText: String,
    onEditTextChange: ((String) -> Unit)?,
    onEditSaveAndSubmit: (() -> Unit)?,
    onEditSaveOnly: (() -> Unit)?,
    onEditCancel: (() -> Unit)?,
    baseUrl: String,
    fontSizeMultiplier: Float,
    useKatex: Boolean,
    showImageDescriptions: Boolean,
    searchQuery: String?,
    isSearchMatch: Boolean,
    isCurrentSearchMatch: Boolean,
    searchFocusedOccurrence: Int,
    onFocusedOccurrencePosition: ((LayoutCoordinates, Rect) -> Unit)?,
    showActions: Boolean,
    siblingIndex: Int,
    siblingCount: Int,
    onSiblingNavigation: ((Int) -> Unit)?,
    onEdit: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onCopy: (() -> Unit)?,
    onFeedback: ((MinimalFeedback?) -> Unit)?,
    onContinue: (() -> Unit)?,
    onReadAloud: (() -> Unit)?,
    onFork: (() -> Unit)?,
    isReading: Boolean,
    currentFeedback: FeedbackRating?,
    // Identity for a mid-run steer, which renders as the user's own turn inside the response.
    userName: String?,
    userAvatarUrl: String?,
    // Only the two-sided layout, where a user turn sits at the end. The thread layout lines every
    // turn up at the start, so pushing images to the end there would split them from their text.
    alignImagesEnd: Boolean,
) {
    // The picker is hosted here — one site for both layouts on both platforms — rather than in
    // ActionButtons, whose action row auto-hides on a timer and would take an open sheet with it.
    // Saveable, like the tag and comment inside the sheet: a plain `remember` here destroys the
    // sheet on rotation, which takes the child's saved draft with it — the host has to survive
    // for the child's saved state to be reachable. Enum names, not entries: the default saver
    // only handles primitives.
    var pendingFeedbackRating by rememberSaveable(
        message.messageId,
        key = "feedback-rating:${message.messageId}",
    ) { mutableStateOf<String?>(null) }
    val ratingBeingTagged = pendingFeedbackRating?.let { name ->
        FeedbackRating.entries.firstOrNull { it.name == name }
    }
    if (ratingBeingTagged != null && onFeedback != null) {
        FeedbackTagSheet(
            rating = ratingBeingTagged,
            onSubmit = { tag, comment ->
                pendingFeedbackRating = null
                onFeedback(MinimalFeedback(rating = ratingBeingTagged, tag = tag, text = comment))
            },
            onDismiss = { pendingFeedbackRating = null },
        )
    }

    if (isEditing && onEditTextChange != null && onEditSaveAndSubmit != null && onEditSaveOnly != null && onEditCancel != null) {
        // Show the attached files above the edit field so it's clear they're
        // retained on save & submit (the resubmit carries message.files).
        val messageFiles = message.files
        if (!messageFiles.isNullOrEmpty()) {
            MessageFiles(
                files = messageFiles,
                baseUrl = baseUrl,
                alignImagesEnd = alignImagesEnd,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        InlineEditInput(
            text = editText,
            onTextChange = onEditTextChange,
            onSaveAndSubmit = onEditSaveAndSubmit,
            onSaveOnly = onEditSaveOnly,
            onCancel = onEditCancel,
        )
    } else {
        // Single Column root so this branch emits from one source (and the quote chips,
        // files, content, and office previews stack the same as before).
        //
        // Selection scope is per-message and content-only: every BasicText-backed descendant
        // (prose, code, table cells, think bodies) shares one registrar, while chrome opts out
        // via DisableSelection so "Select all" copies message text, not UI labels. The action
        // row below and the editing branch above stay outside — a text field must never sit
        // inside a SelectionContainer.
        // The persisted attachments with any preview the client has since resolved applied, so a
        // file that was still rendering when the reply was saved shows its result. Every read
        // below goes through this, never `message.attachments`.
        val previewOverrides = LocalPreviewOverrides.current
        val effectiveAttachments = remember(message.attachments, previewOverrides) {
            message.attachments?.withPreviews(previewOverrides)
        }
        // Workspace changes render once, in their own section, so every other reader takes the
        // inline rest. Markdown images still resolve against all of them, as on web.
        val workspace = remember(effectiveAttachments) { partitionWorkspaceChanges(effectiveAttachments) }
        val inlineAttachments = workspace.inline
        ProvideMarkdownMedia(effectiveAttachments, baseUrl) {
            SelectionContainer {
                Column {
                    // Verbatim excerpts the user referenced on this turn (v0.8.7), above the user's
                    // text. Created on web and via Android's selection-toolbar "Add to chat".
                    // Selectable: a quote is conversation text the user pulled forward, not chrome.
                    val quotes = message.quotes
                    if (isUser && !quotes.isNullOrEmpty()) {
                        MessageQuotes(
                            quotes = quotes,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }

                    // Render attached files above message text (matches web app behavior)
                    val messageFiles = message.files
                    if (!messageFiles.isNullOrEmpty()) {
                        DisableSelection {
                            MessageFiles(
                                files = messageFiles,
                                baseUrl = baseUrl,
                                alignImagesEnd = alignImagesEnd,
                                // Aligned across the whole bubble, not just the files block, or a line of
                                // text wider than the image holds it at the start.
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .then(if (alignImagesEnd) Modifier.align(Alignment.End) else Modifier),
                            )
                        }
                    }

                    val contentParts = message.content
                    if (!contentParts.isNullOrEmpty()) {
                        // Occurrence indices are message-wide (SearchMatchEnumeration): rebase them
                        // per part so each part resolves the focused occurrence within itself. The focused
                        // message contains the query by definition, so the fast-path bail never triggers —
                        // compute each part's starting offset once (not on every recomposition, which would
                        // re-parse every part's markdown each animateScrollBy frame).
                        val partOffsets = remember(contentParts, searchQuery, isCurrentSearchMatch) {
                            val offsets = IntArray(contentParts.size)
                            if (isCurrentSearchMatch && !searchQuery.isNullOrBlank()) {
                                // Lockstep with countMessageOccurrences: a late batch label a
                                // finalized phase consumed is not drawn, so it contributes no offset.
                                val consumed = findLateBatchLabelsConsumedByPhase(contentParts)
                                var acc = 0
                                contentParts.forEachIndexed { i, part ->
                                    offsets[i] = acc
                                    acc += countPartOccurrences(
                                        part,
                                        searchQuery,
                                        consumedLateBatchLabel = i in consumed,
                                    )
                                }
                            }
                            offsets
                        }
                        // Segment + group the parts ONCE, not while emitting them: a group's header has to
                        // be written before its members, which a Column cannot express without knowing the
                        // group's extent up front. Pure, so the boundaries are unit-testable.
                        val segments = remember(contentParts) {
                            groupContentParts(
                                contentParts,
                                groupActivity = !hasParallelGroupIds(contentParts),
                            )
                        }

                        // A collapsed group would swallow the match the user just navigated to, so the
                        // one holding it opens. Resolved once per focus rather than per group per frame.
                        val focusedGroupKey = remember(
                            segments, partOffsets, searchQuery, searchFocusedOccurrence, isCurrentSearchMatch,
                        ) {
                            if (!isCurrentSearchMatch || searchQuery.isNullOrBlank() || searchFocusedOccurrence < 0) {
                                null
                            } else {
                                segments.asSequence()
                                    .flatMap { it.groups.asSequence() }
                                    .filterIsInstance<ContentGroup.Activity>()
                                    .firstOrNull { group ->
                                        group.entries.any { entry ->
                                            val start = partOffsets[entry.index]
                                            val count = countPartOccurrences(entry.part, searchQuery)
                                            searchFocusedOccurrence in start until (start + count)
                                        }
                                    }?.key
                            }
                        }

                        @Composable
                        fun PartContent(entry: IndexedContentPart, hideAttachments: Boolean = false) {
                            val part = entry.part
                            val focusedInPart =
                                if (isCurrentSearchMatch) searchFocusedOccurrence - partOffsets[entry.index] else -1
                            when (part.type) {
                                // Handled here rather than in the shared dispatcher because both need the
                                // viewer's identity, which a single-part renderer has no business knowing.
                                ContentType.STEER -> part.steerText()?.let { steered ->
                                    SteerContentPart(
                                        text = steered,
                                        userName = userName,
                                        userAvatarUrl = userAvatarUrl,
                                        fontSizeMultiplier = fontSizeMultiplier,
                                        useKatex = useKatex,
                                    )
                                }
                                ContentType.ACTIVITY_LABEL -> OrphanActivityLabel(part.activityLabelText())
                                else -> ContentPartRenderer(
                                    part = part,
                                    baseUrl = baseUrl,
                                    fontSizeMultiplier = fontSizeMultiplier,
                                    useKatex = useKatex,
                                    attachments = inlineAttachments,
                                    showImageDescriptions = showImageDescriptions,
                                    searchQuery = if (isSearchMatch) searchQuery else null,
                                    searchFocusedOccurrence = focusedInPart,
                                    onFocusedOccurrencePosition = if (isCurrentSearchMatch) onFocusedOccurrencePosition else null,
                                    stateKey = "${message.messageId}:${entry.index}",
                                    hideAttachments = hideAttachments,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }

                        segments.forEach { segment ->
                            key(segment.key) {
                                // A traversal group so the attribution header is read immediately before
                                // its own content instead of segments interleaving. It is not implicit:
                                // a plain Column does not set one.
                                Column(modifier = Modifier.semantics { isTraversalGroup = true }) {
                                    segment.author?.let { author ->
                                        SegmentAuthorHeader(
                                            author = author,
                                            messageSender = message.sender,
                                            messageIconUrl = message.iconURL,
                                            messageEndpoint = message.endpoint,
                                        )
                                    }
                                    segment.groups.forEach { group ->
                                        // Keyed, so per-part state (an expanded thinking block) follows its
                                        // part when grouping shifts it under a wrapper instead of staying
                                        // with whatever now occupies that position.
                                        key(group.key) {
                                            when (group) {
                                                is ContentGroup.Single -> PartContent(group.entry)
                                                is ContentGroup.Activity -> {
                                                    val hoisted = remember(inlineAttachments, group) {
                                                        collectGroupAttachments(
                                                            inlineAttachments,
                                                            group.groupedToolCallIds(),
                                                        )
                                                    }
                                                    val groupFailed = remember(inlineAttachments, group) {
                                                        activityGroupFailed(group, inlineAttachments)
                                                    }
                                                    ActivityGroup(
                                                        group = group,
                                                        failed = groupFailed,
                                                        stateKey = "${message.messageId}:${group.key}",
                                                        autoExpand = group.key == focusedGroupKey,
                                                        autoExpandKey = LocalSearchFocusNonce.current,
                                                        hoistedAttachments = {
                                                            // Required: the inline path gets this from
                                                            // the dispatcher, the hoisted one sits
                                                            // outside it and would otherwise leak
                                                            // filenames into "Select all".
                                                            DisableSelection {
                                                                ToolAttachmentBuckets(
                                                                    buckets = hoisted,
                                                                    baseUrl = baseUrl,
                                                                    modifier = Modifier.padding(top = 4.dp),
                                                                )
                                                            }
                                                        },
                                                    ) {
                                                        group.entries.forEach { entry ->
                                                            key(entry.index) {
                                                                PartContent(entry, hideAttachments = true)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (isPersistedTurnError(message)) {
                            StreamErrorPart(raw = message.text)
                        } else if (message.text.isNotBlank()) {
                            val query = if (isSearchMatch) searchQuery else null
                            val focused = if (isCurrentSearchMatch) searchFocusedOccurrence else -1
                            val onFocused = if (isCurrentSearchMatch) onFocusedOccurrencePosition else null
                            // Reasoning saved inside the text (`:::thinking`) renders as a reasoning
                            // block ahead of the reply, as on web; its matches are numbered first.
                            val runs = remember(message.text) { message.legacyTextRuns() }
                            val reasoningMatches = remember(runs, query) {
                                if (query.isNullOrBlank()) 0 else countMarkdownOccurrences(runs.reasoning.orEmpty(), query)
                            }
                            runs.reasoning?.let { reasoning ->
                                ThinkingContentPart(
                                    thinkingText = reasoning,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    fontSizeMultiplier = fontSizeMultiplier,
                                    useKatex = useKatex,
                                    searchQuery = query,
                                    searchFocusedOccurrence = if (focused < reasoningMatches) focused else -1,
                                    onFocusedOccurrencePosition = onFocused,
                                    stateKey = "${message.messageId}:legacy",
                                )
                            }
                            val answerFocused = if (focused >= reasoningMatches) focused - reasoningMatches else -1
                            if (runs.answer.isBlank()) {
                                // Reasoning only: nothing else to draw.
                            } else if (message.textFallbackSplitsArtifacts()) {
                                TextContentPart(
                                    text = runs.answer,
                                    fontSizeMultiplier = fontSizeMultiplier,
                                    useKatex = useKatex,
                                    searchQuery = query,
                                    searchFocusedOccurrence = answerFocused,
                                    onFocusedOccurrencePosition = onFocused,
                                )
                            } else {
                                MarkdownContent(
                                    text = runs.answer,
                                    fontSizeMultiplier = fontSizeMultiplier,
                                    useKatex = useKatex,
                                    searchQuery = query,
                                    searchFocusedOccurrence = answerFocused,
                                    onFocusedOccurrencePosition = onFocused,
                                )
                            }
                        }
                    }

                    DisableSelection {
                        WorkspaceChangesSection(
                            changes = workspace.changes,
                            baseUrl = baseUrl,
                            stateKey = message.messageId,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    // Deferred office-doc preview attachments (v0.8.6) on a persisted message
                    // render as their own artifact card. Non-office attachments are unaffected.
                    val renderedCallIds = remember(message.content) { renderedToolCallIds(message.content) }
                    DisableSelection {
                        OfficePreviewAttachments(
                            attachments = inlineAttachments,
                            renderedToolCallIds = renderedCallIds,
                            baseUrl = baseUrl,
                            isDarkTheme = isSurfaceDark(),
                        )
                    }

                    val orphanMemory = remember(inlineAttachments, renderedCallIds) {
                        collectUnrenderedMemoryArtifacts(inlineAttachments, renderedCallIds)
                    }
                    DisableSelection {
                        MemoryArtifactsSection(
                            artifacts = orphanMemory,
                            stateKey = remember(inlineAttachments, message.messageId) {
                                memoryArtifactsStateKey(inlineAttachments, message.messageId)
                            },
                            onManage = LocalOpenMemories.current,
                        )
                    }
                }
            }
        }
    }

    // Action row: sibling nav + message actions (tap-to-reveal)
    val hasActions = siblingCount > 1 || onEdit != null || onRegenerate != null ||
        onCopy != null || onFeedback != null || onContinue != null ||
        onReadAloud != null || onFork != null
    if (hasActions) {
        AnimatedVisibility(
            visible = showActions,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("message_actions"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Sibling navigator (left side)
                    if (siblingCount > 1 && onSiblingNavigation != null) {
                        SiblingNavigator(
                            siblingIndex = siblingIndex,
                            siblingCount = siblingCount,
                            onNavigate = onSiblingNavigation,
                        )
                    } else {
                        Spacer(modifier = Modifier.width(0.dp))
                    }

                    // Action buttons (right side)
                    ActionButtons(
                        isUser = isUser,
                        onFeedback = onFeedback,
                        currentFeedback = currentFeedback,
                        onPickFeedbackTag = { pendingFeedbackRating = it.name },
                        onCopy = onCopy,
                        onEdit = onEdit,
                        onRegenerate = onRegenerate,
                        onReadAloud = onReadAloud,
                        isReading = isReading,
                        onFork = onFork,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

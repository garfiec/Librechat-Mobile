package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Decision
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore.Subject
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.chat.components.web.VendoredAdvisory
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.artifact_library_allowed
import com.garfiec.librechat.feature.chat.resources.artifact_library_change
import com.garfiec.librechat.feature.chat.resources.artifact_library_declined
import com.garfiec.librechat.feature.chat.resources.artifact_library_deprecated
import com.garfiec.librechat.feature.chat.resources.artifact_library_load
import com.garfiec.librechat.feature.chat.resources.artifact_library_remember
import com.garfiec.librechat.feature.chat.resources.artifact_library_sandbox
import com.garfiec.librechat.feature.chat.resources.artifact_library_skip
import com.garfiec.librechat.feature.chat.resources.artifact_library_title
import com.garfiec.librechat.feature.chat.resources.artifact_library_unmaintained
import com.garfiec.librechat.feature.chat.resources.artifact_library_vulnerable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Asks before a React artifact loads a flagged library (`advisory` in scripts/web-assets.json:
 * unmaintained, deprecated or vulnerable), then hands [preview] the libraries the user allowed.
 * Every other flagged library the page replaces with a stub saying so.
 *
 * Every other artifact goes straight to [preview]: the check is a scan of its imports, and only
 * React artifacts import anything. For a flagged one nothing renders until the stored answers
 * are read, so a remembered answer never flashes the prompt.
 *
 * One library is asked about at a time. "Remember" makes the answer device-wide; without it the
 * answer holds for this artifact until the app restarts, so the inline preview and the fullscreen
 * panel ask once between them. Once answered, a row above the preview says what loaded and what
 * didn't; its Change takes back the answers that apply here ([ArtifactLibraryConsentStore.reconsider])
 * and brings the prompt back. Settings > Chat > Artifacts sets the remembered answers directly.
 *
 * [onOpen]: inline, the prompt sits in the same card as a preview and a tap on it opens the
 * panel, as a preview's does; the panel passes none and shows the prompt bare.
 *
 * [blockSheetDrag]: the prompt scrolls inside a bottom sheet, so it keeps its scroll from
 * dragging the sheet, as the panel's code view does.
 */
@Composable
internal fun ArtifactLibraryGate(
    content: String,
    type: String,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    blockSheetDrag: Boolean = false,
    onOpen: (() -> Unit)? = null,
    preview: @Composable (allowedLibraries: Set<String>, modifier: Modifier) -> Unit,
) {
    val advisories = remember(content, type) { ArtifactWebContent.libraryAdvisories(content, type) }
    val store = koinInject<ArtifactLibraryConsentStore>()
    val snapshot by store.snapshot.collectAsStateWithLifecycle()
    val artifactId = remember(content) { "${content.length}:${content.hashCode()}" }
    val scope = rememberCoroutineScope()

    // `preview` has exactly one call site below: a slot invoked from several branches is a new
    // composition in each, so moving between them would rebuild the WebView.
    val current = snapshot
    val decisions = advisories.associateWith { advisory ->
        current?.decisionFor(advisory.subject(), artifactId) ?: Decision.ASK
    }
    val pending = decisions.entries.firstOrNull { it.value == Decision.ASK }?.key
    when {
        // An artifact with nothing flagged never waits on the stored answers.
        advisories.isNotEmpty() && current == null -> Box(modifier)
        pending != null -> LibraryPrompt(
            advisory = pending,
            modifier = modifier,
            blockSheetDrag = blockSheetDrag,
            onOpen = onOpen,
            onAnswer = { allow, remember ->
                if (remember) {
                    scope.launch { store.remember(pending.subject(), allow) }
                } else {
                    store.decideOnce(pending.subject(), artifactId, allow)
                }
            },
        )
        else -> {
            val allowed = decisions.filterValues { it == Decision.ALLOW }.keys.map { it.library }.toSet()
            val declined = decisions.filterValues { it == Decision.DECLINE }.keys.map { it.library }
            Column(modifier) {
                if (advisories.isNotEmpty()) {
                    AnswerRow(
                        allowed = allowed.toList(),
                        declined = declined,
                        onChange = {
                            scope.launch { advisories.forEach { store.reconsider(it.subject(), artifactId) } }
                        },
                    )
                }
                // Only a panel gives the column a bounded height to share out; inline, the gate
                // sits in a lazy list item, where a weight has no height to divide.
                preview(allowed, if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth())
            }
        }
    }
}

/** The advisory's kind is part of the question, so an escalated advisory asks again. */
internal fun VendoredAdvisory.subject() = Subject(library, kind.name.lowercase())

@Composable
private fun AnswerRow(allowed: List<String>, declined: List<String>, onChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (declined.isNotEmpty()) {
                AnswerText(stringResource(Res.string.artifact_library_declined, declined.joinToString(", ")))
            }
            if (allowed.isNotEmpty()) {
                AnswerText(stringResource(Res.string.artifact_library_allowed, allowed.joinToString(", ")))
            }
        }
        AdaptiveTextButton(onClick = onChange) {
            Text(stringResource(Res.string.artifact_library_change))
        }
    }
}

@Composable
private fun AnswerText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryPrompt(
    advisory: VendoredAdvisory,
    onAnswer: (allow: Boolean, remember: Boolean) -> Unit,
    blockSheetDrag: Boolean,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Keyed by library: the next prompt in a multi-library artifact starts unticked.
    var rememberChoice by rememberSaveable(advisory.library) { mutableStateOf(false) }
    val reason = when (advisory.kind) {
        VendoredAdvisory.Kind.UNMAINTAINED -> Res.string.artifact_library_unmaintained
        VendoredAdvisory.Kind.DEPRECATED -> Res.string.artifact_library_deprecated
        VendoredAdvisory.Kind.VULNERABLE -> Res.string.artifact_library_vulnerable
    }
    val scrollState = rememberScrollState()
    val sheetBlocker = remember(scrollState, blockSheetDrag) {
        if (blockSheetDrag) Modifier.nestedScroll(SheetScrollBlocker(scrollState)) else Modifier
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        // Never a glass card: inline, it would sample the message list it sits in, a render
        // loop that crashes. Capped, then scrolled: inline, the gate sits in a lazy list item
        // whose height is unbounded (an uncapped verticalScroll throws there), and at a large
        // font the prompt can outgrow a short panel.
        val body = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PROMPT_MAX_HEIGHT)
                    .then(sheetBlocker)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.artifact_library_title, advisory.library),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(reason, advisory.library), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(Res.string.artifact_library_sandbox),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.toggleable(
                        value = rememberChoice,
                        role = Role.Checkbox,
                        onValueChange = { rememberChoice = it },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AdaptiveCheckbox(checked = rememberChoice, onCheckedChange = null)
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = stringResource(Res.string.artifact_library_remember, advisory.library),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                // FlowRow: two buttons side by side overflow a narrow inline card at a large font.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AdaptiveOutlinedButton(onClick = { onAnswer(false, rememberChoice) }) {
                        Text(stringResource(Res.string.artifact_library_skip))
                    }
                    AdaptiveButton(onClick = { onAnswer(true, rememberChoice) }) {
                        Text(stringResource(Res.string.artifact_library_load))
                    }
                }
            }
        }
        if (onOpen != null) ArtifactCardSurface(onTap = onOpen) { body() } else body()
    }
}

private val PROMPT_MAX_HEIGHT = 480.dp

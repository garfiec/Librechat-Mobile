package com.garfiec.librechat.feature.chat.components.artifact

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.data.datastore.ArtifactLibraryConsentStore
import com.garfiec.librechat.core.model.preferences.ArtifactLibraryDecision
import com.garfiec.librechat.core.model.preferences.ArtifactLibrarySubject
import com.garfiec.librechat.feature.chat.components.web.VendoredAdvisory
import kotlinx.coroutines.launch
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
        current?.decisionFor(advisory.subject(), artifactId) ?: ArtifactLibraryDecision.ASK
    }
    val pending = decisions.entries.firstOrNull { it.value == ArtifactLibraryDecision.ASK }?.key
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
            val allowed = decisions.filterValues { it == ArtifactLibraryDecision.ALLOW }.keys.map { it.library }.toSet()
            val declined = decisions.filterValues { it == ArtifactLibraryDecision.DECLINE }.keys.map { it.library }
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
internal fun VendoredAdvisory.subject() = ArtifactLibrarySubject(library, kind.name.lowercase())

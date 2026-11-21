package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.model.mcp.UiResource
import com.garfiec.librechat.core.model.mcp.isLegacyHtml
import com.garfiec.librechat.core.ui.components.AdaptiveDialog
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.components.topbar.MaterialBarStyle
import com.garfiec.librechat.feature.chat.components.fadeOutBottom
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_close
import com.garfiec.librechat.feature.chat.resources.mcp_app_loading_interactive_view
import org.jetbrains.compose.resources.stringResource

/** Cached per core/ui Compose perf rule 13. */
private val FRAME_SHAPE = RoundedCornerShape(12.dp)

/** Before the resource reports a size; web's iframe has none either, so it is a guess. */
private val DEFAULT_HEIGHT = 240.dp

/** Upstream `clampAppViewHeight`'s floor; the ceiling is the screen's share below. */
private val MIN_HEIGHT = 80.dp

/** Upstream `UIResourceCarousel`'s card size. */
private val CAROUSEL_CARD_WIDTH = 230.dp
private val CAROUSEL_CARD_MIN_HEIGHT = 360.dp

/** Where [legacyUiInlineCapped], a resource never claims more of the thread than this share of the window, as inline artifacts. */
private const val MAX_HEIGHT_FRACTION = 0.7f

/**
 * No frame is taller, cap or not: Compose can't lay out a size much past this at any density, and a
 * resource may report any height (one that grows with its frame reports more each time).
 */
private val MAX_FRAME_HEIGHT = 20_000.dp

/**
 * What one `\ui{…}` marker draws: nothing when `legacyHtmlEnabled` is off or no named resource is a
 * legacy HTML one, the one resource for a single id, else a horizontal carousel (upstream
 * `MCPUIResource` / `MCPUIResourceCarousel`). A single id the conversation doesn't hold shows web's
 * "UI Resource not found" chip, whatever `legacyHtmlEnabled` says (web checks it first too); a
 * carousel drops unknown ids silently. While the reply streams it holds a placeholder: a web view
 * there would reload on every rebuild of the streaming item, and again when the reply is saved.
 */
@Composable
internal fun LegacyUiResourceBlock(ids: List<String>, streaming: Boolean, modifier: Modifier = Modifier) {
    val local = LocalLegacyUiResources.current ?: return
    val single = ids.singleOrNull()
    if (single != null && single !in local.byId) {
        // Not while streaming: the resource's attachment can still arrive after its marker.
        if (!streaming) UiResourceNotFoundChip(single, modifier)
        return
    }
    if (!local.enabled) return
    val resources = remember(ids, local.byId) {
        ids.mapNotNull { id -> local.byId[id]?.takeIf { it.isLegacyHtml && it.inlineHtml() != null } }
    }
    if (resources.isEmpty()) return
    if (streaming) {
        Box(
            modifier = modifier
                .height(DEFAULT_HEIGHT)
                .clip(FRAME_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.mcp_app_loading_interactive_view),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val onMessage by rememberUpdatedState(local.onMessage)
    BoxWithConstraints(modifier = modifier) {
        val cap = if (legacyUiInlineCapped) (legacyWindowHeight() * MAX_HEIGHT_FRACTION).coerceAtLeast(MIN_HEIGHT) else null
        val single = resources.singleOrNull()
        if (single != null) {
            key(single.key) {
                LegacyUiFrame(single, width = maxWidth, minHeight = MIN_HEIGHT, cap = cap, onMessage = onMessage)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                resources.forEachIndexed { index, resource ->
                    key(index, resource.key) {
                        LegacyUiFrame(
                            resource,
                            width = CAROUSEL_CARD_WIDTH,
                            minHeight = CAROUSEL_CARD_MIN_HEIGHT,
                            cap = cap?.coerceAtLeast(CAROUSEL_CARD_MIN_HEIGHT),
                            onMessage = onMessage,
                        )
                    }
                }
            }
        }
    }
}

internal fun legacyUiFrameHeight(reported: Int?, minHeight: Dp, cap: Dp?): Dp {
    val height = (reported?.dp ?: DEFAULT_HEIGHT).coerceIn(minHeight, MAX_FRAME_HEIGHT)
    return if (cap == null) height else height.coerceAtMost(cap)
}

internal fun legacyUiClipped(reported: Int?, cap: Dp?): Boolean =
    cap != null && reported != null && reported.dp > cap

@Composable
private fun LegacyUiFrame(
    resource: UiResource,
    width: Dp,
    minHeight: Dp,
    cap: Dp?,
    onMessage: (String, (Boolean) -> Unit) -> Unit,
) {
    val html = remember(resource) { resource.inlineHtml().orEmpty() }
    // The platform views load their page once, when created: a resource whose content changed is a
    // new page, so it gets a new view, a new host (the old view's release clears only its own) and
    // its own height.
    key(html) {
        var reported by remember { mutableStateOf<Int?>(null) }
        var showFull by remember { mutableStateOf(false) }
        val host = remember {
            LegacyUiFrameHost(onHeight = { reported = it }, onMessage = { text, reply -> onMessage(text, reply) })
        }
        val clipped = legacyUiClipped(reported, cap)
        Column(modifier = Modifier.width(width)) {
            LegacyUiResourceWebView(
                page = remember { LegacyUiRelayPage.build(html) },
                host = host,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(legacyUiFrameHeight(reported, minHeight, cap))
                    .clip(FRAME_SHAPE)
                    .then(if (clipped) Modifier.fadeOutBottom() else Modifier),
            )
            if (clipped) ShowFullAppButton(onClick = { showFull = true })
        }
        if (showFull) {
            LegacyUiFullView(html = html, onMessage = onMessage, onClose = { showFull = false })
        }
    }
}

/** The whole resource, a screen tall and scrolling: a fresh page beside the inline one. */
@Composable
private fun LegacyUiFullView(html: String, onMessage: (String, (Boolean) -> Unit) -> Unit, onClose: () -> Unit) {
    AdaptiveDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding(),
        ) {
            AdaptiveTopBar(
                spec = AdaptiveTopBarSpec(
                    navigation = BarNavigation(
                        icon = BarIcons.Close,
                        contentDescription = stringResource(Res.string.cd_close),
                        onClick = onClose,
                    ),
                    title = BarTitle(text = null),
                ),
                materialStyle = MaterialBarStyle(containerColor = MaterialTheme.colorScheme.surface),
            )
            val host = remember {
                LegacyUiFrameHost(onHeight = {}, onMessage = { text, reply -> onMessage(text, reply) })
            }
            LegacyUiResourceWebView(
                page = remember { LegacyUiRelayPage.build(html, fullView = true) },
                host = host,
                modifier = Modifier.fillMaxWidth().weight(1f),
                fullView = true,
            )
        }
    }
}

package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.ui.theme.chatColors
import com.garfiec.librechat.feature.chat.components.artifact.Artifact

/**
 * What the chat UI draws through a platform renderer (WebViews, players, native PDF). The feature
 * module provides the real ones once at the navigation root ([LocalChatContentRenderers]); a slot
 * left null draws the fallback named on it, which shows the source or nothing.
 */
@Immutable
class ChatContentRenderers(
    /** Math, block ([display]) or inline. Null: the LaTeX source in monospace ([LatexSource]). */
    val math: (@Composable (latex: String, display: Boolean, useKatex: Boolean, modifier: Modifier) -> Unit)? = null,
    /** A ```mermaid fence. Null: the fence as a code block. */
    val mermaid: (@Composable (code: String, modifier: Modifier) -> Unit)? = null,
    /** A base64 audio part. Null: nothing. */
    val audio: (@Composable (data: String?, format: String?, modifier: Modifier) -> Unit)? = null,
    /** A video part. Null: its URL as a link. */
    val video: (@Composable (url: String, modifier: Modifier) -> Unit)? = null,
    /** The PDF overlay's page surface. Null: reports a render error, so the overlay offers the file. */
    val pdf: (@Composable (bytes: ByteArray, onRenderError: () -> Unit, modifier: Modifier) -> Unit)? = null,
    /** An artifact drawn inside a message. Null: the tap-to-open card (an office preview: its header). */
    val inlineArtifact: InlineArtifactRenderer? = null,
    /** The artifact panel's Preview tab. Null: the artifact is not previewable and shows its code. */
    val artifactPreview: (
        @Composable (
            content: String,
            type: String,
            isDarkTheme: Boolean,
            modifier: Modifier,
            allowedLibraries: Set<String>,
        ) -> Unit
    )? = null,
    /** Asks about flagged libraries before a WebView preview runs. Null: the preview runs with none. */
    val artifactLibraryGate: ArtifactLibraryGateRenderer? = null,
    /** The artifact panel's Share action. Null: no Share button. */
    val shareArtifact: ((Artifact) -> Unit)? = null,
    /** MCP App rows for these tool calls. Null: nothing. */
    val mcpAppRows: (
        @Composable (attachments: List<Attachment>, toolCallIds: Collection<String>, modifier: Modifier) -> Unit
    )? = null,
    /** A legacy MCP-UI `\ui{…}` block. Null: nothing (the markers are already stripped). */
    val uiResourceBlock: (@Composable (ids: List<String>, streaming: Boolean, modifier: Modifier) -> Unit)? = null,
)

/** The inline artifact WebView, and whether it composites WebGL (Android's software layer can't). */
@Immutable
class InlineArtifactRenderer(
    val drawsWebGl: Boolean,
    val content: @Composable (
        artifact: Artifact,
        onTap: () -> Unit,
        modifier: Modifier,
        fitContent: Boolean,
        header: (@Composable () -> Unit)?,
        allowedLibraries: Set<String>,
    ) -> Unit,
)

/** Wraps a WebView [preview] in the flagged-library consent gate; see `ArtifactLibraryGate`. */
fun interface ArtifactLibraryGateRenderer {
    @Composable
    fun Gate(
        content: String,
        type: String,
        modifier: Modifier,
        fillHeight: Boolean,
        blockSheetDrag: Boolean,
        onOpen: (() -> Unit)?,
        preview: @Composable (allowedLibraries: Set<String>, modifier: Modifier) -> Unit,
    )
}

val LocalChatContentRenderers = staticCompositionLocalOf { ChatContentRenderers() }

/** Math through [ChatContentRenderers.math], else its source. */
@Composable
fun ChatMath(latex: String, display: Boolean, useKatex: Boolean, modifier: Modifier = Modifier) {
    val math = LocalChatContentRenderers.current.math
    if (math != null) math(latex, display, useKatex, modifier) else LatexSource(latex, display, modifier)
}

/** A mermaid fence through [ChatContentRenderers.mermaid], else as code. */
@Composable
fun ChatMermaid(code: String, modifier: Modifier = Modifier) {
    val mermaid = LocalChatContentRenderers.current.mermaid
    if (mermaid != null) mermaid(code, modifier) else CodeBlock(code = code, language = "mermaid", modifier = modifier)
}

/** A video part through [ChatContentRenderers.video], else its URL as a link. */
@Composable
internal fun ChatVideo(url: String, modifier: Modifier = Modifier) {
    val video = LocalChatContentRenderers.current.video
    if (video != null) {
        video(url, modifier)
    } else {
        val uriHandler = LocalUriHandler.current
        Text(
            text = url,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.clickable { uriHandler.openUri(url) },
        )
    }
}

/** The PDF page surface through [ChatContentRenderers.pdf], else a render error. */
@Composable
internal fun ChatPdf(bytes: ByteArray, onRenderError: () -> Unit, modifier: Modifier = Modifier) {
    val pdf = LocalChatContentRenderers.current.pdf
    if (pdf != null) {
        pdf(bytes, onRenderError, modifier)
    } else {
        val currentOnRenderError by rememberUpdatedState(onRenderError)
        LaunchedEffect(bytes) { currentOnRenderError() }
    }
}

/** A WebView [preview] behind [ChatContentRenderers.artifactLibraryGate], else with no flagged library. */
@Composable
internal fun ChatArtifactLibraryGate(
    content: String,
    type: String,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    blockSheetDrag: Boolean = false,
    onOpen: (() -> Unit)? = null,
    preview: @Composable (allowedLibraries: Set<String>, modifier: Modifier) -> Unit,
) {
    val gate = LocalChatContentRenderers.current.artifactLibraryGate
    if (gate != null) {
        gate.Gate(content, type, modifier, fillHeight, blockSheetDrag, onOpen, preview)
    } else {
        preview(emptySet(), modifier)
    }
}

/** LaTeX source in monospace: what math looks like without a math renderer. */
@Composable
fun LatexSource(latex: String, display: Boolean, modifier: Modifier = Modifier) {
    if (display) {
        Text(
            text = latex,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            ),
            color = chatColors().messageText,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    color = chatColors().codeBody,
                    shape = RoundedCornerShape(6.dp),
                )
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    } else {
        Text(
            text = latex,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = chatColors().messageText,
            modifier = modifier
                .background(
                    color = chatColors().codeBody,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

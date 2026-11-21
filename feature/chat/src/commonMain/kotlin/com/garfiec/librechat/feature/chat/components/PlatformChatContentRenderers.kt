package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactLibraryGate
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactPreviewSurface
import com.garfiec.librechat.feature.chat.components.artifact.InlineArtifactView
import com.garfiec.librechat.feature.chat.components.artifact.inlineArtifactDrawsWebGl
import com.garfiec.librechat.feature.chat.components.artifact.rememberShareArtifact
import com.garfiec.librechat.feature.chat.components.mcpapp.LegacyUiResourceBlock
import com.garfiec.librechat.feature.chat.components.mcpapp.McpAppRows

/**
 * This platform's [ChatContentRenderers]: the WebViews, players and PDF engine the chat UI draws
 * through. Provided once as [LocalChatContentRenderers] where the app's navigation is hosted.
 */
@Composable
fun rememberPlatformChatContentRenderers(): ChatContentRenderers {
    val shareArtifact = rememberShareArtifact()
    return remember(shareArtifact) {
        ChatContentRenderers(
            math = { latex, display, useKatex, modifier ->
                if (display) LatexBlock(latex, modifier, useKatex) else LatexInline(latex, modifier, useKatex)
            },
            mermaid = { code, modifier -> MermaidDiagram(code, modifier) },
            audio = { data, format, modifier -> AudioContent(data, format, modifier) },
            video = { url, modifier -> VideoContent(url, modifier) },
            pdf = { bytes, onRenderError, modifier -> PdfViewer(bytes, onRenderError, modifier) },
            inlineArtifact = InlineArtifactRenderer(drawsWebGl = inlineArtifactDrawsWebGl) {
                    artifact, onTap, modifier, fitContent, header, allowedLibraries ->
                InlineArtifactView(artifact, onTap, modifier, fitContent, header, allowedLibraries)
            },
            artifactPreview = { content, type, isDarkTheme, modifier, allowedLibraries ->
                ArtifactPreviewSurface(content, type, isDarkTheme, modifier, allowedLibraries)
            },
            artifactLibraryGate = ArtifactLibraryGateRenderer { content, type, modifier, fillHeight, blockSheetDrag, onOpen, preview ->
                ArtifactLibraryGate(content, type, modifier, fillHeight, blockSheetDrag, onOpen, preview)
            },
            shareArtifact = shareArtifact,
            mcpAppRows = { attachments, toolCallIds, modifier -> McpAppRows(attachments, toolCallIds, modifier) },
            uiResourceBlock = { ids, streaming, modifier -> LegacyUiResourceBlock(ids, streaming, modifier) },
        )
    }
}

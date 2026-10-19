package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Platform-specific audio player from base64-encoded data. */
@Composable
expect fun AudioContent(
    data: String?,
    format: String?,
    modifier: Modifier = Modifier,
)

/** Platform-specific video player from URL. */
@Composable
expect fun VideoContent(
    url: String,
    modifier: Modifier = Modifier,
)

/** Platform-specific LaTeX rendering. */
@Composable
expect fun LatexBlock(
    latex: String,
    modifier: Modifier = Modifier,
    useKatex: Boolean = false,
)

/** Platform-specific LaTeX inline rendering. */
@Composable
expect fun LatexInline(
    latex: String,
    modifier: Modifier = Modifier,
    useKatex: Boolean = false,
)

/** Platform-specific Mermaid diagram renderer. */
@Composable
expect fun MermaidDiagram(
    code: String,
    modifier: Modifier = Modifier,
)

/** Bounds on an inline diagram's reported height, shared so both platforms size a diagram alike. */
internal val MERMAID_MIN_INLINE_HEIGHT = 48.dp
internal val MERMAID_MAX_INLINE_HEIGHT = 300.dp

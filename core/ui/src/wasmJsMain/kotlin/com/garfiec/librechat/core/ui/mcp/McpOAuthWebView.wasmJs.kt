package com.garfiec.librechat.core.ui.mcp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome

// The browser build has no embedded WebView: the sheet stays empty.
@Composable
internal actual fun McpOAuthWebView(
    launch: McpOAuthLaunch.InApp,
    onOutcome: (McpOAuthOutcome) -> Unit,
    onLocationChange: (McpOAuthPageLocation) -> Unit,
    modifier: Modifier,
) = Unit

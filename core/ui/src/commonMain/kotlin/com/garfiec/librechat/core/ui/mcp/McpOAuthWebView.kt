package com.garfiec.librechat.core.ui.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpOAuthOutcome
import com.garfiec.librechat.core.ui.components.AdaptiveDialog
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBar
import com.garfiec.librechat.core.ui.components.topbar.AdaptiveTopBarSpec
import com.garfiec.librechat.core.ui.components.topbar.BarIcons
import com.garfiec.librechat.core.ui.components.topbar.BarNavigation
import com.garfiec.librechat.core.ui.components.topbar.BarTitle
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.mcp_oauth_connection_not_secure
import com.garfiec.librechat.core.ui.resources.mcp_oauth_connection_secure
import com.garfiec.librechat.core.ui.resources.mcp_oauth_sign_in_close
import com.garfiec.librechat.core.ui.resources.mcp_oauth_sign_in_title
import org.jetbrains.compose.resources.stringResource

/**
 * The full-screen sign-in for an MCP server's OAuth provider (v0.8.9-rc1 browser binding).
 *
 * The page runs in a WebView whose jar is seeded with the binding cookies of
 * [McpOAuthLaunch.InApp.seedCookies] before the provider's page loads, so the provider's redirect
 * to the server's callback carries them. [onOutcome] fires exactly once: when the callback
 * redirects to its success or error page, when the page cannot load, or when the user closes it.
 *
 * Not MCP-only despite the name: Settings' pull request account connect (v0.8.9-rc1) runs through it
 * too. Its callback (`/api/pull-requests/oauth/callback`) checks the same two cookies and lands on the
 * same `/oauth/success|error` pages, so the gate and the cookie seeding apply unchanged.
 */
@Composable
fun McpOAuthSignInDialog(
    launch: McpOAuthLaunch.InApp,
    onOutcome: (McpOAuthOutcome) -> Unit,
) {
    val currentOnOutcome by rememberUpdatedState(onOutcome)
    var reported by remember(launch) { mutableStateOf(false) }
    val report: (McpOAuthOutcome) -> Unit = remember(launch) {
        { outcome ->
            if (!reported) {
                reported = true
                currentOnOutcome(outcome)
            }
        }
    }
    var location by remember(launch) { mutableStateOf(mcpOAuthPageLocation(launch.authorizationUrl)) }
    AdaptiveDialog(
        onDismissRequest = { report(McpOAuthOutcome.Abandoned) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                AdaptiveTopBar(
                    spec = AdaptiveTopBarSpec(
                        navigation = BarNavigation(
                            BarIcons.Close,
                            stringResource(Res.string.mcp_oauth_sign_in_close),
                        ) { report(McpOAuthOutcome.Abandoned) },
                        title = BarTitle(stringResource(Res.string.mcp_oauth_sign_in_title, launch.serverName)),
                    ),
                    belowBar = location?.let { current -> { PageLocationRow(current) } },
                )
                McpOAuthWebView(
                    launch = launch,
                    onOutcome = report,
                    onLocationChange = { location = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
        }
    }
}

/**
 * The page's host under the bar, so the user can see whose sign-in page they are typing into: a
 * lock over HTTPS, a warning over plain HTTP (allowed only for the user's own server and loopback,
 * see [McpOAuthNavigationGate]).
 */
@Composable
private fun PageLocationRow(location: McpOAuthPageLocation) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (location.secure) Icons.Outlined.Lock else Icons.Outlined.Warning,
            contentDescription = stringResource(
                if (location.secure) Res.string.mcp_oauth_connection_secure else Res.string.mcp_oauth_connection_not_secure,
            ),
            tint = if (location.secure) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = location.host,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The WebView behind [McpOAuthSignInDialog]. Seeds [McpOAuthLaunch.InApp.seedCookies] for every
 * origin in [mcpOAuthCookieOrigins], then loads the authorization URL with a browser User-Agent
 * (providers refuse embedded browsers), and stops at [McpOAuthNavigationGate]'s verdict, the first
 * load included. [onLocationChange] reports each committed main-frame page.
 */
@Composable
internal expect fun McpOAuthWebView(
    launch: McpOAuthLaunch.InApp,
    onOutcome: (McpOAuthOutcome) -> Unit,
    onLocationChange: (McpOAuthPageLocation) -> Unit,
    modifier: Modifier = Modifier,
)

/**
 * Where the binding cookies must live: the callback's origin (from the authorization URL's
 * `redirect_uri`, the server's `DOMAIN_SERVER`) and the address the app talks to, in case they
 * differ. A browser would hold them only for the origin that set them; the WebView has to be told.
 */
fun mcpOAuthCookieOrigins(serverUrl: String, authorizationUrl: String): List<String> {
    val redirect = authorizationUrl.substringAfter('?', "").substringBefore('#').split('&')
        .firstOrNull { it.substringBefore('=') == "redirect_uri" }
        ?.substringAfter('=', "")
        ?.let(::decodeOriginComponent)
    return listOfNotNull(redirect?.let(::originOf), originOf(serverUrl)).distinct()
}

private fun originOf(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val rest = url.substring(schemeEnd + 3)
    val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
    val authority = rest.substring(0, end).substringAfterLast('@')
    if (authority.isEmpty()) return null
    return url.substring(0, schemeEnd).lowercase() + "://" + authority.lowercase()
}

private fun decodeOriginComponent(value: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < value.length) {
        val hex = if (value[i] == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null && hex < 0x80) {
            out.append(hex.toChar())
            i += 3
        } else {
            out.append(value[i])
            i++
        }
    }
    return out.toString()
}

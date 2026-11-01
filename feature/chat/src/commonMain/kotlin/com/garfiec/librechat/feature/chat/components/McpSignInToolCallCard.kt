package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_action
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_needed
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_needed_unnamed
import com.garfiec.librechat.feature.chat.resources.mcp_sign_in_resumes
import org.jetbrains.compose.resources.stringResource

private val CardShape = RoundedCornerShape(8.dp)

/**
 * A live tool call the run is holding until the user signs in to an MCP server (upstream
 * `ToolCall.tsx`'s OAuth button). The button binds and opens the sign-in; nothing launches on its
 * own, since the redirect hands a third-party provider this session. Without a known server
 * ([serverName] null) or a host ([LocalMcpSignIn] null) the prompt shows without the action.
 */
@Composable
internal fun McpSignInToolCallCard(
    serverName: String?,
    authUrl: String,
    modifier: Modifier = Modifier,
) {
    val signIn = LocalMcpSignIn.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = CardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (serverName != null) {
                        stringResource(Res.string.mcp_sign_in_needed, serverName)
                    } else {
                        stringResource(Res.string.mcp_sign_in_needed_unnamed)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.mcp_sign_in_resumes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (signIn != null && serverName != null) {
                val preparing = signIn.preparingServer == serverName
                AdaptiveFilledTonalButton(
                    onClick = { signIn.signIn(serverName, authUrl) },
                    enabled = signIn.preparingServer == null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // The label keeps its width under the spinner, so the row doesn't jump.
                        Text(
                            text = stringResource(Res.string.mcp_sign_in_action),
                            color = if (preparing) Color.Transparent else Color.Unspecified,
                        )
                        if (preparing) AdaptiveCircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

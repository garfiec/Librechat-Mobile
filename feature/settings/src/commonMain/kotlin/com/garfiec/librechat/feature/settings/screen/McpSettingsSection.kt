package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun McpSettingsSection(
    servers: List<McpServer>,
    connectionStatus: Map<String, McpServerStatus>,
    onAddServer: () -> Unit,
    onEditServer: (McpServer) -> Unit,
    onDeleteServer: (String) -> Unit,
    onReinitialize: (String) -> Unit,
    modifier: Modifier = Modifier,
    reinitializingServers: Set<String> = emptySet(),
    error: String? = null,
    mcpServersEnabled: Boolean = true,
    mcpServersCreateEnabled: Boolean = true,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!mcpServersEnabled) {
                Text(
                    text = stringResource(Res.string.mcp_not_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (error != null) {
                Text(
                    text = stringResource(Res.string.mcp_not_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (servers.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_mcp_servers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                servers.forEach { server ->
                    McpServerItem(
                        server = server,
                        serverStatus = connectionStatus[server.name],
                        isReinitializing = server.name in reinitializingServers,
                        onEdit = { onEditServer(server) },
                        onDelete = { onDeleteServer(server.name) },
                        onReinitialize = { onReinitialize(server.name) },
                    )
                }
            }

            if (mcpServersEnabled && error == null) {
                Spacer(modifier = Modifier.height(4.dp))
            }

            // "+ Add MCP Server" stays behind both gates:
            // USE must be allowed (otherwise the whole section is degraded) and
            // CREATE must be allowed (separate sub-action).
            if (mcpServersEnabled && error == null && mcpServersCreateEnabled) {
                AdaptiveOutlinedButton(
                    onClick = onAddServer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.add_mcp_server))
                }
            }
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun McpServerItem(
    server: McpServer,
    serverStatus: McpServerStatus?,
    isReinitializing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReinitialize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = serverStatus?.isConnected ?: server.isConnected

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            McpServerStatusIndicator(
                isConnected = if (serverStatus != null) isConnected else null,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.title ?: server.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (server.url.isNotBlank()) {
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onReinitialize,
                modifier = Modifier.size(32.dp),
                enabled = !isReinitializing,
            ) {
                if (isReinitializing) {
                    AdaptiveCircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.cd_reinitialize),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(Res.string.cd_edit),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.cd_delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Show error if present
        val error = serverStatus?.error ?: server.error
        if (!error.isNullOrBlank()) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 22.dp, top = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Show tools if available
        if (server.tools.isNotEmpty()) {
            McpToolList(
                tools = server.tools,
                modifier = Modifier.padding(start = 22.dp, top = 4.dp),
            )
        }
    }
}

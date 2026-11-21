package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCheckbox
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cancel
import com.garfiec.librechat.feature.chat.resources.mcp_app_action_warning
import com.garfiec.librechat.feature.chat.resources.mcp_app_cancel_pauses
import com.garfiec.librechat.feature.chat.resources.mcp_app_confirm_message
import com.garfiec.librechat.feature.chat.resources.mcp_app_confirm_tool
import com.garfiec.librechat.feature.chat.resources.mcp_app_dont_ask_again
import com.garfiec.librechat.feature.chat.resources.mcp_app_location_allow
import com.garfiec.librechat.feature.chat.resources.mcp_app_location_deny
import com.garfiec.librechat.feature.chat.resources.mcp_app_location_text
import com.garfiec.librechat.feature.chat.resources.mcp_app_location_title
import com.garfiec.librechat.feature.chat.resources.mcp_app_message_tool_context
import com.garfiec.librechat.feature.chat.resources.mcp_app_run_tool
import com.garfiec.librechat.feature.chat.resources.mcp_app_send_message
import com.garfiec.librechat.feature.chat.resources.mcp_app_stop
import com.garfiec.librechat.feature.chat.resources.mcp_app_tool_line
import com.garfiec.librechat.feature.chat.viewmodel.McpAppLocationRequest
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Cached per core/ui Compose perf rule 13. */
private val CODE_BLOCK_SHAPE = RoundedCornerShape(8.dp)

private const val APPROVE_ARM_DELAY_MS = 500L

/**
 * Upstream `approval.tsx`'s confirmation: the server and tool (or the server alone for a message),
 * then the exact arguments or text, whole, in a scrolling block. Three stacked buttons, since they
 * never fit one row on a phone.
 */
@Composable
fun McpAppActionDialog(
    action: McpAppAction,
    onApprove: (dontAskAgain: Boolean) -> Unit,
    onCancel: () -> Unit,
    onStop: () -> Unit,
) {
    val isMessage = action is McpAppAction.Message
    // Approve arms only after the dialog has been up a moment, so a tap meant for the previous
    // dialog (or the App underneath) can't approve an action the user hasn't read.
    var armed by remember(action) { mutableStateOf(false) }
    // Unchecked on every dialog: a grant is never carried from one decision to the next.
    var dontAskAgain by remember(action) { mutableStateOf(false) }
    LaunchedEffect(action) {
        delay(APPROVE_ARM_DELAY_MS)
        armed = true
    }
    AdaptiveAlertDialog(
        // Back or an outside tap is a Cancel, which pauses the App like upstream's Cancel.
        onDismissRequest = onCancel,
        title = {
            Text(stringResource(if (isMessage) Res.string.mcp_app_confirm_message else Res.string.mcp_app_confirm_tool))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val warning = buildString {
                    append(stringResource(Res.string.mcp_app_action_warning))
                    if (isMessage) append(' ').append(stringResource(Res.string.mcp_app_message_tool_context))
                    append(' ').append(stringResource(Res.string.mcp_app_cancel_pauses))
                }
                Text(warning, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = when (action) {
                        is McpAppAction.ToolCall -> stringResource(Res.string.mcp_app_tool_line, action.serverName, action.toolName)
                        is McpAppAction.Message -> action.serverName
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CODE_BLOCK_SHAPE)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(
                        text = when (action) {
                            is McpAppAction.ToolCall -> action.argumentsText
                            is McpAppAction.Message -> action.text
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                // Never for a message: it starts a model response that may use the agent's tools.
                if (action is McpAppAction.ToolCall) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = dontAskAgain,
                                enabled = armed,
                                role = Role.Checkbox,
                                onValueChange = { dontAskAgain = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AdaptiveCheckbox(checked = dontAskAgain, onCheckedChange = null, enabled = armed)
                        Text(
                            text = stringResource(Res.string.mcp_app_dont_ask_again, action.toolName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdaptiveButton(onClick = { onApprove(dontAskAgain) }, enabled = armed, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (isMessage) Res.string.mcp_app_send_message else Res.string.mcp_app_run_tool))
                }
                AdaptiveOutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.mcp_app_stop), textAlign = TextAlign.Center)
                }
                AdaptiveTextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        },
    )
}

/**
 * Asked once per server, the first time one of its Apps wants the location. This is about the
 * device capability, not about running the App. Back or an outside tap is "Don't allow".
 */
@Composable
fun McpAppLocationDialog(request: McpAppLocationRequest, onAnswer: (allow: Boolean) -> Unit) {
    val serverName = request.serverName
    // Allow arms only after the dialog has been up a moment: an App can time its request so a tap
    // meant for the page lands here, and Allow is remembered for the server.
    var armed by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request) {
        delay(APPROVE_ARM_DELAY_MS)
        armed = true
    }
    AdaptiveAlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(Res.string.mcp_app_location_title, serverName)) },
        text = { Text(stringResource(Res.string.mcp_app_location_text, serverName)) },
        confirmButton = {
            AdaptiveButton(onClick = { onAnswer(true) }, enabled = armed) {
                Text(stringResource(Res.string.mcp_app_location_allow))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.mcp_app_location_deny)) }
        },
    )
}

/**
 * "Send message from app?" for a legacy MCP-UI resource's action: the exact text, Send once armed,
 * or Cancel (also Back and an outside tap). Web sends these without asking.
 */
@Composable
fun LegacyUiMessageDialog(text: String, onAnswer: (send: Boolean) -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(APPROVE_ARM_DELAY_MS)
        armed = true
    }
    AdaptiveAlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(Res.string.mcp_app_confirm_message)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.mcp_app_action_warning) + " " +
                        stringResource(Res.string.mcp_app_message_tool_context),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CODE_BLOCK_SHAPE)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            }
        },
        confirmButton = {
            AdaptiveButton(onClick = { onAnswer(true) }, enabled = armed) {
                Text(stringResource(Res.string.mcp_app_send_message))
            }
        },
        dismissButton = {
            AdaptiveTextButton(onClick = { onAnswer(false) }) { Text(stringResource(Res.string.cancel)) }
        },
    )
}

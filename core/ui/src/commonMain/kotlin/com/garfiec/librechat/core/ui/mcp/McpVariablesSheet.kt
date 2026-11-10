package com.garfiec.librechat.core.ui.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.garfiec.librechat.core.model.mcp.McpUserVar
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.SecretTextField
import com.garfiec.librechat.core.ui.components.VerbatimKeyboardOptions
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.mcp_vars_body
import com.garfiec.librechat.core.ui.resources.mcp_vars_cancel
import com.garfiec.librechat.core.ui.resources.mcp_vars_connect
import com.garfiec.librechat.core.ui.resources.mcp_vars_keep
import com.garfiec.librechat.core.ui.resources.mcp_vars_keep_hint
import com.garfiec.librechat.core.ui.resources.mcp_vars_not_set
import com.garfiec.librechat.core.ui.resources.mcp_vars_remove
import com.garfiec.librechat.core.ui.resources.mcp_vars_remove_confirm
import com.garfiec.librechat.core.ui.resources.mcp_vars_remove_failed
import com.garfiec.librechat.core.ui.resources.mcp_vars_save_connect
import com.garfiec.librechat.core.ui.resources.mcp_vars_save_failed
import com.garfiec.librechat.core.ui.resources.mcp_vars_set
import com.garfiec.librechat.core.ui.resources.mcp_vars_title
import com.garfiec.librechat.core.ui.text.configHtmlToAnnotatedString
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The values an MCP server needs from the user ([vars], its `customUserVars`), in web's form. Saved
 * values never come back from the server, so each field starts empty and shows only whether it is set
 * ([loadFlags]; null when that read failed). An empty field keeps what's saved. [onSave] gets the
 * typed values (empty when the user only connects) and closes the sheet on true; it then reconnects.
 * [onRemove] follows an in-place confirm, since the server's remove also revokes the server's OAuth
 * tokens. While a save or removal is out nothing closes the sheet; while a secret is shown the sheet is
 * `secure`, and ON_PAUSE re-masks (ON_STOP is too late for the iOS app-switcher snapshot).
 */
@Composable
fun McpVariablesSheet(
    serverLabel: String,
    vars: List<McpUserVar>,
    loadFlags: suspend () -> Map<String, Boolean>?,
    onSave: suspend (Map<String, String>) -> Boolean,
    onRemove: suspend () -> Boolean,
    onDismiss: () -> Unit,
) {
    var revealed by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var flags by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    val currentLoadFlags by rememberUpdatedState(loadFlags)
    LaunchedEffect(Unit) { flags = currentLoadFlags() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { revealed = emptySet() }
    AdaptiveModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        interceptDismiss = busy,
        modifier = Modifier.clearFocusOnTap(),
        skipPartiallyExpanded = true,
        dragHandle = { LowProfileDragHandle() },
        secure = revealed.isNotEmpty(),
    ) {
        McpVariablesSheetContent(
            serverLabel = serverLabel,
            vars = vars,
            flags = flags,
            revealed = revealed,
            onRevealedChange = { key, show -> revealed = if (show) revealed + key else revealed - key },
            onBusyChange = { busy = it },
            onSave = onSave,
            onRemove = onRemove,
            onDismiss = onDismiss,
        )
    }
}

/** The sheet's body, apart from the sheet so previews can render it. */
@Composable
fun McpVariablesSheetContent(
    serverLabel: String,
    vars: List<McpUserVar>,
    flags: Map<String, Boolean>?,
    revealed: Set<String>,
    onRevealedChange: (String, Boolean) -> Unit,
    onBusyChange: (Boolean) -> Unit,
    onSave: suspend (Map<String, String>) -> Boolean,
    onRemove: suspend () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = remember { mutableStateMapOf<String, String>() }
    var saving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    val scope = rememberCoroutineScope()
    val busy = saving || removing
    val typed = values.filterValues { it.isNotBlank() }
    val anySet = flags?.values?.any { it } == true
    // With the flags unknown the server decides; a connect with values still owed fails there and says so.
    val canSave = flags == null || vars.all { flags[it.key] == true || !values[it.key].isNullOrBlank() }

    fun run(onFlag: (Boolean) -> Unit, failure: StringResource, action: suspend () -> Boolean) {
        scope.launch {
            onFlag(true)
            onBusyChange(true)
            error = null
            val ok = action()
            onFlag(false)
            onBusyChange(false)
            if (ok) onDismiss() else error = failure
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.mcp_vars_title, serverLabel),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.mcp_vars_body, serverLabel) +
                if (anySet) " " + stringResource(Res.string.mcp_vars_keep_hint) else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        vars.forEach { variable ->
            VariableField(
                variable = variable,
                isSet = flags?.get(variable.key),
                value = values[variable.key].orEmpty(),
                revealed = variable.key in revealed,
                enabled = !busy,
                onValueChange = { values[variable.key] = it },
                onRevealedChange = { onRevealedChange(variable.key, it) },
            )
        }
        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (confirmRemove) {
            Text(
                text = stringResource(Res.string.mcp_vars_remove_confirm, serverLabel),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        // Each button is its own flow item, so on a narrow screen the primary one drops to a line of its own.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (confirmRemove) {
                AdaptiveOutlinedButton(onClick = { confirmRemove = false }, enabled = !busy) {
                    Text(stringResource(Res.string.mcp_vars_keep))
                }
                AdaptiveButton(
                    onClick = { run({ removing = it }, Res.string.mcp_vars_remove_failed, onRemove) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    if (removing) ButtonProgress()
                    Text(stringResource(Res.string.mcp_vars_remove))
                }
            } else {
                if (anySet) {
                    AdaptiveTextButton(
                        onClick = { confirmRemove = true },
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(Res.string.mcp_vars_remove))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
                AdaptiveOutlinedButton(onClick = onDismiss, enabled = !busy) {
                    Text(stringResource(Res.string.mcp_vars_cancel))
                }
                AdaptiveButton(
                    onClick = { run({ saving = it }, Res.string.mcp_vars_save_failed) { onSave(typed) } },
                    enabled = !busy && canSave,
                ) {
                    if (saving) ButtonProgress()
                    Text(stringResource(if (typed.isEmpty()) Res.string.mcp_vars_connect else Res.string.mcp_vars_save_connect))
                }
            }
        }
    }
}

@Composable
private fun VariableField(
    variable: McpUserVar,
    isSet: Boolean?,
    value: String,
    revealed: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onRevealedChange: (Boolean) -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val description = remember(variable.description, linkColor) {
        variable.description.takeIf { it.isNotBlank() }?.let {
            configHtmlToAnnotatedString(
                it,
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = variable.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isSet != null) {
                Text(
                    text = stringResource(if (isSet) Res.string.mcp_vars_set else Res.string.mcp_vars_not_set),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (variable.sensitive) {
            SecretTextField(
                value = value,
                onValueChange = onValueChange,
                revealed = revealed,
                onRevealedChange = onRevealedChange,
                enabled = enabled,
                label = { Text(variable.title) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            AdaptiveOutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                label = { Text(variable.title) },
                singleLine = true,
                keyboardOptions = VerbatimKeyboardOptions,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ButtonProgress() {
    AdaptiveCircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
}

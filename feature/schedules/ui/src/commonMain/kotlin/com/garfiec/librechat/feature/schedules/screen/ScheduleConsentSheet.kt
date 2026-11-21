package com.garfiec.librechat.feature.schedules.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat
import com.garfiec.librechat.core.model.schedule.ScheduleMcpConsentState
import com.garfiec.librechat.core.model.schedule.ScheduledMcpCredentialMode
import com.garfiec.librechat.core.model.schedule.ScheduledMcpTarget
import com.garfiec.librechat.core.ui.components.AdaptiveButton
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveCircularProgressIndicator
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.schedules.resources.Res
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_active
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_audience
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_boundary
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_changed
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_confirm
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_confirmed
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_deadline
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_error
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_expired
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_hours
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_hours_range
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_issuer
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_load_error
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_missing
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_mode_anonymous
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_mode_browser_bearer
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_mode_renewable_obo
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_mode_resource_bearer
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_mode_static
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_mode_stored_oauth
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_retry
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_revoke
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_revoked
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_scopes
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_title
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_tools
import com.garfiec.librechat.feature.schedules.resources.schedule_consent_unsupported
import com.garfiec.librechat.feature.schedules.viewmodel.ConsentMessage
import com.garfiec.librechat.feature.schedules.viewmodel.ScheduleConsentSheetState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/** What the consent sheet can ask of its owner. */
internal class ScheduleConsentActions(
    val onDismiss: () -> Unit = {},
    val onHoursChange: (String) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onRevoke: () -> Unit = {},
    val onReload: () -> Unit = {},
)

/**
 * "Scheduled MCP authorization" (v0.8.9-rc1, web `SidePanel/Schedules/Consent.tsx`): the schedule's
 * consent status, the servers and tools it covers, and Confirm / Revoke.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleConsentSheet(state: ScheduleConsentSheetState, actions: ScheduleConsentActions) {
    AdaptiveModalBottomSheet(onDismissRequest = actions.onDismiss, modifier = Modifier.clearFocusOnTap()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(stringResource(Res.string.schedule_consent_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = state.scheduleName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(Res.string.schedule_consent_boundary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConsentBody(state, actions)
            state.message?.let { ConsentMessageText(it) }
        }
    }
}

@Composable
private fun ConsentBody(state: ScheduleConsentSheetState, actions: ScheduleConsentActions) {
    val view = state.view
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            view == null && state.isLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                AdaptiveCircularProgressIndicator()
            }
            view == null -> {
                Text(
                    text = stringResource(
                        if (state.loadFailed) Res.string.schedule_consent_load_error else Res.string.schedule_consent_error,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                AdaptiveOutlinedButton(onClick = actions.onReload) { Text(stringResource(Res.string.schedule_consent_retry)) }
            }
            else -> {
                ConsentStatusRow(view.state)
                view.expiresAtMs?.let { ms ->
                    Text(
                        text = stringResource(Res.string.schedule_consent_deadline, ms.asConsentDeadline(LocalDateTimeFormat.current)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                view.targets.forEach { ConsentTargetCard(it) }
                if (state.showsConfirm) {
                    AdaptiveOutlinedTextField(
                        value = state.hoursText,
                        onValueChange = actions.onHoursChange,
                        label = { Text(stringResource(Res.string.schedule_consent_hours)) },
                        supportingText = state.maxLifetimeHours?.let { max ->
                            { Text(stringResource(Res.string.schedule_consent_hours_range, max)) }
                        },
                        isError = state.validHours == null,
                        singleLine = true,
                        enabled = !state.isWriting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AdaptiveButton(
                        onClick = actions.onConfirm,
                        enabled = state.canSubmitConfirm,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.schedule_consent_confirm)) }
                }
                if (state.showsRevoke) {
                    AdaptiveOutlinedButton(
                        onClick = actions.onRevoke,
                        enabled = !state.isWriting && !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(Res.string.schedule_consent_revoke)) }
                }
            }
        }
    }
}

@Composable
private fun ConsentStatusRow(consentState: String) {
    val colors = statusColors()
    val (label, color) = when (consentState) {
        ScheduleMcpConsentState.ACTIVE -> Res.string.schedule_consent_active to colors.success
        ScheduleMcpConsentState.EXPIRED -> Res.string.schedule_consent_expired to colors.warning
        ScheduleMcpConsentState.REVOKED -> Res.string.schedule_consent_revoked to colors.neutral
        ScheduleMcpConsentState.CHANGED -> Res.string.schedule_consent_changed to colors.warning
        ScheduleMcpConsentState.UNSUPPORTED -> Res.string.schedule_consent_unsupported to colors.error
        // MISSING, and any state a newer server adds: the safe reading is "not authorized".
        else -> Res.string.schedule_consent_missing to colors.warning
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusDot(color)
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}

@Composable
private fun ConsentTargetCard(target: ScheduledMcpTarget) {
    val resource = target.resource
    val detail = MaterialTheme.typography.bodySmall
    val detailColor = MaterialTheme.colorScheme.onSurfaceVariant
    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(resource.serverName, style = MaterialTheme.typography.titleSmall)
            Text(resource.url, style = detail, color = detailColor)
            Text(credentialModeLabel(resource.credentialMode), style = detail, color = detailColor)
            resource.issuer?.let { Text(stringResource(Res.string.schedule_consent_issuer, it), style = detail, color = detailColor) }
            resource.audience?.let {
                Text(stringResource(Res.string.schedule_consent_audience, it), style = detail, color = detailColor)
            }
            if (resource.scopes.isNotEmpty()) {
                Text(
                    stringResource(Res.string.schedule_consent_scopes, resource.scopes.joinToString(", ")),
                    style = detail,
                    color = detailColor,
                )
            }
            target.permittedTools.forEach { selection ->
                Text(
                    stringResource(Res.string.schedule_consent_tools, selection.agentId, selection.tools.joinToString(", ")),
                    style = detail,
                    color = detailColor,
                )
            }
        }
    }
}

@Composable
private fun ConsentMessageText(message: ConsentMessage) {
    val (text, color) = when (message) {
        ConsentMessage.CONFIRMED -> Res.string.schedule_consent_confirmed to MaterialTheme.colorScheme.onSurface
        ConsentMessage.REVOKED -> Res.string.schedule_consent_revoked to MaterialTheme.colorScheme.onSurface
        ConsentMessage.FAILED -> Res.string.schedule_consent_error to MaterialTheme.colorScheme.error
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = color)
}

/** A wire `credentialMode` in words; one a newer server adds shows as itself. */
@Composable
private fun credentialModeLabel(mode: String): String =
    credentialModeResource(mode)?.let { stringResource(it) } ?: mode

private fun credentialModeResource(mode: String): StringResource? = when (mode) {
    ScheduledMcpCredentialMode.STORED_OAUTH -> Res.string.schedule_consent_mode_stored_oauth
    ScheduledMcpCredentialMode.BROWSER_BEARER -> Res.string.schedule_consent_mode_browser_bearer
    ScheduledMcpCredentialMode.RENEWABLE_OBO -> Res.string.schedule_consent_mode_renewable_obo
    ScheduledMcpCredentialMode.RESOURCE_BEARER -> Res.string.schedule_consent_mode_resource_bearer
    ScheduledMcpCredentialMode.STATIC -> Res.string.schedule_consent_mode_static
    ScheduledMcpCredentialMode.ANONYMOUS -> Res.string.schedule_consent_mode_anonymous
    else -> null
}

/**
 * The consent view's `expiresAtMs` is epoch MILLISECONDS, unlike the schedule's ISO stamps that
 * [asScheduleTimestamp] reads, so it goes through an [Instant] rather than the ISO parser.
 */
internal fun Long.asConsentDeadline(format: ResolvedDateTimeFormat): String =
    format.formatAbsolute(Instant.fromEpochMilliseconds(this))

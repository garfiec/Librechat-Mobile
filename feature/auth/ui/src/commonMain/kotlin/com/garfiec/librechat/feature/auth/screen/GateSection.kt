package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.auth.DEFAULT_GATE_CLIENT_ID
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.gate_body
import com.garfiec.librechat.feature.auth.resources.gate_client_id_label
import com.garfiec.librechat.feature.auth.resources.gate_failed_body
import com.garfiec.librechat.feature.auth.resources.gate_page_close
import com.garfiec.librechat.feature.auth.resources.gate_page_instead
import com.garfiec.librechat.feature.auth.resources.gate_redirect_body
import com.garfiec.librechat.feature.auth.resources.gate_redirect_title
import com.garfiec.librechat.feature.auth.resources.gate_refused_title
import com.garfiec.librechat.feature.auth.resources.gate_setup_guide
import com.garfiec.librechat.feature.auth.resources.gate_title_authelia
import com.garfiec.librechat.feature.auth.resources.gate_title_generic
import com.garfiec.librechat.feature.auth.resources.gate_unknown_client_body
import com.garfiec.librechat.feature.auth.resources.gate_unknown_client_title
import com.garfiec.librechat.feature.auth.viewmodel.GateProblem
import com.garfiec.librechat.feature.auth.viewmodel.GateUi
import org.jetbrains.compose.resources.stringResource

/** Where admins find the Authelia client and proxy configuration the browser sign-in needs. */
internal const val GATE_SETUP_GUIDE_URL =
    "https://github.com/garfiec/Librechat-Mobile/blob/develop/FAQ.md#can-i-use-switchboard-behind-authelia"

/**
 * The server sits behind a sign-in gate: what it is, what's wrong when the sign-in failed, and the
 * fallback to the gate's own page when the browser sign-in can't work.
 */
@Composable
internal fun GateCard(gate: GateUi, onPageSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val problem = gate.problem
    val colors = if (problem == null) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    AdaptiveCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("server_url_gate")
            .semantics { liveRegion = LiveRegionMode.Polite },
        colors = colors,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = gateTitle(gate),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(text = gateBody(gate), style = MaterialTheme.typography.bodyMedium)
            val uriHandler = LocalUriHandler.current
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (problem is GateProblem.UnknownClient || problem is GateProblem.RedirectNotRegistered) {
                    AdaptiveTextButton(onClick = { uriHandler.openUri(GATE_SETUP_GUIDE_URL) }) {
                        Text(stringResource(Res.string.gate_setup_guide))
                    }
                }
            }
            if (problem != null && gate.browserSignIn == true) {
                AdaptiveTextButton(onClick = onPageSignIn, modifier = Modifier.testTag("server_url_gate_page")) {
                    Text(stringResource(Res.string.gate_page_instead))
                }
            } else {
                Spacer(Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
private fun gateTitle(gate: GateUi): String = when (gate.problem) {
    null -> stringResource(if (gate.isAuthelia) Res.string.gate_title_authelia else Res.string.gate_title_generic)
    is GateProblem.UnknownClient -> stringResource(Res.string.gate_unknown_client_title)
    is GateProblem.RedirectNotRegistered -> stringResource(Res.string.gate_redirect_title)
    is GateProblem.Refused, GateProblem.Failed -> stringResource(Res.string.gate_refused_title, gate.portalHost)
}

@Composable
private fun gateBody(gate: GateUi): String = when (val problem = gate.problem) {
    null -> stringResource(Res.string.gate_body, gate.portalHost)
    is GateProblem.UnknownClient -> stringResource(Res.string.gate_unknown_client_body, gate.portalHost, problem.clientId)
    is GateProblem.RedirectNotRegistered -> stringResource(Res.string.gate_redirect_body, gate.portalHost, problem.clientId)
    is GateProblem.Refused -> problem.description ?: stringResource(Res.string.gate_failed_body)
    GateProblem.Failed -> stringResource(Res.string.gate_failed_body)
}

/** The OAuth client id field, under Advanced, once a gate has been found. */
@Composable
internal fun GateClientIdField(clientId: String, enabled: Boolean, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    AdaptiveOutlinedTextField(
        value = clientId,
        onValueChange = onChange,
        label = { Text(stringResource(Res.string.gate_client_id_label)) },
        placeholder = { Text(DEFAULT_GATE_CLIENT_ID) },
        singleLine = true,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().testTag("server_url_gate_client_id"),
    )
}

/**
 * The frame of the gate's own sign-in page, full screen over the server screen: a close button over
 * [webView], which gets the modifier that keeps it clear of the button.
 */
@Composable
fun GatePageSignInFrame(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    webView: @Composable (Modifier) -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("server_url_gate_page_view"),
    ) {
        webView(Modifier.fillMaxSize().padding(top = 48.dp))
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.gate_page_close))
        }
    }
}

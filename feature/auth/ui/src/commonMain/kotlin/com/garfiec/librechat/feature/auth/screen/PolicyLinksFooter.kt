package com.garfiec.librechat.feature.auth.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveTextButton
import com.garfiec.librechat.feature.auth.resources.Res
import com.garfiec.librechat.feature.auth.resources.privacy_policy
import com.garfiec.librechat.feature.auth.resources.terms_of_service
import com.garfiec.librechat.feature.auth.viewmodel.PolicyLinks
import org.jetbrains.compose.resources.stringResource

/**
 * The deployment's privacy policy and terms of service links, as upstream's auth footer shows them.
 * Renders nothing when the server published neither (or only unsafe ones, already dropped by
 * [PolicyLinks.from]).
 */
@Composable
internal fun PolicyLinksFooter(links: PolicyLinks, modifier: Modifier = Modifier) {
    if (links.privacyPolicyUrl == null && links.termsOfServiceUrl == null) return
    val uriHandler = LocalUriHandler.current
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        links.privacyPolicyUrl?.let { url ->
            AdaptiveTextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Text(stringResource(Res.string.privacy_policy), style = MaterialTheme.typography.bodySmall)
            }
        }
        links.termsOfServiceUrl?.let { url ->
            AdaptiveTextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Text(stringResource(Res.string.terms_of_service), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

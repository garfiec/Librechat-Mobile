package com.garfiec.librechat.shared.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.UriHandler
import com.garfiec.librechat.core.common.extensions.serverHostLabel
import com.garfiec.librechat.core.common.identity.ShareLinkAccount
import com.garfiec.librechat.core.common.identity.ShareLinkMatcher
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.garfiec.librechat.core.ui.components.AdaptiveAlertDialog
import com.garfiec.librechat.shared.resources.Res
import com.garfiec.librechat.shared.resources.shared_link_open_in_browser
import com.garfiec.librechat.shared.resources.shared_link_switch_confirm
import com.garfiec.librechat.shared.resources.shared_link_switch_message
import com.garfiec.librechat.shared.resources.shared_link_switch_title
import org.jetbrains.compose.resources.stringResource

/** Where a `/share/` link opened inside the app goes. */
sealed interface SharedLinkRoute {
    /** A link on the active account's server: the viewer opens it. */
    data class Open(val shareId: String) : SharedLinkRoute

    /**
     * A link on another signed-in account's server: the user is asked to switch to it, and the
     * viewer opens after the switch.
     */
    data class OfferSwitch(
        val url: String,
        val shareId: String,
        val accountId: String,
        val accountLabel: String,
        val serverHost: String,
    ) : SharedLinkRoute

    /** Not a shared link, or none of the user's signed-in accounts is on its server. */
    data object NotOurs : SharedLinkRoute
}

/**
 * Where [url] goes, given the roster [entries], the [activeId] account and the accounts that still
 * hold a session ([liveIds]). The active account is tried first; other signed-in accounts by
 * recency. An account whose session has expired is never offered (the browser gets the link), and
 * nothing is offered while signed out: the viewer reads a link as a signed-in user.
 */
internal fun sharedLinkRoute(
    url: String,
    entries: List<AccountEntry>,
    activeId: String?,
    liveIds: Set<String>,
): SharedLinkRoute {
    if (ShareLinkMatcher.parse(url) == null) return SharedLinkRoute.NotOurs
    val candidates = buildList {
        entries.firstOrNull { it.accountId == activeId && it.accountId in liveIds }?.let(::add)
        entries.filter { it.accountId != activeId && it.accountId in liveIds }
            .sortedByDescending { it.lastActiveAt }
            .forEach(::add)
    }
    val target = ShareLinkMatcher.match(url, candidates.map { ShareLinkAccount(it.accountId, it.serverUrl) })
        ?: return SharedLinkRoute.NotOurs
    if (target.accountId == activeId) return SharedLinkRoute.Open(target.shareId)
    val entry = candidates.first { it.accountId == target.accountId }
    return SharedLinkRoute.OfferSwitch(
        url = url,
        shareId = target.shareId,
        accountId = entry.accountId,
        accountLabel = entry.displayLabel,
        serverHost = entry.serverUrl.serverHostLabel(),
    )
}

/**
 * The app's [UriHandler]: a tapped `/share/` link goes to [onSharedLink], which opens it in the
 * viewer when it is on a signed-in account's server (or hands it back to [platform]); every other
 * URI goes straight to [platform]. Only the syntax is checked here, so non-share links never wait.
 */
internal class SharedLinkUriHandler(
    private val platform: UriHandler,
    private val onSharedLink: (url: String) -> Unit,
) : UriHandler {
    override fun openUri(uri: String) {
        if (ShareLinkMatcher.parse(uri) == null) platform.openUri(uri) else onSharedLink(uri)
    }
}

/** Asks before switching accounts to open a shared link that lives on another account's server. */
@Composable
internal fun SharedLinkSwitchDialog(
    offer: SharedLinkRoute.OfferSwitch,
    onSwitch: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.shared_link_switch_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.shared_link_switch_message, offer.serverHost, offer.accountLabel),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onSwitch) {
                Text(stringResource(Res.string.shared_link_switch_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenInBrowser) {
                Text(stringResource(Res.string.shared_link_open_in_browser))
            }
        },
    )
}

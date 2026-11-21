package com.garfiec.librechat.feature.auth.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.auth.DEFAULT_GATE_CLIENT_ID
import com.garfiec.librechat.core.model.server.HeaderRejection
import com.garfiec.librechat.core.model.server.HeaderWriteFailure
import com.garfiec.librechat.core.ui.components.CustomHeaderRow

/**
 * A rejected header row, as the row index plus the machine-readable reason. Deliberately not a
 * message: `:core:network` has no string resources and this module's strings live in its own
 * `composeResources`, so the mapping to text belongs in the composable.
 */
@Immutable
data class HeaderFieldError(val index: Int, val reason: HeaderRejection)

/** A server the roster's accounts are on, as the "Your servers" shortcut shows it. */
@Immutable
data class KnownServer(
    val url: String,
    /** The address without its scheme; the path stays, so two subpath servers on one host differ. */
    val label: String,
    val host: String,
    val accountCount: Int,
)

@Immutable
data class ServerUrlUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isValidated: Boolean = false,
    val showHttpWarning: Boolean = false,
    val showAdvanced: Boolean = false,
    val customHeaders: List<CustomHeaderRow> = emptyList(),
    val headerError: HeaderFieldError? = null,
    /**
     * The stored headers could not be read, so [customHeaders] is empty because nothing loaded, not
     * because nothing is configured. Rendered as a warning: an unlabelled empty editor reads as the
     * credential having been lost.
     */
    val headersLoadFailed: Boolean = false,
    /**
     * Why the typed headers could not be persisted, or null. Connect stops on any of these rather
     * than probing, because a probe without the credential fails as an opaque connection error and
     * sends the user to fix a URL that was never the problem.
     */
    val headersSaveFailure: HeaderWriteFailure? = null,
    /** Servers of the accounts on this device, most recently used first. */
    val knownServers: List<KnownServer> = emptyList(),
    /** The server sits behind a sign-in gate (Authelia) that turned Connect away; null otherwise. */
    val gate: GateUi? = null,
)

/** A forward-auth gate in front of the server, as the server screen shows it. */
@Immutable
data class GateUi(
    /** The gate's sign-in host, e.g. `auth.example.com`. */
    val portalHost: String,
    /** Authelia names itself in its redirect (`rm=`); another gate gets neutral wording. */
    val isAuthelia: Boolean,
    /** Whether the gate offers the browser sign-in; null while that is being checked. */
    val browserSignIn: Boolean? = null,
    /** The OAuth client id typed under Advanced; blank means [DEFAULT_GATE_CLIENT_ID]. */
    val clientId: String = "",
    val problem: GateProblem? = null,
    val busy: Boolean = false,
    /** The gate's own sign-in page is open in the app. */
    val pageSignIn: Boolean = false,
    /** Where the page sign-in starts and which server it ends on; the screen only reads these. */
    val portalUrl: String = "",
    val serverUrl: String = "",
)

sealed interface GateProblem {
    /** The gate's OAuth provider has no usable public client by that id. */
    data class UnknownClient(val clientId: String) : GateProblem

    /** The client exists but doesn't list the app's callback address. */
    data class RedirectNotRegistered(val clientId: String) : GateProblem

    /** Any other refusal, in the provider's own words when it gave some. */
    data class Refused(val description: String?) : GateProblem

    data object Failed : GateProblem
}

package com.garfiec.librechat.core.brand

/**
 * TCHAT: every Tchat-specific constant lives here, and nowhere else. Upstream files may reference
 * these but must never inline a Tchat string of their own — see TCHAT.md for the full convention.
 */
object TchatBrand {
    /** The one server this build talks to. See [ServerPinning] for how it reaches [ServerDataStore]. */
    const val SERVER_URL: String = "https://chat.tensorgrid.space"

    const val APP_NAME: String = "Tchat"

    /** Matches `registration.socialLogins: ['openid']` in deploy/tchat/librechat.yaml. */
    const val OIDC_PROVIDER: String = "openid"

    /**
     * Shown only until the server's own `openidLabel` (from `OPENID_BUTTON_LABEL`) resolves, or if
     * it's ever absent. deploy/tchat/env.example already sets `OPENID_BUTTON_LABEL=Continue with
     * TensorGrid`, so in practice the server-supplied label wins and this is just a safety net.
     */
    const val OIDC_FALLBACK_LABEL: String = "Continue with TensorGrid"

    /**
     * Whether to show the generic Switchboard settings for talking to an arbitrary,
     * self-administered LibreChat server: custom per-request headers (for access gateways like
     * Cloudflare Access — Tchat's nginx has none), LibreChat's own programmatic API keys
     * (`/api/api-keys`, gated behind `REMOTE_AGENTS`), and per-endpoint user-provided provider
     * keys. False for Tchat: every model call is routed through the broker with a fixed,
     * server-side credential — see `Tchat/deploy/tchat/README.md`'s "Why users cannot switch
     * providers" — so there is nothing for a user to configure here, and showing the controls
     * would look like a real capability they don't actually have. Gates:
     * `AccountSettingsScreen`'s "Server connection" / "API Keys" / "Provider API Keys" rows,
     * `ServerUrlScreen`'s "Custom request headers" section, and `DataSettingsScreen`'s
     * "Revoke API keys" action.
     */
    const val EXPOSE_PROVIDER_BACKEND_SETTINGS: Boolean = false

    /**
     * Whether the Android build checks GitHub Releases for a newer version. False for Tchat: the
     * check reads Switchboard's own releases (`GitHubReleasesApi.REPO_URL`), so it would offer
     * Switchboard APKs — a different app id that installs alongside Tchat rather than updating
     * it — and Tchat has no public release channel of its own yet. Passed as
     * `AppUpdateRepositoryImpl.isSupported` on Android, the same switch iOS already sets false,
     * which hides Settings' "Check for updates" rows, the daily check and the chat update banner.
     * The installed version's release notes are a separate surface and stay.
     */
    const val APP_UPDATE_CHECK: Boolean = false
}

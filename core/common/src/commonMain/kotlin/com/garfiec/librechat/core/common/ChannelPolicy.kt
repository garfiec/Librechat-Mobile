package com.garfiec.librechat.core.common

/**
 * What differs between distribution channels. Code that needs to behave differently on a store
 * build reads a field here, never [DistributionChannel] directly, so every channel difference is
 * listed in one place.
 */
class ChannelPolicy(channel: DistributionChannel) {
    private val openSource = channel == DistributionChannel.OPEN_SOURCE

    /**
     * Whether the app may check GitHub for a newer release and offer it. Only the open-source
     * channel: a store updates its own installs, and Play policy forbids an app updating itself
     * outside Play. iOS has no self-update path on any channel, so its binding forces this off.
     */
    val selfUpdateCheck: Boolean = openSource

    /** Whether Settings offers the GitHub release notes. A store's listing carries its own. */
    val releaseNotes: Boolean = openSource

    /**
     * What "show website icons" means before the user has chosen. `null` keeps the choice open,
     * which shows icons off and asks on first use. The open-source channels do that because the
     * icons are fetched from a third-party favicon service, which sees every host a result links to.
     */
    val siteIconsDefault: Boolean? = if (openSource) null else true
}

package com.garfiec.librechat.core.common

/**
 * What differs between distribution channels. Code that needs to behave differently on a store
 * build reads a field here, never [DistributionChannel] directly, so every channel difference is
 * listed in one place. Preference defaults that differ by channel are declared with [pick] in the
 * preference registry (`AppPreferences` in `:core:data`).
 */
class ChannelPolicy(private val channel: DistributionChannel) {
    /**
     * Whether the app may check GitHub for a newer release and offer it. Only the open-source
     * channel: a store updates its own installs, and Play policy forbids an app updating itself
     * outside Play. iOS has no self-update path on any channel, so its binding forces this off.
     */
    val selfUpdateCheck: Boolean = pick(openSource = true, playStore = false, appStore = false)

    /** Whether Settings offers the GitHub release notes. A store's listing carries its own. */
    val releaseNotes: Boolean = pick(openSource = true, playStore = false, appStore = false)

    /**
     * Whether opening an MCP App from a server for the first time asks the user first. An App is
     * web code an MCP server's author supplies at run time, which may load more code from hosts it
     * declares, so the app does not run one without an explicit yes per account and server. The
     * same on every channel today; a field so a channel can differ without touching the chat.
     */
    val mcpAppsNeedConsent: Boolean = pick(openSource = true, playStore = true, appStore = true)

    /**
     * The value for this build's channel. Every channel is named, so adding one fails to compile
     * until each caller decides its value.
     *
     * For the fields above and the preference registry's `perChannel` defaults and migration
     * scopes only. A feature that calls it directly is branching on the channel at a call site,
     * which is what this class exists to prevent: add a field here instead.
     */
    fun <T> pick(openSource: T, playStore: T, appStore: T): T = when (channel) {
        DistributionChannel.OPEN_SOURCE -> openSource
        DistributionChannel.PLAY_STORE -> playStore
        DistributionChannel.APP_STORE -> appStore
    }
}

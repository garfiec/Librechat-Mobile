package com.garfiec.librechat.core.data.datastore

/**
 * Registry of device-wide preferences read through [PreferenceStore]. A new preference is one entry
 * here plus its line in [all], which the registry test checks for duplicate keys and prints as a
 * table of defaults per channel.
 *
 * Key names are persisted: renaming one drops every existing user's choice.
 */
object AppPreferences {
    /**
     * Whether web-search results may load site icons from external servers (Google's favicon
     * service, or an icon URL the result carries). Null means unchosen: icons stay off and the chat
     * asks on first use. The open-source channel asks because the favicon service sees every host a
     * result links to.
     */
    val SiteIcons = boolPreference(
        "show_site_icons",
        default = perChannel<Boolean?>(openSource = null, playStore = true, appStore = true),
    )

    val all: List<PreferenceSpec<*, *>> = listOf(SiteIcons)
}

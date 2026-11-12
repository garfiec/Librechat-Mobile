package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.data.prefetch.PrefetchDepth

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

    /**
     * Whether recent and pinned conversations are fetched ahead of time, so they open instantly and
     * read offline. Device-wide, not per account, like the rest of the prefetch settings: they are
     * about the device's bandwidth and battery.
     */
    val PrefetchEnabled = boolPreference("prefetch_enabled", default = always(true))

    /** Nested under [PrefetchEnabled]. Off by default: images are far heavier than the text they accompany. */
    val PrefetchAttachments = boolPreference("prefetch_attachments", default = always(false))

    /** Lets [PrefetchEnabled] run on metered connections. On by default because text alone is light. */
    val PrefetchOnMetered = boolPreference("prefetch_on_metered", default = always(true))

    /** How many recent conversations [PrefetchEnabled] keeps warm. Clamped on read: it is each pass's request count. */
    val PrefetchDepthSetting = intPreference(
        "prefetch_depth",
        default = always(PrefetchDepth.DEFAULT),
        decode = { it.coerceIn(PrefetchDepth.RANGE) },
    )

    /**
     * Whether the Search screen shows its power-user options (match case, whole word, regex, and
     * role/model/date/archive filters). Off by default: the plain search box is what most people want.
     */
    val AdvancedSearch = boolPreference("advanced_search", default = always(false))

    /**
     * Whether Compare's side-by-side panes scroll together, each at the rate that keeps their
     * corresponding parts level. Toggled from the link between the panes.
     */
    val CompareSyncScroll = boolPreference("compare_sync_scroll", default = always(true))

    /**
     * Whether a new chat opens on the user's last-used model rather than the server's prioritized
     * model spec. Off by default, as on web. On, a prioritized spec opens a new chat only when
     * last-used can't, the way a soft-default spec does. Mobile-only.
     */
    val PreferLastUsedModel = boolPreference("prefer_last_used_model", default = always(false))

    /** Which search the chat's web-search control tries first ([WebSearchPreference]). Mobile-only. */
    val WebSearchFirst = stringPreference(
        "web_search_first",
        default = always(WebSearchPreference.LIBRECHAT_FIRST),
        decode = WebSearchPreference::fromStorage,
        encode = { it.storageValue },
    )

    /**
     * Drawer destinations pinned as rows under Workspace, in the user's order: their stable ids,
     * comma-separated. Device-wide, not per account. The key and the ids are persisted: never rename.
     * Ids this build doesn't know are kept here and skipped by the reader.
     */
    val DrawerPinned = stringListPreference("drawer_pinned_destinations")

    /**
     * Remembered answers about flagged libraries (unmaintained, deprecated or vulnerable) in React
     * artifacts, as `allow|<library>|<advisory>` / `decline|…` entries. Device-wide. One key for all
     * of them so a change is one write. Encoded and read only by [ArtifactLibraryConsentStore].
     */
    val ArtifactLibraryAnswers = stringListPreference("artifact_library_answers")

    /**
     * Whether a long user message renders collapsed to a preview with Show more (web's
     * `collapseLongUserMessages`, Settings › Chat, on by default as on web).
     */
    val CollapseLongUserMessages = boolPreference("collapse_long_user_messages", default = always(true))

    /**
     * Whether large images are scaled down before upload (web's `clientImageResize`, on by default as
     * on web). An admin's `fileConfig.clientImageResize.enabled` overrides it either way.
     */
    val ClientImageResize = boolPreference("client_image_resize", default = always(true))

    /**
     * Whether a reply that finishes while the user is elsewhere is announced (a system notification
     * in the background, a snackbar in another chat). Device-wide. On by default, unlike web: it
     * only takes effect once the user has granted notification permission, which they are asked for.
     */
    val ReplyNotifications = boolPreference("reply_notifications", default = always(true))

    /** Notify when a scheduled chat runs, or the server pauses one. On by default, as Replies. */
    val ScheduledChatNotifications = boolPreference("scheduled_chat_notifications", default = always(true))

    /**
     * The in-context notification asks the user has already answered (`NotificationAskTrigger`
     * names), so none is offered twice. Device-wide.
     */
    val NotificationAsksAnswered = stringListPreference("notification_asks_answered")

    /**
     * How an artifact opens: Automatic (beside the chat, as a sheet or full screen, by the window),
     * always a bottom sheet, or always full screen. Mobile-only.
     */
    val ArtifactDisplay = stringPreference(
        "artifact_display_mode",
        default = always(ArtifactDisplayMode.AUTOMATIC),
        decode = ArtifactDisplayMode::fromString,
        encode = ArtifactDisplayMode::toStorageString,
    )

    /**
     * The artifact's share of the width when it stands beside the chat, set by dragging the divider;
     * one value for every window.
     */
    val ArtifactSplitFraction = floatPreference(
        "artifact_split_fraction",
        default = always(0.5f),
        decode = { it.takeIf { fraction -> fraction in 0f..1f } },
    )

    val all: List<PreferenceSpec<*, *>> =
        listOf(
            SiteIcons,
            PrefetchEnabled,
            PrefetchAttachments,
            PrefetchOnMetered,
            PrefetchDepthSetting,
            AdvancedSearch,
            CompareSyncScroll,
            PreferLastUsedModel,
            WebSearchFirst,
            DrawerPinned,
            ArtifactLibraryAnswers,
            CollapseLongUserMessages,
            ClientImageResize,
            ReplyNotifications,
            ScheduledChatNotifications,
            NotificationAsksAnswered,
            ArtifactDisplay,
            ArtifactSplitFraction,
        )
}

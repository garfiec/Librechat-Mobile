package com.garfiec.librechat.core.model.notifications

enum class NotificationPermissionStatus {
    /** Notifications are shown (and, on Android, the Replies channel isn't blocked). */
    Allowed,

    /** Not shown, and the app may still ask. */
    NotAsked,

    /** Not shown, and only the system settings can change that. */
    Off,
}

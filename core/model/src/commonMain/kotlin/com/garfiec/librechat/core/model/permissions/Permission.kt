package com.garfiec.librechat.core.model.permissions

enum class Permission(val serverKey: String) {
    USE("USE"),
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    SHARE("SHARE"),
    SHARE_PUBLIC("SHARE_PUBLIC"),

    /** PEOPLE_PICKER: may search users to grant them access. */
    VIEW_USERS("VIEW_USERS"),

    /** PEOPLE_PICKER: may search groups to grant them access. */
    VIEW_GROUPS("VIEW_GROUPS"),
}

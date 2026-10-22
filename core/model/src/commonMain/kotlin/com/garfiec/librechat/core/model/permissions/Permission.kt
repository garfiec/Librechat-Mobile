package com.garfiec.librechat.core.model.permissions

/**
 * The permission keys a role's per-type map carries (upstream `Permissions` in
 * `packages/data-provider/src/permissions.ts`), as a subset: only the keys some gate here reads.
 * Registered in `scripts/mirrors.json`. [READ] and [OPT_OUT] are defined for MEMORIES
 * (`memoryPermissionsSchema`): READ gates the list route, OPT_OUT the preferences route.
 */
enum class Permission(val serverKey: String) {
    USE("USE"),
    CREATE("CREATE"),
    UPDATE("UPDATE"),
    READ("READ"),
    SHARE("SHARE"),
    OPT_OUT("OPT_OUT"),
    SHARE_PUBLIC("SHARE_PUBLIC"),

    /** PEOPLE_PICKER: may search users to grant them access. */
    VIEW_USERS("VIEW_USERS"),

    /** PEOPLE_PICKER: may search groups to grant them access. */
    VIEW_GROUPS("VIEW_GROUPS"),

    /** PEOPLE_PICKER: may search roles to grant them access. */
    VIEW_ROLES("VIEW_ROLES"),
}

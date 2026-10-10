package com.garfiec.librechat.core.common

/**
 * The app's own calver version (`YYYY.MM.PATCH`, optionally `-rcN`), as written in
 * `version.properties` and in release tags (`v2026.10.1`, `v2026.10.1-rc2`).
 *
 * Kept separate from [BackendVersion.SemanticVersion] because that type deliberately ignores
 * the prerelease part in compatibility checks; here an rc must sort below its final release so
 * a user on `2026.10.1-rc1` is told about `2026.10.1`.
 */
data class AppVersion(
    val year: Int,
    val month: Int,
    val patch: Int,
    /** `N` of `-rcN`, or null for a final release. */
    val rc: Int? = null,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            rc == other.rc -> 0
            rc == null -> 1
            other.rc == null -> -1
            else -> rc.compareTo(other.rc)
        }
    }

    // Month is zero-padded to match the tags (`v2026.09.0`).
    override fun toString(): String {
        val base = "$year.${month.toString().padStart(2, '0')}.$patch"
        return if (rc == null) base else "$base-rc$rc"
    }

    companion object {
        private val PATTERN = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:-rc\.?(\d+))?$""")

        /**
         * Parses a version name or release tag. A trailing `-debug` (the debug build type's
         * `versionNameSuffix`) is ignored so debug builds compare like their release.
         * Returns null for anything else, so callers never treat garbage as a newer version.
         */
        fun parse(raw: String): AppVersion? {
            val trimmed = raw.trim().removeSuffix("-debug")
            val match = PATTERN.matchEntire(trimmed) ?: return null
            val groups = match.groupValues
            return AppVersion(
                year = groups[1].toIntOrNull() ?: return null,
                month = groups[2].toIntOrNull() ?: return null,
                patch = groups[3].toIntOrNull() ?: return null,
                rc = groups[4].takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return null },
            )
        }
    }
}

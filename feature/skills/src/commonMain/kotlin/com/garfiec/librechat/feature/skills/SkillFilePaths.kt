package com.garfiec.librechat.feature.skills

/**
 * The relative path a picked file is uploaded under, held to the server's rule (upstream
 * `validateRelativePath` in `methods/skill.ts` and the upload handler in `skills/upload.ts`):
 * at most 500 characters of `[a-zA-Z0-9._-/]`, no empty, `.` or `..` segment, no leading slash,
 * and never `SKILL.md`, which the upload handler refuses in any letter case because it is the
 * skill's body. A path the client lets through and the server refuses is a bare 400.
 *
 * Uploads here are flat (no folders), so only the basename is kept and `/` never appears.
 */
internal object SkillFilePaths {
    const val MAX_LENGTH = 500
    private const val FALLBACK = "file"
    private const val RESERVED_UPPER = "SKILL.MD"

    /**
     * [filename]'s basename with every character outside ASCII `[A-Za-z0-9._-]` replaced by `_`
     * (Kotlin's `isLetterOrDigit` admits `é` and `文`, which the server's regex does not), leading
     * dots trimmed, and cut to [MAX_LENGTH] keeping the extension.
     */
    fun sanitize(filename: String): String {
        val base = filename.substringAfterLast('/').substringAfterLast('\\')
        val safe = base.map { c -> if (c.isAllowed()) c else '_' }.joinToString("")
            .trimStart('.')
            .ifBlank { FALLBACK }
        return safe.capped()
    }

    /** Whether [relativePath] is the reserved `SKILL.md`, in the upload handler's case-insensitive sense. */
    fun isReserved(relativePath: String): Boolean = relativePath.uppercase() == RESERVED_UPPER

    private fun Char.isAllowed(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '.' || this == '_' || this == '-'

    private fun String.capped(): String {
        if (length <= MAX_LENGTH) return this
        val ext = substringAfterLast('.', missingDelimiterValue = "")
        // A pathological "extension" longer than the budget is just cut with the rest.
        if (ext.isEmpty() || ext.length >= MAX_LENGTH / 2) return take(MAX_LENGTH)
        return take(MAX_LENGTH - ext.length - 1).trimEnd('.') + "." + ext
    }
}

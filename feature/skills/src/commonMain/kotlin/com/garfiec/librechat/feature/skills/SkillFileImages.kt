package com.garfiec.librechat.feature.skills

/**
 * The image types the server serves inline from `?raw=true` (upstream `SAFE_INLINE_MIMES`,
 * `packages/api/src/skills/handlers.ts`). Every other type comes back as an attachment, so only
 * these get a preview on the file screen. Registered in `scripts/mirrors.json`.
 */
internal val SKILL_FILE_INLINE_IMAGE_MIMES: Set<String> = setOf(
    "image/png",
    "image/jpeg",
    "image/gif",
    "image/webp",
    "image/avif",
    "image/bmp",
)

internal fun isSkillFileInlineImage(mimeType: String?): Boolean =
    mimeType != null && mimeType.substringBefore(';').trim().lowercase() in SKILL_FILE_INLINE_IMAGE_MIMES

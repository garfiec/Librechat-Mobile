package com.garfiec.librechat.core.model

/** Why a bookmark name can or can't be saved; see [BookmarkNameRules.validateName]. */
enum class BookmarkNameCheck { Ok, Blank, TooLong, Duplicate, Reserved }

/**
 * Client-side rules for a bookmark (conversation tag) name and description.
 *
 * The limits mirror upstream `client/src/components/Bookmarks/BookmarkForm.tsx` (title `maxLength`
 * 128, description `maxLength` 1048); the server enforces neither (registered in
 * `scripts/mirrors.json`). The duplicate check is case-insensitive, which the server's is not, and
 * [SAVED_TAG] is reserved for the favorite star.
 */
object BookmarkNameRules {
    const val MAX_NAME = 128
    const val MAX_DESCRIPTION = 1048

    /**
     * Checks [name] (trimmed) against [existing] names. [editing] is the name being renamed, if any;
     * it doesn't count as a duplicate of itself, so a case-only rename passes.
     *
     * A name the edit leaves as it was passes once it isn't blank: the web makes names these rules
     * refuse ("SAVED", "work" beside "Work", over 128 characters), and such a bookmark must still
     * take a description edit. A case-only rename is a change, and is checked.
     */
    fun validateName(name: String, existing: Collection<String>, editing: String? = null): BookmarkNameCheck {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> BookmarkNameCheck.Blank
            editing != null && trimmed == editing.trim() -> BookmarkNameCheck.Ok
            trimmed.length > MAX_NAME -> BookmarkNameCheck.TooLong
            trimmed.equals(SAVED_TAG, ignoreCase = true) -> BookmarkNameCheck.Reserved
            existing.any { it != editing && it.equals(trimmed, ignoreCase = true) } -> BookmarkNameCheck.Duplicate
            else -> BookmarkNameCheck.Ok
        }
    }
}

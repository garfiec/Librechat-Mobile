package com.garfiec.librechat.feature.chat.viewmodel.delegate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A PDF upload held until the user types the file's password. [chipKey] is the attached-file chip
 * (its `uri`), left pending (not failed) meanwhile; [file] is what to upload again with the password.
 */
data class PdfPasswordPrompt(
    val chipKey: Any,
    val file: PickedFile,
    val incorrectPassword: Boolean,
)

/**
 * The prompts waiting on the user, oldest first; the UI shows the head. Shared by both platform
 * handlers so the queueing rules can't drift apart.
 */
class PdfPasswordPrompts {
    private val _pending = MutableStateFlow<List<PdfPasswordPrompt>>(emptyList())
    val pending: StateFlow<List<PdfPasswordPrompt>> = _pending.asStateFlow()

    /** Replaces any prompt for the same chip: a retry's answer supersedes the first attempt's. */
    fun add(prompt: PdfPasswordPrompt) = _pending.update { list -> list.filter { it.chipKey != prompt.chipKey } + prompt }

    /** Removes and returns the prompt the UI is showing. */
    fun takeHead(): PdfPasswordPrompt? {
        var head: PdfPasswordPrompt? = null
        _pending.update { list ->
            head = list.firstOrNull()
            list.drop(1)
        }
        return head
    }

    /**
     * Removes [prompt] if it is still pending. False when a dismiss, a chip removal or an earlier
     * submit already consumed it, so a late answer can never land on a different file's prompt.
     */
    fun take(prompt: PdfPasswordPrompt): Boolean {
        var found = false
        _pending.update { list ->
            found = prompt in list
            list - prompt
        }
        return found
    }

    /** Drops the prompt for a chip the user removed or retried. */
    fun remove(chipKey: Any) = _pending.update { list -> list.filter { it.chipKey != chipKey } }

    fun clear() = _pending.update { emptyList() }
}

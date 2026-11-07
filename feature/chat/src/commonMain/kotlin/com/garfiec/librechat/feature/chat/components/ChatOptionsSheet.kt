package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.garfiec.librechat.feature.chat.util.ToolsSheetFocus

/**
 * The pages the shared chat-options sheet can show. [Options] is the root; the others stack over
 * it and return to it, rather than each being its own sheet that dismisses to the chat.
 *
 * The [ChatOptionsSheetController] Saver persists an entry by [name][Enum.name] (a stable string,
 * never the ordinal), so reordering the entries is safe but renaming one breaks a saved round trip.
 */
enum class ChatOptionsPage {
    /** The tools/attachment menu — the "+" sheet's root. See [ChatToolsSheetContent]. */
    Options,
    ModelSelector,
    ModelParameters,
}

/**
 * Visibility state for [ChatOptionsBottomSheet], hoisted into a controller because two entry points
 * drive it (the composer's "+" and the pull-up surface). Deliberately *not* ViewModel state:
 * `ChatUiState.showModelSheet` means "the standalone selector is open" and must stay independent.
 */
@Stable
class ChatOptionsSheetController {
    /**
     * Non-null while open; the page *currently* showing. In-sheet swaps write back through [open],
     * so the [Saver] persists the visible page — what makes the "Add key" round trip return to
     * the open selector rather than the Options menu.
     */
    var openPage: ChatOptionsPage? by mutableStateOf(null)
        private set

    /**
     * The row the Options page scrolls to and marks as it opens: a settings chip's tool or MCP server.
     * Not saved: it is for the tap that opened the sheet.
     */
    var focus: ToolsSheetFocus? by mutableStateOf(null)
        private set

    /**
     * Opens the sheet on [page], or navigates the already-open sheet to it — same effect either way —
     * at [focus]'s row when given.
     */
    fun open(page: ChatOptionsPage = ChatOptionsPage.Options, focus: ToolsSheetFocus? = null) {
        openPage = page
        this.focus = focus
    }

    fun close() {
        openPage = null
        focus = null
    }

    internal companion object {
        /**
         * Persists the open page across host teardown — load-bearing for the selector's "Set API
         * Key" CTA, which round-trips to Settings and back. `showModelSheet` got this free from the
         * ViewModel; this state is screen-local, so it must be saved.
         */
        val Saver: Saver<ChatOptionsSheetController, String> = Saver(
            save = { it.openPage?.name ?: CLOSED_KEY },
            restore = { key ->
                ChatOptionsSheetController().apply {
                    ChatOptionsPage.entries.find { it.name == key }?.let { openPage = it }
                }
            },
        )

        private const val CLOSED_KEY = "closed"
    }
}

@Composable
fun rememberChatOptionsSheetController(): ChatOptionsSheetController =
    rememberSaveable(saver = ChatOptionsSheetController.Saver) { ChatOptionsSheetController() }

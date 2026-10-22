package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryKeyProblem
import com.garfiec.librechat.core.model.response.MemoriesResponse

/** The per-value cap the memory routes apply when the deployment configures none. */
const val DEFAULT_MEMORY_CHAR_LIMIT: Int = 10_000

/** Search appears from this many memories, like the MCP servers page. */
internal const val MEMORY_SEARCH_THRESHOLD: Int = 5

@Immutable
data class MemoriesUiState(
    /** The last list and usage envelope that loaded; null until one has. */
    val snapshot: MemoriesResponse? = null,
    /** True until the first load settles, success or failure. The only time a spinner shows. */
    val isFirstLoad: Boolean = true,
    /** A pull-to-refresh the user asked for is running. Quiet reloads never set it. */
    val isRefreshing: Boolean = false,
    /** Why the first load failed, while nothing has ever loaded. Later failures are events. */
    val loadError: MemoryFailure? = null,
    /** The search text as typed. Applied only while [showSearch]. */
    val query: String = "",
    /** The selected filter chip, already narrowed to one that still has memories. */
    val partition: MemoryPartition = MemoryPartition.All,
    /** The user's opt-out from the shared repository flow, or null while not yet known. */
    val memoriesEnabled: Boolean? = null,
    /**
     * The last read of the opt-out failed while its value is still unknown, so the switch card
     * offers Retry instead of a blank switch. Derived false once [memoriesEnabled] is known.
     */
    val preferenceLoadFailed: Boolean = false,
    val isTogglingPreference: Boolean = false,
    /** Memories with a delete in flight, by [memoryRowHandle]. */
    val deletingHandles: Set<String> = emptySet(),
    /** The open memory sheet, or null when none is. */
    val editor: MemoryEditorState? = null,
    // Role gates: MEMORIES USE plus the action's own bit, permissive while the role or a key is
    // unknown. Derived in MemoriesViewModel.uiState, never set directly.
    /**
     * `canOpenMemories()`, the list's gate. The page is reachable before the role loads, and without
     * READ when the role may opt out, so this can be false here.
     */
    val canRead: Boolean = true,
    val canCreate: Boolean = true,
    val canUpdate: Boolean = true,
    val canOptOut: Boolean = true,
    /**
     * Whether the opt-out switch still shows while the list is denied: [canOptOut], except after a
     * 403 on the list's load while READ looked allowed. That 403 may mean USE is missing too, so the
     * switch then stays only when the role grants OPT_OUT explicitly.
     */
    val canOptOutWithoutList: Boolean = false,
    // Derived from the fields above.
    /** Every memory, in server order (last updated first). */
    val memories: List<Memory> = emptyList(),
    /** [memories] narrowed by [partition] and, while shown, the search. What the list draws. */
    val visibleMemories: List<Memory> = emptyList(),
    /** The filter chips, or null when no memory belongs to an agent (no chips at all). */
    val partitionOptions: List<MemoryPartitionOption>? = null,
    /** agentId to the number that tells its label apart from another agent's identical one. */
    val agentOrdinals: Map<String, Int> = emptyMap(),
    val showSearch: Boolean = false,
    /** The value cap from the envelope, or [DEFAULT_MEMORY_CHAR_LIMIT] when the server omits it. */
    val charLimit: Int = DEFAULT_MEMORY_CHAR_LIMIT,
) {
    /** The trimmed search the list answers; blank while search is hidden. */
    val activeQuery: String get() = if (showSearch) query.trim() else ""

    /**
     * The list may not be shown: the role lacks READ (the page opened while the role was unknown or
     * on OPT_OUT alone, or the role changed since), or the list's first load answered 403, which its
     * route returns only for a missing USE or READ.
     */
    val listDenied: Boolean get() = !canRead || loadError == MemoryFailure.Forbidden

    /**
     * The list is denied but the role may opt out: the page is the switch card alone, with no list,
     * search, chips, FAB or banner. The opt-out route needs USE and OPT_OUT, not READ.
     */
    val switchOnly: Boolean get() = listDenied && canOptOutWithoutList

    /** Nothing on the page is allowed: it shows only its no-access state, as web's panel does. */
    val noAccess: Boolean get() = listDenied && !canOptOutWithoutList

    /** Memories exist but none matches the search: the "no matches" line, not the empty state. */
    val isSearchEmpty: Boolean get() = memories.isNotEmpty() && visibleMemories.isEmpty() && activeQuery.isNotEmpty()
}

/** Which memories the list shows. */
sealed interface MemoryPartition {
    data object All : MemoryPartition

    /** The shared pool: `agentId == null`. */
    data object Personal : MemoryPartition

    data class Agent(val agentId: String) : MemoryPartition
}

/**
 * One filter chip. [agentName] is the server-resolved name for an [MemoryPartition.Agent] chip,
 * null when the agent is not visible to the user (the screen falls back to a generic label) and
 * for the All and Personal chips. [ordinal] numbers an agent chip whose label another agent chip
 * would repeat, so the two read differently; null otherwise.
 */
@Immutable
data class MemoryPartitionOption(
    val partition: MemoryPartition,
    val agentName: String? = null,
    val ordinal: Int? = null,
)

enum class MemoryEditorMode {
    Create,
    Edit,

    /** Read-only: no UPDATE permission, or a content-filtered entry. */
    View,
}

/** Why a memory value would be refused. */
enum class MemoryValueProblem {
    REQUIRED,

    /** Longer than the envelope's `charLimit`, counted on the trimmed text. */
    TOO_LONG,
}

/**
 * The memory sheet's draft. [key] and [value] are the text as typed; everything sent is trimmed.
 * The validation fields are derived in `MemoriesViewModel.uiState` from the draft and the list.
 */
@Immutable
data class MemoryEditorState(
    val mode: MemoryEditorMode,
    /** The memory being edited or viewed; null when creating. */
    val original: Memory? = null,
    val key: String = "",
    val value: String = "",
    val isSaving: Boolean = false,
    /** The trimmed key the server answered 409 for; the key field reads it as a duplicate. */
    val conflictKey: String? = null,
    val showDiscardConfirm: Boolean = false,
    /**
     * Why the last save from this sheet failed, shown inside the sheet: a snackbar would sit under
     * it. Save is the retry. Cleared by the next edit or save. A 409 goes to [conflictKey] instead.
     */
    val saveFailure: MemoryFailure? = null,
    // Derived.
    val keyError: MemoryKeyProblem? = null,
    val valueError: MemoryValueProblem? = null,
    /** Length of the trimmed value, which is what the server counts against the cap. */
    val valueLength: Int = 0,
    val isDirty: Boolean = false,
    val canSave: Boolean = false,
    /** The stored entry's share of the personal pool, or null when it can't be worked out. */
    val tokenShare: MemoryTokenShare? = null,
)

/**
 * What [tokens] takes of the room the personal pool leaves this entry, as web's edit dialog puts it:
 * `tokenCount / (tokenLimit − totalTokens + tokenCount)`, rounded to a whole [percent].
 */
@Immutable
data class MemoryTokenShare(val tokens: Int, val percent: Int)

/** A failed action's reason, mapped to the page's own strings by the screen. */
sealed interface MemoryFailure {
    /** 409: the key is taken in that partition. */
    data object DuplicateKey : MemoryFailure

    /** 403 for the user's role. */
    data object Forbidden : MemoryFailure

    /** 400 `content_filter_block`, or an edit of an entry the filter redacted. */
    data object ContentFiltered : MemoryFailure

    /** `Agent not found.` (404) or `Agent access denied.` (403) on an agent-partitioned write. */
    data object AgentUnavailable : MemoryFailure

    data object Network : MemoryFailure

    /**
     * An edit's 404 (agent's aside): the memory was deleted elsewhere, so the same PATCH can only
     * answer 404 again. Only a write sets it; a 404 on the list's load stays [Generic].
     */
    data object NotFound : MemoryFailure

    /**
     * The edit can't be sent on this server: only the key route could carry it, and its path for
     * this key belongs to another route (a memory keyed `preferences`). Deleting it still works.
     */
    data object KeyRouteShadowed : MemoryFailure

    /** Any other 400: the server's own sentence, already screened for display. */
    data class ServerMessage(val text: String) : MemoryFailure

    /** Anything else; the screen words it by the action that failed. */
    data object Generic : MemoryFailure

    /** Whether repeating the same request could succeed. */
    val retryable: Boolean get() = this == Network || this == Generic
}

/** A request the page sends, carried by a failure so the snackbar can word it and Retry repeat it. */
sealed interface MemoryOperation {
    /**
     * An operation a snackbar's Retry re-runs. A save is not one: the sheet can't be closed or
     * replaced while it saves, so the sheet shows its failure and its own Save is the retry. Only a
     * delete of the same row can close it meanwhile, and then there is nothing left to save.
     */
    sealed interface Retryable : MemoryOperation

    /** A reload of the list the user asked for (pull to refresh). */
    data object Refresh : Retryable

    data class Create(val key: String, val value: String) : MemoryOperation

    /** [newKey] is the trimmed key field; the repository sends it only when it differs. */
    data class Update(val memory: Memory, val value: String, val newKey: String) : MemoryOperation

    data class Delete(val memory: Memory) : Retryable

    data class SetEnabled(val enabled: Boolean) : Retryable
}

sealed interface MemoriesEvent {
    /** A failure the page reports in a snackbar, with Retry while [retryOperation] is non-null. */
    data class Failed(
        val operation: MemoryOperation,
        val failure: MemoryFailure,
    ) : MemoriesEvent {
        /** What Retry re-runs: [operation], when it is [MemoryOperation.Retryable] and repeating it could help. */
        val retryOperation: MemoryOperation.Retryable?
            get() = (operation as? MemoryOperation.Retryable)?.takeIf { failure.retryable }

        val canRetry: Boolean get() = retryOperation != null
    }
}

/**
 * One memory's identity across reloads: the server id, else its partition-qualified key. Keys are
 * unique only within a partition, and a content-filtered row's key comes back blank.
 */
fun memoryRowHandle(memory: Memory): String = memory.id ?: "${memory.agentId.orEmpty()}/${memory.key}"

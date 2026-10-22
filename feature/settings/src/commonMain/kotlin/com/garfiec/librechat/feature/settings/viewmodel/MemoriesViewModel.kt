package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MemoryRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryKeyProblem
import com.garfiec.librechat.core.model.memoryKeyProblem
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.canOpenMemories
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.response.MemoriesResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * The Memories page: the list with its usage envelope, local search and partition filter, the
 * opt-out switch, and the memory sheet's draft.
 *
 * Loading has two faces. Until the first load settles the page shows placeholders, and a failure
 * then is [MemoriesUiState.loadError]. After that every reload is quiet and keeps the list; a
 * failed action is a one-shot [MemoriesEvent] whose Retry re-runs exactly that action, except a
 * save that fails while its sheet is open: that one is [MemoryEditorState.saveFailure], since the
 * sheet covers the snackbar.
 */
@Suppress("TooManyFunctions") // one entry point per user action on the page and its sheet
class MemoriesViewModel(
    private val memoryRepository: MemoryRepository,
    private val roleRepository: RoleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoriesUiState())

    val uiState: StateFlow<MemoriesUiState> = combine(
        _uiState,
        roleRepository.userPermissions,
        memoryRepository.memoriesEnabled,
    ) { state, role, enabled ->
        state.derive(role, enabled)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MemoriesUiState().derive(null, null))

    // A Channel, not a SharedFlow: a failure that lands while the screen is not collecting (the
    // app in the background) is delivered once it is, rather than dropped.
    private val _events = Channel<MemoriesEvent>(Channel.BUFFERED)
    val events: Flow<MemoriesEvent> = _events.receiveAsFlow()

    // Apart from [events], whose collector waits out each snackbar: the scroll must not queue
    // behind one. Conflated, as two creates in a row need one scroll.
    private val _scrollToTop = Channel<Unit>(Channel.CONFLATED)

    /** Asks the screen to scroll the list to the top, where a memory just created is. */
    val scrollToTop: Flow<Unit> = _scrollToTop.receiveAsFlow()

    private var loadJob: Job? = null

    /** Whether [loadJob] reports its failure: it replaced, or is, a pull-to-refresh. */
    private var loadReportsFailure = false
    private var preferenceJob: Job? = null
    private var saveJob: Job? = null
    private var toggleJob: Job? = null

    /** The first resume follows `init`'s load; only later ones are returns to the page. */
    private var resumedOnce = false

    init {
        val readableAtOpen = current().canRead
        load(reportFailure = false)
        refreshPreference()
        loadWhenReadIsGranted(readableAtOpen)
    }

    /**
     * [load] skips the list while the role denies READ, and the role is fetched only at session
     * start. A live fetch granting READ after a stale cached denial must load the list here, or
     * the page shows the empty state for memories it never asked for. [readableAtOpen] is what
     * init's load saw, so a grant landing before this collector starts still counts as one.
     */
    private fun loadWhenReadIsGranted(readableAtOpen: Boolean) {
        var readable = readableAtOpen
        viewModelScope.launch {
            roleRepository.userPermissions
                .map { it.canOpenMemories() }
                .distinctUntilChanged()
                .collect { canRead ->
                    val granted = canRead && !readable
                    readable = canRead
                    if (!granted || _uiState.value.snapshot != null) return@collect
                    _uiState.update { it.copy(isFirstLoad = true, loadError = null) }
                    load(reportFailure = false)
                }
        }
    }

    /** The state as [uiState] derives it, read synchronously for the action guards. */
    private fun current(): MemoriesUiState = _uiState.value.derive(
        roleRepository.userPermissions.value,
        memoryRepository.memoriesEnabled.value,
    )

    // ---------------------------------------------------------------------------------- loading

    /** Pull to refresh: keeps the list on screen and reports a failure as an event. */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        load(reportFailure = true)
        refreshPreference()
    }

    /** Retry on the first-load error banner: back to the placeholders, then the load again. */
    fun retryLoad() {
        _uiState.update { it.copy(isFirstLoad = it.snapshot == null, loadError = null) }
        load(reportFailure = false)
        refreshPreference()
    }

    /**
     * Reloads when the page resumes after the first time, which is a return from a chat that may
     * have saved a memory. Quiet: no spinner, and skipped while a load is already running.
     */
    fun refreshOnReturn() {
        if (!resumedOnce) {
            resumedOnce = true
            return
        }
        if (loadJob?.isActive == true) return
        load(reportFailure = false)
        refreshPreference()
    }

    /**
     * The latest request wins: a reload started after a save must not be overwritten by an older
     * one still in flight. A reload that replaces a running pull-to-refresh reports its failure as
     * that refresh would have, or the refresh the user asked for would fail without a word. Not
     * sent when the role is known to lack READ, as the route would answer 403: the page is then the
     * opt-out switch alone, or its no-access state.
     */
    private fun load(reportFailure: Boolean) {
        val report = reportFailure || (loadJob?.isActive == true && loadReportsFailure)
        loadJob?.cancel()
        loadReportsFailure = report
        if (!current().canRead) {
            _uiState.update { it.copy(isFirstLoad = false, isRefreshing = false) }
            return
        }
        loadJob = viewModelScope.launch {
            when (val result = memoryRepository.getMemories()) {
                is Result.Success -> _uiState.update { state ->
                    state.copy(
                        isFirstLoad = false,
                        isRefreshing = false,
                        loadError = null,
                        partition = state.partition.narrowedTo(result.data.memories),
                    ).withSnapshot(result.data)
                }
                is Result.Error -> {
                    val failure = result.toMemoryFailure()
                    val hadList = _uiState.value.snapshot != null
                    _uiState.update {
                        it.copy(
                            isFirstLoad = false,
                            isRefreshing = false,
                            loadError = if (hadList) null else failure,
                        )
                    }
                    if (hadList && report) _events.send(MemoriesEvent.Failed(MemoryOperation.Refresh, failure))
                }
                is Result.Loading -> Unit
            }
        }
    }

    /** The switch card's Retry, after the opt-out could not be read. */
    fun retryPreference() = refreshPreference()

    /**
     * Re-reads the opt-out into the repository's shared flow. Skipped while a toggle is in flight,
     * since its answer could be older than the PATCH; the repository also drops any read that a
     * write overlapped, which covers one already running when the toggle starts. The latest read
     * wins, so an older one failing late can't mark the switch unreadable. A failure is kept in
     * [MemoriesUiState.preferenceLoadFailed], which shows only while the value is still unknown.
     */
    private fun refreshPreference() {
        if (toggleJob?.isActive == true) return
        preferenceJob?.cancel()
        preferenceJob = viewModelScope.launch {
            _uiState.update { it.copy(preferenceLoadFailed = false) }
            val result = memoryRepository.refreshMemoriesPreference()
            if (result is Result.Error) Logger.d(result.exception) { "Failed to load memories opt-out" }
            _uiState.update { it.copy(preferenceLoadFailed = result is Result.Error) }
        }
    }

    // ------------------------------------------------------------------------- search and filter

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun selectPartition(partition: MemoryPartition) {
        _uiState.update { it.copy(partition = partition.narrowedTo(it.snapshot?.memories.orEmpty())) }
    }

    // ---------------------------------------------------------------------------------- opt-out

    /**
     * Ignored without OPT_OUT, on the no-access page, while the stored value is unknown, when it
     * already reads [enabled], or while a change is in flight.
     */
    fun setMemoriesEnabled(enabled: Boolean) {
        if (canSetEnabled(enabled)) runSetEnabled(enabled)
    }

    /**
     * An unknown value is refused too: it is unknown after an account switch, and the change would
     * then land on the incoming account.
     */
    private fun canSetEnabled(enabled: Boolean): Boolean {
        val state = current()
        return state.canOptOut && !state.noAccess && state.memoriesEnabled != null && state.memoriesEnabled != enabled
    }

    private fun runSetEnabled(enabled: Boolean) {
        if (toggleJob?.isActive == true) return
        toggleJob = viewModelScope.launch {
            _uiState.update { it.copy(isTogglingPreference = true) }
            // The repository publishes the stored value to memoriesEnabled on success; the switch
            // follows that flow rather than moving ahead of the server.
            val result = memoryRepository.updatePreferences(UpdateMemoryPreferencesRequest(enabled = enabled))
            _uiState.update { it.copy(isTogglingPreference = false) }
            if (result is Result.Error) {
                _events.send(MemoriesEvent.Failed(MemoryOperation.SetEnabled(enabled), result.toMemoryFailure()))
            }
        }
    }

    // ----------------------------------------------------------------------------------- editor

    /**
     * Opens an empty sheet for a new personal memory. Needs CREATE. Ignored while a save is in
     * flight, as [openMemory] is: its answer belongs to the open sheet.
     */
    fun openCreate() {
        if (!current().canCreate || saveJob?.isActive == true) return
        _uiState.update { it.copy(editor = MemoryEditorState(mode = MemoryEditorMode.Create)) }
    }

    /**
     * Opens [memory]'s sheet: editable with UPDATE, read-only without it or when the content filter
     * redacted the entry (its blanked fields must never be saved over what is stored). Ignored while
     * a save is in flight: replacing its sheet would leave the save's failure nowhere to show.
     */
    fun openMemory(memory: Memory) {
        if (saveJob?.isActive == true) return
        val mode = if (current().canUpdate && memory.contentFilterBlocked != true) {
            MemoryEditorMode.Edit
        } else {
            MemoryEditorMode.View
        }
        _uiState.update {
            it.copy(editor = MemoryEditorState(mode = mode, original = memory, key = memory.key, value = memory.value))
        }
    }

    fun updateEditorKey(key: String) = updateDraft { it.copy(key = key) }

    fun updateEditorValue(value: String) = updateDraft { it.copy(value = value) }

    private inline fun updateDraft(crossinline change: (MemoryEditorState) -> MemoryEditorState) {
        _uiState.update { state ->
            val editor = state.editor
            if (editor == null || editor.mode == MemoryEditorMode.View || editor.isSaving) {
                state
            } else {
                state.copy(editor = change(editor).copy(saveFailure = null))
            }
        }
    }

    /**
     * Closes the sheet, or asks first when the draft has unsaved changes. Ignored while a save is in
     * flight: its answer belongs to this sheet, and closing would let it land on the next one.
     */
    fun dismissEditor() {
        val editor = current().editor ?: return
        if (editor.isSaving) return
        _uiState.update { state ->
            if (editor.isDirty) {
                state.copy(editor = state.editor?.copy(showDiscardConfirm = true))
            } else {
                state.copy(editor = null)
            }
        }
    }

    fun confirmDiscard() {
        _uiState.update { state -> if (state.editor?.isSaving == true) state else state.copy(editor = null) }
    }

    fun keepEditing() {
        _uiState.update { it.copy(editor = it.editor?.copy(showDiscardConfirm = false)) }
    }

    /** Saves the draft, trimmed. Ignored while invalid or while a save is already in flight. */
    fun saveEditor() {
        val editor = current().editor ?: return
        if (!editor.canSave || saveJob?.isActive == true) return
        val operation = when (editor.mode) {
            MemoryEditorMode.Create -> MemoryOperation.Create(key = editor.key.trim(), value = editor.value.trim())
            MemoryEditorMode.Edit -> MemoryOperation.Update(
                memory = editor.original ?: return,
                value = editor.value.trim(),
                newKey = editor.key.trim(),
            )
            MemoryEditorMode.View -> return
        }
        runSave(operation)
    }

    private fun runSave(operation: MemoryOperation) {
        if (saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            setSaving(operation, saving = true)
            val result = when (operation) {
                is MemoryOperation.Create -> memoryRepository.createMemory(
                    CreateMemoryRequest(key = operation.key, value = operation.value),
                )
                is MemoryOperation.Update -> memoryRepository.updateMemory(
                    memory = operation.memory,
                    value = operation.value,
                    newKey = operation.newKey,
                )
                else -> return@launch
            }
            when (result) {
                // A 2xx without the entry echoed back is still a stored write: close and reload.
                is Result.Success -> {
                    val written = result.data
                    val replaced = (operation as? MemoryOperation.Update)?.memory
                    val created = operation as? MemoryOperation.Create
                    _uiState.update { state ->
                        val next = state.copy(editor = state.editor?.takeUnless { it.isDraftOf(operation) })
                            // Shown now rather than on the reload, which may fail and would leave
                            // the write invisible (and a re-add of a created key a 409).
                            .withSnapshot(if (written == null) state.snapshot else state.snapshot?.withWritten(written, replaced))
                        if (created == null) next else next.revealing(created)
                    }
                    // The list changing is the only confirmation a save gets, so a new memory must
                    // be on screen: it is first in the list, which may be scrolled past.
                    if (created != null) _scrollToTop.trySend(Unit)
                    load(reportFailure = false)
                }
                is Result.Error -> onSaveFailed(operation, result)
                is Result.Loading -> Unit
            }
        }
    }

    private suspend fun onSaveFailed(operation: MemoryOperation, result: Result.Error) {
        setSaving(operation, saving = false)
        if (operation is MemoryOperation.Update && result.isNotFound()) {
            onEditedMemoryGone(operation)
            load(reportFailure = false)
            return
        }
        val failure = result.toMemoryFailure()
        val editorOpen = _uiState.value.editor?.isDraftOf(operation) == true
        when {
            failure == MemoryFailure.DuplicateKey && editorOpen -> {
                // The key field says it; a line under the form as well would say it twice.
                val conflict = when (operation) {
                    is MemoryOperation.Create -> operation.key
                    is MemoryOperation.Update -> operation.newKey
                    else -> null
                }
                _uiState.update { it.copy(editor = it.editor?.copy(conflictKey = conflict)) }
            }
            // The open sheet covers the snackbar host, so the sheet says it; Save is the retry.
            editorOpen -> _uiState.update { it.copy(editor = it.editor?.copy(saveFailure = failure)) }
            else -> _events.send(MemoriesEvent.Failed(operation, failure))
        }
        // A key-route rename is create-then-delete, so a failure can leave both rows (or neither);
        // a 404 means the list itself is stale, and a 409 means the server holds a row the list
        // lacks (stored by a chat or the web since it loaded). Either way, show what it now holds;
        // the reloaded row then keeps the key flagged as a duplicate after the user edits it.
        val renamed = operation is MemoryOperation.Update && operation.newKey != operation.memory.key
        if (renamed || result.isNotFound() || failure == MemoryFailure.DuplicateKey) load(reportFailure = false)
    }

    /**
     * The memory [operation] edits was deleted elsewhere (a chat's delete_memory, the web), so its
     * PATCH can only answer 404 again. Its row goes now, whether or not the reload works. An open
     * personal sheet becomes an add of the same draft, keeping what the user typed, when the role
     * may create; a create is always personal, so an agent's sheet closes with a snackbar instead.
     */
    private suspend fun onEditedMemoryGone(operation: MemoryOperation.Update) {
        val handle = memoryRowHandle(operation.memory)
        val editorOpen = _uiState.value.editor?.isDraftOf(operation) == true
        val readd = editorOpen && operation.memory.agentId == null && current().canCreate
        _uiState.update { state ->
            val removed = state.withoutRow(handle)
            if (readd) {
                removed.copy(
                    editor = state.editor?.copy(
                        mode = MemoryEditorMode.Create,
                        original = null,
                        conflictKey = null,
                        showDiscardConfirm = false,
                        saveFailure = MemoryFailure.NotFound,
                    ),
                )
            } else {
                removed
            }
        }
        if (!readd) _events.send(MemoriesEvent.Failed(operation, MemoryFailure.NotFound))
    }

    private fun setSaving(operation: MemoryOperation, saving: Boolean) {
        _uiState.update { state ->
            val editor = state.editor
            if (editor != null && editor.isDraftOf(operation)) {
                // A new attempt clears the last one's failure line.
                val failure = if (saving) null else editor.saveFailure
                state.copy(editor = editor.copy(isSaving = saving, saveFailure = failure))
            } else {
                state
            }
        }
    }

    // ----------------------------------------------------------------------------------- delete

    /**
     * Deletes [memory]. Needs UPDATE and a list the page may show; a second request for the same row
     * while one runs is ignored.
     */
    fun deleteMemory(memory: Memory) {
        val state = current()
        if (!state.canUpdate || state.listDenied) return
        runDelete(memory)
    }

    private fun runDelete(memory: Memory) {
        val handle = memoryRowHandle(memory)
        if (handle in _uiState.value.deletingHandles) return
        _uiState.update { it.copy(deletingHandles = it.deletingHandles + handle) }
        viewModelScope.launch {
            val result = memoryRepository.deleteMemory(memory)
            _uiState.update { it.copy(deletingHandles = it.deletingHandles - handle) }
            // A 404 means the memory was already gone (deleted by a chat or elsewhere): what the
            // user asked for holds, and a Retry could only answer 404 again.
            val gone = result is Result.Success || (result is Result.Error && result.isNotFound())
            if (gone) {
                _uiState.update { it.withoutRow(handle) }
                load(reportFailure = false)
            } else if (result is Result.Error) {
                _events.send(MemoriesEvent.Failed(MemoryOperation.Delete(memory), result.toMemoryFailure()))
            }
        }
    }

    // ------------------------------------------------------------------------------------ retry

    /**
     * Re-runs exactly the request [operation] describes, as a snackbar's Retry, under the same gates
     * as the action itself: the role may have changed (or the page lost access) since it failed.
     * A toggle whose value the shared flow already holds is not re-sent.
     */
    fun retry(operation: MemoryOperation.Retryable) {
        when (operation) {
            MemoryOperation.Refresh -> refresh()
            is MemoryOperation.Delete -> deleteMemory(operation.memory)
            is MemoryOperation.SetEnabled -> setMemoriesEnabled(operation.enabled)
        }
    }
}

/**
 * This state with the filters loosened so [created] is in the list: a create is always personal, so
 * an agent chip falls back to All, and a search it doesn't match is cleared.
 */
private fun MemoriesUiState.revealing(created: MemoryOperation.Create): MemoriesUiState {
    val needle = query.trim()
    val matches = needle.isEmpty() ||
        created.key.contains(needle, ignoreCase = true) ||
        created.value.contains(needle, ignoreCase = true)
    return copy(
        partition = if (partition is MemoryPartition.Agent) MemoryPartition.All else partition,
        query = if (matches) query else "",
    )
}

/**
 * This state with the row [handle] names removed, and its sheet closed if open. Gone now, not on
 * the reload: a reload that fails must not leave a row whose every action would answer 404.
 */
private fun MemoriesUiState.withoutRow(handle: String): MemoriesUiState {
    val open = editor?.original
    return copy(editor = editor.takeUnless { open != null && memoryRowHandle(open) == handle })
        .withSnapshot(snapshot?.let { it.copy(memories = it.memories.filterNot { row -> memoryRowHandle(row) == handle }) })
}

/**
 * This state showing [next]. A list that drops below [MEMORY_SEARCH_THRESHOLD] hides the search, so
 * its query is cleared with it: kept, it would come back and filter the list unasked once the count
 * reached the threshold again, with the field showing text the user had stopped seeing.
 */
private fun MemoriesUiState.withSnapshot(next: MemoriesResponse?): MemoriesUiState {
    val searchHidden = next != null && next.memories.size < MEMORY_SEARCH_THRESHOLD
    return copy(snapshot = next, query = if (searchHidden) "" else query)
}

/** Whether this sheet is the one [operation] was saved from. */
private fun MemoryEditorState.isDraftOf(operation: MemoryOperation): Boolean = when (operation) {
    is MemoryOperation.Create -> mode == MemoryEditorMode.Create
    is MemoryOperation.Update -> mode == MemoryEditorMode.Edit &&
        original != null && memoryRowHandle(original) == memoryRowHandle(operation.memory)
    else -> false
}

/**
 * This list with [written], the entry a create or update echoed, in place of [replaced] (the row as
 * it was before the update, whose key a rename changed) and of any row with its own handle. First,
 * as the server orders by last update. The usage totals stay as they were until the next load.
 *
 * Only the list route resolves `agentName`; a write's echo is the stored record without it, so the
 * replaced row's name is kept while the entry stays in the same agent's partition.
 */
private fun MemoriesResponse.withWritten(written: Memory, replaced: Memory?): MemoriesResponse {
    val gone = setOfNotNull(memoryRowHandle(written), replaced?.let(::memoryRowHandle))
    val merged = if (written.agentName == null && replaced != null && replaced.agentId == written.agentId) {
        written.copy(agentName = replaced.agentName)
    } else {
        written
    }
    return copy(memories = listOf(merged) + memories.filterNot { memoryRowHandle(it) in gone })
}

/** An agent chip whose memories are gone, or any chip once there are no chips, falls back to All. */
private fun MemoryPartition.narrowedTo(memories: List<Memory>): MemoryPartition {
    if (memories.none { it.agentId != null }) return MemoryPartition.All
    return when (this) {
        is MemoryPartition.Agent -> if (memories.any { it.agentId == agentId }) this else MemoryPartition.All
        else -> this
    }
}

private fun UserRolePermissions?.canMemories(permission: Permission): Boolean =
    hasAccessOrPermissive(PermissionType.MEMORIES, Permission.USE) &&
        hasAccessOrPermissive(PermissionType.MEMORIES, permission)

/** Fills the derived fields from the raw state and the shared flows. Pure. */
internal fun MemoriesUiState.derive(
    role: UserRolePermissions?,
    memoriesEnabled: Boolean?,
): MemoriesUiState {
    val memories = snapshot?.memories.orEmpty()
    val canRead = role.canOpenMemories()
    val canOptOut = role.canMemories(Permission.OPT_OUT)
    // A known READ denial leaves OPT_OUT's own bit to decide. A 403 despite READ looking allowed
    // could be a missing USE, so then only an explicit OPT_OUT grant keeps the switch.
    val readLooksAllowedButForbidden = canRead && loadError == MemoryFailure.Forbidden
    val canOptOutWithoutList = canOptOut &&
        (!readLooksAllowedButForbidden || role.hasAccessStrictOrDenied(PermissionType.MEMORIES, Permission.OPT_OUT))
    val charLimit = snapshot?.charLimit?.takeIf { it > 0 } ?: DEFAULT_MEMORY_CHAR_LIMIT
    val showSearch = memories.size >= MEMORY_SEARCH_THRESHOLD
    val partition = partition.narrowedTo(memories)
    val needle = if (showSearch) query.trim() else ""
    return copy(
        memories = memories,
        partition = partition,
        partitionOptions = partitionOptions(memories),
        agentOrdinals = agentLabelOrdinals(memories),
        showSearch = showSearch,
        visibleMemories = memories.filter { it.isIn(partition) && it.matches(needle) },
        memoriesEnabled = memoriesEnabled,
        preferenceLoadFailed = preferenceLoadFailed && memoriesEnabled == null,
        charLimit = charLimit,
        canRead = canRead,
        canCreate = role.canMemories(Permission.CREATE),
        canUpdate = role.canMemories(Permission.UPDATE),
        canOptOut = canOptOut,
        canOptOutWithoutList = canOptOutWithoutList,
        editor = editor?.validated(memories, charLimit)?.copy(
            tokenShare = editor.original?.let { memoryTokenShare(it, snapshot) },
        ),
    )
}

/**
 * [memory]'s [MemoryTokenShare], or null unless every input is there and meaningful: the totals
 * count the personal pool only, so an agent's entry has no share in them; a zero count or a missing
 * limit has nothing to say; and the room left can reach zero or below, since edits don't enforce
 * the limit.
 */
internal fun memoryTokenShare(memory: Memory, snapshot: MemoriesResponse?): MemoryTokenShare? {
    val tokens = memory.tokenCount?.takeIf { it > 0 } ?: return null
    val limit = snapshot?.tokenLimit ?: return null
    if (memory.agentId != null) return null
    val room = limit.toLong() - (snapshot.totalTokens ?: 0) + tokens
    if (room <= 0) return null
    // Half up, as web's Math.round.
    val percent = floor(tokens * PERCENT / room.toDouble() + HALF).toInt()
    return MemoryTokenShare(tokens = tokens, percent = percent)
}

private const val PERCENT = 100.0
private const val HALF = 0.5

private fun partitionOptions(memories: List<Memory>): List<MemoryPartitionOption>? {
    val agents = memories.mapNotNull { it.agentId }.distinct()
    if (agents.isEmpty()) return null
    val names = agents.associateWith { agentId -> agentName(memories, agentId) }
    val ordinals = agentLabelOrdinals(agents, names)
    return buildList {
        add(MemoryPartitionOption(MemoryPartition.All))
        add(MemoryPartitionOption(MemoryPartition.Personal))
        agents.forEach { agentId ->
            add(MemoryPartitionOption(MemoryPartition.Agent(agentId), agentName = names[agentId], ordinal = ordinals[agentId]))
        }
    }
}

private fun agentName(memories: List<Memory>, agentId: String): String? = memories.firstNotNullOfOrNull { memory ->
    memory.agentName?.takeIf { memory.agentId == agentId && it.isNotBlank() }
}

/**
 * A 1-based number for each agent whose label another agent's would repeat: two agents the server
 * can't name both read "Agent memory", and two agents may share a name. Agents with a label of
 * their own get none. Numbered in agentId order, not list order: the list is last-updated first,
 * so an edit would otherwise swap two agents' numbers between reloads.
 */
internal fun agentLabelOrdinals(memories: List<Memory>): Map<String, Int> {
    val agents = memories.mapNotNull { it.agentId }.distinct()
    return agentLabelOrdinals(agents, agents.associateWith { agentName(memories, it) })
}

private fun agentLabelOrdinals(agents: List<String>, names: Map<String, String?>): Map<String, Int> =
    agents.groupBy { names[it]?.trim()?.lowercase() }
        .values
        .filter { it.size > 1 }
        .flatMap { group -> group.sorted().mapIndexed { index, agentId -> agentId to index + 1 } }
        .toMap()

private fun Memory.isIn(partition: MemoryPartition): Boolean = when (partition) {
    MemoryPartition.All -> true
    MemoryPartition.Personal -> agentId == null
    is MemoryPartition.Agent -> agentId == partition.agentId
}

/** Substring over key and value, ignoring case. A blank needle matches everything. */
private fun Memory.matches(needle: String): Boolean =
    needle.isEmpty() || key.contains(needle, ignoreCase = true) || value.contains(needle, ignoreCase = true)

private fun MemoryEditorState.validated(
    memories: List<Memory>,
    charLimit: Int,
): MemoryEditorState {
    val trimmedKey = key.trim()
    val trimmedValue = value.trim()
    val keyError = when (mode) {
        MemoryEditorMode.View -> null
        MemoryEditorMode.Create -> memoryKeyProblem(key = key, memories = memories)
        // Validated in the memory's own partition: the same key may exist in another one.
        MemoryEditorMode.Edit -> memoryKeyProblem(
            key = key,
            memories = memories,
            agentId = original?.agentId,
            originalKey = original?.key,
        )
    } ?: MemoryKeyProblem.DUPLICATE.takeIf { mode != MemoryEditorMode.View && trimmedKey == conflictKey }
    val valueError = when {
        mode == MemoryEditorMode.View -> null
        trimmedValue.isEmpty() -> MemoryValueProblem.REQUIRED
        trimmedValue.length > charLimit -> MemoryValueProblem.TOO_LONG
        else -> null
    }
    val dirty = when (mode) {
        MemoryEditorMode.Create -> key.isNotBlank() || value.isNotBlank()
        MemoryEditorMode.Edit -> key != original?.key || value != original?.value
        MemoryEditorMode.View -> false
    }
    // An edit's Save needs something to send: a PATCH of the stored text still bumps updated_at and
    // moves the row to the top. Compared trimmed, as the request is, so a stray space is no change.
    val hasChange = mode != MemoryEditorMode.Edit ||
        original == null ||
        trimmedKey != original.key.trim() ||
        trimmedValue != original.value.trim()
    return copy(
        keyError = keyError,
        valueError = valueError,
        valueLength = trimmedValue.length,
        isDirty = dirty,
        canSave = mode != MemoryEditorMode.View && !isSaving && keyError == null && valueError == null && hasChange,
    )
}

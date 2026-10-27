package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_all_failed
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyEntry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeysUiState
import com.garfiec.librechat.feature.settings.state.providerkeys.fetchProviderKeyState
import com.garfiec.librechat.feature.settings.state.providerkeys.listedProviderKeyEndpoints
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/**
 * Backs the Provider API Keys list screen. Lists the endpoints [listedProviderKeyEndpoints] picks
 * (the ones the user can reach that take a user-provided value), fetches each one's key state, and
 * orchestrates the key sheet and revoke-all.
 *
 * After save/delete the VM re-runs `configRepository.fetchEndpoints()` and
 * `configRepository.fetchModels()` so the chat model selector picks up admin-side
 * changes (added/removed endpoints + key-dependent model lists).
 *
 * The open sheet's endpoint and its open counter live in [savedStateHandle], so the sheet comes
 * back after process death; the sheet's own draft is in its ViewModel's handle.
 */
@Suppress("TooManyFunctions")
class ProviderKeysViewModel(
    private val keyRepository: KeyRepository,
    private val configRepository: ConfigRepository,
    private val roleRepository: RoleRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProviderKeysUiState(
            pendingDialogEndpoint = savedStateHandle[KEY_PENDING_DIALOG],
            dialogOpenRequest = savedStateHandle[KEY_OPEN_REQUEST] ?: 0L,
        ),
    )
    val uiState: StateFlow<ProviderKeysUiState> = _uiState.asStateFlow()

    /**
     * Single cancel-and-restart guard for all entry-list refreshes. A fresh refresh always
     * wins over an in-flight stale one regardless of which caller initiated it.
     *
     * [pendingForceFor] / [pendingForceAll] accumulate the work the in-flight job was supposed
     * to do so a cancellation does NOT lose it. Example race: the reachable-endpoints collector
     * fires (`forceFor=emptySet`) while [onChildKeyChanged] is mid-save with
     * `forceFor=setOf(endpointName)` in flight; without merging, the just-mutated row would
     * fall back to its cached pre-save value. The pending sets are cleared only on successful
     * completion (the `finally` block is also reached on cancellation, where we keep them).
     */
    private var refreshJob: Job? = null
    private var pendingForceFor: Set<String> = emptySet()
    private var pendingForceAll: Boolean = false

    private var loadJob: Job? = null
    private var resumedOnce = false

    init {
        loadJob = viewModelScope.launch { loadConfigAndRefresh() }
        // Pick up admin-side endpoint changes, a spec change or an AGENTS permission change while
        // the screen is foregrounded. The first emission is what the initial load already used.
        viewModelScope.launch {
            listedProviderKeyEndpoints(configRepository, roleRepository)
                .map { it.orEmpty() }
                .distinctUntilChanged()
                .drop(1)
                .collect { refresh() }
        }
    }

    private suspend fun loadConfigAndRefresh() {
        if (configRepository.endpointConfigs.value.isEmpty()) {
            val result = configRepository.fetchEndpoints()
            if (result is Result.Error && configRepository.endpointConfigs.value.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, loadError = true) }
                return
            }
        }
        _uiState.update { it.copy(loadError = false) }
        refresh(forceAll = true).join()
    }

    /** Retries a failed config load. */
    fun retry() {
        if (loadJob?.isActive == true) return
        _uiState.update { it.copy(isLoading = true, loadError = false) }
        loadJob = viewModelScope.launch { loadConfigAndRefresh() }
    }

    /**
     * Re-checks one row whose key-state GET failed. The row keeps saying it couldn't check, with
     * its Retry busy, until the answer lands ([ProviderKeyEntry.retrying]). Ignored while that
     * row's retry is already out.
     */
    fun retry(endpointName: String) {
        val state = _uiState.value
        val row = (state.entries + listOfNotNull(state.unlistedDialogEntry))
            .firstOrNull { it.endpointName == endpointName } ?: return
        if (row.retrying) return
        updateRows { if (it.endpointName == endpointName) it.copy(retrying = true) else it }
        refresh(forceFor = setOf(endpointName))
    }

    /**
     * ON_RESUME: re-reads every row's key state, quietly — no spinner, and nothing about the sheet
     * changes (the user may be back from copying a key into it). The first resume is the initial
     * load's.
     */
    fun refreshOnReturn() {
        if (!resumedOnce) {
            resumedOnce = true
            return
        }
        if (loadJob?.isActive == true) return
        // A refresh in flight may be a single row's retry; this one merges into it (refresh keeps
        // the cancelled job's pending work) rather than being dropped.
        if (_uiState.value.loadError) {
            loadJob = viewModelScope.launch { loadConfigAndRefresh() }
            return
        }
        refresh(forceAll = true)
    }

    /**
     * The open sheet's endpoint when [listed] doesn't include it (a deep link to an endpoint the
     * list leaves out), tracked alongside the rows so the sheet's header has a key state.
     */
    private fun unlistedDialogEntry(listed: List<ProviderKeyEntry>): ProviderKeyEntry? {
        val name = _uiState.value.pendingDialogEndpoint ?: return null
        val configs = configRepository.endpointConfigs.value
        if (configs.isEmpty() || listed.any { it.endpointName == name }) return null
        return ProviderKeyEntry(endpointName = name, config = configs[name] ?: EndpointConfig(name = name))
    }

    private fun currentEntries(): List<ProviderKeyEntry> =
        listedProviderKeyEndpoints(
            configRepository.endpointConfigs.value,
            configRepository.startupConfig.value,
            roleRepository.userPermissions.value,
        ).orEmpty().map { ProviderKeyEntry(endpointName = it.name, config = it.config) }

    /**
     * Single fan-out helper used by all entry-list refresh paths.
     *
     * @param forceFor endpoint names whose [KeyState] should be re-fetched even if a cached
     *   value is available. Used by [onChildKeyChanged] to refresh the just-mutated row.
     * @param forceAll when true, re-fetch every entry's [KeyState] from the server,
     *   ignoring cached values. Used by initial load, return-to-screen and revoke-all success.
     *
     * Net-new endpoints are always fetched regardless of [forceFor] / [forceAll] — otherwise
     * they'd sit on `KeyState.Loading` forever. Existing rows fall through to the cached value
     * when neither flag selects them. A failed fetch keeps the row's last answer; a row with no
     * answer yet is marked [ProviderKeyEntry.fetchFailed]. The open sheet's endpoint is tracked the
     * same way when the list leaves it out ([ProviderKeysUiState.unlistedDialogEntry]).
     */
    private fun refresh(forceFor: Set<String> = emptySet(), forceAll: Boolean = false): Job {
        refreshJob?.cancel()
        // Merge in the new caller's request. If a prior in-flight job was just cancelled, its
        // requested work is still in `pendingForceFor` / `pendingForceAll` and will be picked
        // up by this restart. Cleared at the end of the launched block on successful completion.
        pendingForceFor = pendingForceFor + forceFor
        pendingForceAll = pendingForceAll || forceAll
        val mergedForceFor = pendingForceFor
        val mergedForceAll = pendingForceAll
        val job = viewModelScope.launch {
            val filtered = currentEntries()
            val unlisted = unlistedDialogEntry(filtered)
            val tracked = filtered + listOfNotNull(unlisted)
            val previous = _uiState.value
            val previousByName = (previous.entries + listOfNotNull(previous.unlistedDialogEntry))
                .associateBy { it.endpointName }

            // Surface initial-load progress only when the previous list was empty; otherwise
            // we'd unnecessarily flip the spinner each time the configs collector fires.
            if (previous.entries.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, entries = filtered, error = null) }
            }

            val toFetch = tracked.filter { entry ->
                mergedForceAll ||
                    entry.endpointName in mergedForceFor ||
                    !previousByName.containsKey(entry.endpointName)
            }
            val freshStates = coroutineScope {
                toFetch.map { entry ->
                    async { entry.endpointName to keyRepository.fetchProviderKeyState(entry.endpointName, entry.config) }
                }.awaitAll()
            }.toMap()

            fun merged(entry: ProviderKeyEntry): ProviderKeyEntry {
                val last = previousByName[entry.endpointName]
                val fresh = freshStates[entry.endpointName]
                return when {
                    entry.endpointName !in freshStates -> entry.copy(
                        keyState = last?.keyState ?: KeyState.Loading,
                        fetchFailed = last?.fetchFailed ?: false,
                        retrying = last?.retrying ?: false,
                    )
                    fresh != null -> entry.copy(keyState = fresh)
                    last != null && last.keyState !is KeyState.Loading -> entry.copy(keyState = last.keyState)
                    else -> entry.copy(keyState = KeyState.Loading, fetchFailed = true)
                }
            }

            _uiState.update {
                it.copy(isLoading = false, entries = filtered.map(::merged), unlistedDialogEntry = unlisted?.let(::merged))
            }
            // Clear pending only when we reach the end without cancellation. A
            // CancellationException unwinds before this line, so the merged work survives
            // for the next caller's restart.
            pendingForceFor = emptySet()
            pendingForceAll = false
        }
        refreshJob = job
        return job
    }

    /**
     * An explicit open of the key sheet: a row tap or a deep link. Bumps
     * [ProviderKeysUiState.dialogOpenRequest], which is what tells the sheet to start from a blank
     * form; anything else that re-shows it (recomposition, resume, restoration) keeps the draft.
     */
    fun openDialog(endpointName: String) {
        val openRequest = _uiState.value.dialogOpenRequest + 1
        savedStateHandle[KEY_PENDING_DIALOG] = endpointName
        savedStateHandle[KEY_OPEN_REQUEST] = openRequest
        _uiState.update { it.copy(pendingDialogEndpoint = endpointName, dialogOpenRequest = openRequest) }
        // An endpoint the list leaves out has no row to read from: fetch its state for the sheet.
        // Before the configs load, the load's own refresh picks it up.
        val state = _uiState.value
        if (state.dialogEntry == null && configRepository.endpointConfigs.value.isNotEmpty()) {
            refresh(forceFor = setOf(endpointName))
        }
    }

    fun dismissDialog() {
        savedStateHandle[KEY_PENDING_DIALOG] = null
        _uiState.update { it.copy(pendingDialogEndpoint = null, unlistedDialogEntry = null) }
    }

    fun showRevokeAllConfirm() {
        _uiState.update { it.copy(showRevokeAllConfirm = true) }
    }

    /** Ignored while the revoke is in flight: the confirmation stays up until the server answers. */
    fun dismissRevokeAllConfirm() {
        _uiState.update { if (it.isRevokingAll) it else it.copy(showRevokeAllConfirm = false) }
    }

    /**
     * After a child dialog mutates a key (save or revoke), refresh the just-mutated row
     * and pick up any admin-side endpoint changes.
     *
     * Concurrency: [refresh] cancels any in-flight job but accumulates the requested
     * `forceFor` / `forceAll` work into [pendingForceFor] / [pendingForceAll] so the
     * just-mutated row is guaranteed to be re-fetched even if the reachable-endpoints
     * collector cancels this call mid-flight (e.g. when `fetchEndpoints()` produces a
     * differing config map).
     *
     * The two config fetches finish even if this ViewModel is cleared meanwhile: a save reached
     * from chat pops this screen at once, and the chat's model list depends on what they load.
     *
     * [knownState] is what the mutation left on the server. The row shows it at once, before the
     * config fetches and the re-read, so a re-read that fails keeps that answer rather than the
     * pre-mutation one. Null (unknown) puts the row back to loading, so a failed re-read reads
     * "Couldn't check".
     */
    fun onChildKeyChanged(endpointName: String, knownState: KeyState? = null) {
        applyKnownStates { name -> if (name == endpointName) knownState ?: KeyState.Loading else null }
        viewModelScope.launch {
            withContext(NonCancellable) {
                configRepository.fetchEndpoints()
                configRepository.fetchModels()
            }
            refresh(forceFor = setOf(endpointName))
        }
    }

    /**
     * Revokes every stored key. Single-flight: a second call while one is running is dropped. The
     * confirmation stays up, showing progress, until the server answers; a failure shows the
     * translated message only, never the server's text.
     */
    fun revokeAll() {
        if (_uiState.value.isRevokingAll) return
        _uiState.update { it.copy(isRevokingAll = true, showRevokeAllConfirm = true) }
        viewModelScope.launch {
            when (keyRepository.deleteAllKeys()) {
                is Result.Success -> {
                    // Every row is known to be empty now; a failed re-read below keeps that.
                    applyKnownStates { KeyState.Unset }
                    _uiState.update { it.copy(isRevokingAll = false, showRevokeAllConfirm = false) }
                    configRepository.fetchEndpoints()
                    configRepository.fetchModels()
                    refresh(forceAll = true)
                }
                is Result.Error -> _uiState.update {
                    it.copy(
                        isRevokingAll = false,
                        showRevokeAllConfirm = false,
                        error = Res.string.provider_keys_revoke_all_failed,
                    )
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Puts the state a mutation is known to have left into the rows [stateFor] names (non-null),
     * clearing their failed marks. Cancels a refresh in flight first: it may hold a pre-mutation
     * answer it would write over this one. Its pending work is kept, and the caller's re-read
     * picks it up.
     */
    private fun applyKnownStates(stateFor: (endpointName: String) -> KeyState?) {
        refreshJob?.cancel()
        updateRows { entry ->
            stateFor(entry.endpointName)
                ?.let { entry.copy(keyState = it, fetchFailed = false, retrying = false) }
                ?: entry
        }
    }

    /** Applies [transform] to every tracked row: the listed ones and the open sheet's unlisted one. */
    private fun updateRows(transform: (ProviderKeyEntry) -> ProviderKeyEntry) {
        _uiState.update { state ->
            state.copy(entries = state.entries.map(transform), unlistedDialogEntry = state.unlistedDialogEntry?.let(transform))
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun consumeTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }

    /** A message for the list's snackbar, resolved by the screen, so the sender never suspends for it. */
    fun emitTransientMessage(message: StringResource) {
        _uiState.update { it.copy(transientMessage = message) }
    }

    private companion object {
        const val KEY_PENDING_DIALOG = "provider_keys_pending_dialog_endpoint"
        const val KEY_OPEN_REQUEST = "provider_keys_dialog_open_request"
    }
}

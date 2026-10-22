package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.skills.SkillViewer
import com.garfiec.librechat.core.data.skills.isSkillOwner
import com.garfiec.librechat.core.data.skills.resolveSkillActive
import com.garfiec.librechat.core.data.skills.toSkillViewer
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.config.skillsDefaultActiveOnShare
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.hasAccessStrictOrDenied
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.model.response.SkillImportFailedResponse
import com.garfiec.librechat.core.model.response.SkillListResponse
import com.garfiec.librechat.feature.skills.SkillCategoriesLoader
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.garfiec.librechat.feature.skills.components.PickedDocument
import com.garfiec.librechat.feature.skills.skillError
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * One list row: the skill plus what is true of it for the viewer.
 *
 * [isActive] is null while it can't be known yet (the states map or the viewer hasn't loaded), so
 * a row never flashes "Off" before the inputs arrive.
 */
@Immutable
data class SkillRowUi(
    val summary: SkillSummary,
    val isActive: Boolean?,
    val isOwnedByViewer: Boolean,
)

@Immutable
data class SkillsListUiState(
    val skills: List<SkillSummary> = emptyList(),
    /** [skills] with the viewer's active state and ownership per row, in the same order. */
    val rows: List<SkillRowUi> = emptyList(),
    /** True from the start: the first frame is "loading", never a flash of the empty state. */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    /**
     * The last replace-load failed. Drawn only while nothing is listed; with skills on screen the
     * failure also goes out as [SkillsListEvent.LoadFailed].
     */
    val error: SkillError? = null,
    val searchQuery: String = "",
    /** The query the listed [skills] answer. Runs behind [searchQuery] while a search is in flight. */
    val shownQuery: String = "",
    val hasMore: Boolean = false,
    val cursor: String? = null,
    /**
     * The last next-page fetch failed. Auto-paging stops until [SkillsListViewModel.retryLoadMore]
     * or a reload: otherwise a failing page flips `isLoadingMore` back to false with `hasMore`
     * still true, the scroll trigger re-fires, and the screen requests the page forever.
     */
    val loadMoreFailed: Boolean = false,
    /** Fail-CLOSED: only true when SKILLS.CREATE is explicitly granted. Hides the
     *  create + import affordances unless the server would allow it. */
    val canCreate: Boolean = false,
    val isImporting: Boolean = false,
    /**
     * The last import was refused with a per-file failure report (v0.8.8-rc4). Held apart from
     * [error] on purpose: the list reload that follows clears [error], and the failed paths are the
     * only place the user learns which files their archive lost.
     */
    val importFailure: SkillImportFailedResponse? = null,
    /** Server category presets, for the cards' category labels. Empty until (or unless) loaded. */
    val categories: List<Category> = emptyList(),
) {
    /** A search is typed or still shown, so an empty list means "no results", not "no skills". */
    val isSearching: Boolean get() = searchQuery.isNotBlank() || shownQuery.isNotEmpty()

    /** The search field earns its place once there are a few skills to sift, and stays while searching. */
    val showSearch: Boolean get() = skills.size >= SEARCH_MIN_SKILLS || isSearching

    private companion object {
        const val SEARCH_MIN_SKILLS = 5
    }
}

/** One-shot failures the screen shows as a snackbar while the list stays on screen. */
sealed interface SkillsListEvent {
    /** A refresh, quiet reload or search over a non-empty list failed. Retry refreshes. */
    data class LoadFailed(val error: SkillError) : SkillsListEvent

    /** The next page failed. Retry asks for it again. */
    data object PageFailed : SkillsListEvent

    /**
     * An import failed without a per-file report. [canRetry] is false for a 4xx, which the same
     * file would get again; Retry re-sends the file that was picked.
     */
    data class ImportFailed(val error: SkillError, val canRetry: Boolean) : SkillsListEvent

    /** The server's non-blocking notes on a skill the editor just saved, already user-facing text. */
    data class SaveWarnings(val messages: List<String>) : SkillsListEvent
}

class SkillsListViewModel(
    private val skillsRepository: SkillsRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val configRepository: ConfigRepository,
    private val saveNotices: SkillSaveNotices,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsListUiState())
    val uiState: StateFlow<SkillsListUiState> = _uiState.asStateFlow()

    // A buffered channel, sent to without suspending. A SharedFlow drops what is emitted while no
    // collector runs, and the screen's collector is gone whenever the list isn't composed (a detail
    // page pushed over it, an activity recreation): an import that fails then would vanish with
    // its Retry. Save warnings are taken on resume, before the collector restarts, for the same
    // reason. Not suspending keeps a load or page job from waiting on the snackbar being shown.
    private val _events = Channel<SkillsListEvent>(Channel.BUFFERED)
    val events: Flow<SkillsListEvent> = _events.receiveAsFlow()

    /** The file the last failed import sent, kept for [retryImport]. */
    private var lastImport: PickedDocument? = null

    /** The category presets, once per screen; a failure retries with the next replace-load. */
    private val categories = SkillCategoriesLoader(configRepository, viewModelScope) {
        _uiState.value = _uiState.value.copy(categories = it)
    }

    private var searchJob: Job? = null

    /** The in-flight replace-load (first page, refresh, quiet reload, search). One at a time. */
    private var fetchJob: Job? = null

    /** The mode of the last replace-load started, read only while [fetchJob] is active. */
    private var fetchMode = ReplaceMode.FIRST

    /** The in-flight next-page fetch. Cancelled by every replace-load. */
    private var pageJob: Job? = null

    /**
     * Bumped by every replace-load. A result applies only while its generation is current, so a
     * page that was in flight when the list was replaced is dropped even if it was already past
     * the point where cancellation could stop it. Appending it would mix two queries' results and
     * can repeat ids, which crashes a keyed lazy list.
     */
    private var generation = 0
    private var hasLoadedOnce = false

    /**
     * A [loadMore] arrived while a replace-load ran. The screen's scroll trigger fires once per
     * change, so a request dropped here would never come again when the reload lands with the
     * same layout; the reload asks for the page on its behalf instead.
     */
    private var pendingLoadMore = false

    // Row inputs; see [SkillRowUi]. None depends on the query, so a search doesn't refetch them.
    private var viewer: SkillViewer? = null
    private var viewerJob: Job? = null
    private var states: Map<String, Boolean>? = null
    private var statesJob: Job? = null
    private var defaultActiveOnShare = false

    init {
        observeRole()
        observeConfig()
        // Initial + on-return load is driven by the screen's ON_RESUME effect so a
        // skill created in the editor shows on return (Nav3 retains this VM, so
        // init runs only once and wouldn't re-fetch). See SkillsListScreen.
    }

    /**
     * Loads on screen resume: the first resume does the initial load; later ones (a return from
     * the editor or the detail page) reload quietly, keeping as many skills as were loaded so the
     * list doesn't jump back to page one.
     *
     * A load or page still in flight was sent before the user left, so it can't hold what they
     * changed meanwhile (a skill created, a toggle on the detail page): it is replaced, not waited
     * for. A load restarts in its own mode, so a pull-to-refresh keeps its indicator; a page is
     * asked for again once the reload lands.
     */
    fun refreshOnReturn() {
        deliverSaveWarnings()
        if (!hasLoadedOnce) {
            loadFirstPage()
            return
        }
        val inFlightMode = fetchMode.takeIf { fetchJob?.isActive == true }
        if (pageJob?.isActive == true) pendingLoadMore = true
        // A toggle on the detail page changes the states map, so a return reads it again.
        loadRowInputs()
        replaceLoad(
            when {
                // Nothing on screen (the first load failed or found nothing): a visible load, so the
                // error or the spinner holds instead of flashing the empty state while it runs.
                _uiState.value.skills.isEmpty() -> ReplaceMode.FIRST
                inFlightMode != null -> inFlightMode
                else -> ReplaceMode.QUIET
            },
        )
    }

    private fun deliverSaveWarnings() {
        val messages = saveNotices.takeAll()
        if (messages.isNotEmpty()) _events.trySend(SkillsListEvent.SaveWarnings(messages))
    }

    /** Fail-CLOSED create gate (mutations hide unless explicitly permitted). */
    private fun observeRole() {
        viewModelScope.launch {
            roleRepository.userPermissions.collect {
                _uiState.value = _uiState.value.copy(
                    canCreate = it.hasAccessStrictOrDenied(PermissionType.SKILLS, Permission.CREATE),
                )
            }
        }
    }

    private fun observeConfig() {
        viewModelScope.launch {
            configRepository.startupConfig.collect { config ->
                defaultActiveOnShare = skillsDefaultActiveOnShare(config?.interfaceConfig?.skills)
                recomputeRows()
            }
        }
    }

    /** The first load, and the error banner's Retry. */
    fun loadFirstPage() {
        replaceLoad(ReplaceMode.FIRST)
        loadRowInputs()
    }

    fun refresh() {
        replaceLoad(ReplaceMode.REFRESH)
        loadRowInputs()
    }

    fun loadMore() {
        if (fetchJob?.isActive == true) {
            pendingLoadMore = true
            return
        }
        startPage()
    }

    private fun startPage() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore || state.loadMoreFailed) return
        val gen = generation
        val cursor = state.cursor
        // The cursor and the rows on screen belong to the query they answer, not to one typed since
        // whose debounce hasn't run: mixing the two would append the new query's matches to the old.
        val query = state.shownQuery.takeIf { it.isNotEmpty() }
        _uiState.value = state.copy(isLoadingMore = true)
        pageJob = viewModelScope.launch {
            val result = skillsRepository.listSkills(limit = PAGE_SIZE, search = query, cursor = cursor)
            if (gen != generation) return@launch
            _uiState.value = when (result) {
                is Result.Success -> {
                    val shown = _uiState.value.skills
                    val merged = (shown + result.data.skills).distinctBy { it.id }
                    // A page that adds no new skill would be asked for again and again by the
                    // scroll trigger; treat it as the end of the list.
                    val advanced = merged.size > shown.size
                    _uiState.value.copy(
                        skills = merged,
                        hasMore = result.data.hasMore && advanced,
                        cursor = if (advanced) result.data.after else null,
                        isLoadingMore = false,
                    )
                }
                is Result.Error -> _uiState.value.copy(isLoadingMore = false, loadMoreFailed = true)
                is Result.Loading -> return@launch
            }
            recomputeRows()
            if (result is Result.Error) _events.trySend(SkillsListEvent.PageFailed)
        }
    }

    /** Clears a failed page and asks for it again. */
    fun retryLoadMore() {
        _uiState.value = _uiState.value.copy(loadMoreFailed = false)
        loadMore()
    }

    /** Imports a skill from a picked .md/.zip/.skill and refreshes on success. A per-file report
     *  lands in [SkillsListUiState.importFailure]; any other failure is a [SkillsListEvent.ImportFailed]. */
    fun importSkill(doc: PickedDocument) {
        if (_uiState.value.isImporting) return
        lastImport = null
        viewModelScope.launch {
            // [SkillsListUiState.error] stays: an import over a failed first load keeps that banner until
            // the reload after the import replaces it.
            _uiState.value = _uiState.value.copy(isImporting = true, importFailure = null)
            when (val result = skillsRepository.importSkill(doc.bytes, doc.filename, doc.mimeType)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(isImporting = false)
                    replaceLoad(ReplaceMode.FIRST)
                }
                is Result.Error -> {
                    val failure = SkillImportFailedResponse.from((result.exception as? ApiException)?.body)
                    if (failure != null) {
                        _uiState.value = _uiState.value.copy(isImporting = false, importFailure = failure)
                        // A failed rollback leaves the partial skill listed server-side, and the
                        // other two codes can change the list too; reload rather than trust it.
                        replaceLoad(ReplaceMode.FIRST)
                    } else {
                        // A snackbar, not [SkillsListUiState.error]: that one swaps an empty list
                        // for a banner whose Retry reloads the list, not the import.
                        val status = (result.exception as? ApiException)?.statusCode
                        val canRetry = status == null || status !in CLIENT_ERROR_STATUSES
                        if (canRetry) lastImport = doc
                        _uiState.value = _uiState.value.copy(isImporting = false)
                        _events.trySend(
                            SkillsListEvent.ImportFailed(skillError(SkillErrorKind.IMPORT_SKILL, result.message), canRetry),
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    /** Re-sends the file the last failed import sent, when that failure was worth retrying. */
    fun retryImport() {
        lastImport?.let(::importSkill)
    }

    fun dismissImportFailure() {
        _uiState.value = _uiState.value.copy(importFailure = null)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            replaceLoad(ReplaceMode.FIRST)
        }
    }

    private enum class ReplaceMode { FIRST, REFRESH, QUIET }

    /**
     * Replaces the list from the top. Cancels any in-flight load or page (see [generation]).
     *
     * [ReplaceMode.QUIET] shows no spinner and refetches as many skills as were loaded: the first
     * page and then, past [PAGE_SIZE] (the server's maximum limit), the following pages by cursor
     * until the count is reached or the server runs out. The cursor kept is the last page's, so
     * the next scroll continues right after what is shown.
     */
    private fun replaceLoad(mode: ReplaceMode) {
        hasLoadedOnce = true
        fetchMode = mode
        fetchJob?.cancel()
        pageJob?.cancel()
        val gen = ++generation
        val target = if (mode == ReplaceMode.QUIET) _uiState.value.skills.size else 0
        _uiState.value = _uiState.value.copy(
            // A pull-to-refresh over the initial spinner keeps the spinner; a quiet reload shows none.
            isLoading = when (mode) {
                ReplaceMode.FIRST -> true
                ReplaceMode.REFRESH -> _uiState.value.isLoading
                ReplaceMode.QUIET -> false
            },
            isRefreshing = mode == ReplaceMode.REFRESH,
            isLoadingMore = false,
            loadMoreFailed = false,
            // A pull over the error banner keeps the banner under the refresh indicator; clearing it
            // would show "No skills yet" until the result lands.
            error = if (mode == ReplaceMode.REFRESH) _uiState.value.error else null,
        )
        val query = currentQuery()
        fetchJob = viewModelScope.launch {
            val result = fetchFromTop(target)
            if (gen != generation) return@launch
            val hadSkills = _uiState.value.skills.isNotEmpty()
            val pageRequested = pendingLoadMore
            pendingLoadMore = false
            _uiState.value = when (result) {
                is Result.Success -> _uiState.value.copy(
                    skills = result.data.skills,
                    shownQuery = query.orEmpty(),
                    hasMore = result.data.hasMore,
                    cursor = result.data.after,
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                )
                is Result.Error -> _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = skillError(SkillErrorKind.LOAD_SKILLS, result.message),
                )
                is Result.Loading -> return@launch
            }
            recomputeRows()
            // With skills listed the error banner isn't drawn, so the failure needs a voice of its own.
            val error = _uiState.value.error
            if (result is Result.Error && hadSkills && error != null) {
                _events.trySend(SkillsListEvent.LoadFailed(error))
            }
            if (result is Result.Success) {
                dropStaleLoadFailures()
                if (pageRequested) startPage()
            }
        }
        categories.load()
    }

    /**
     * A reload that landed answers every load or page failure still waiting in [_events] (sent while
     * the list was off screen); showing one after the list it failed to fetch is already back would
     * offer a Retry for nothing. Other events go back in their order.
     */
    private fun dropStaleLoadFailures() {
        val kept = generateSequence { _events.tryReceive().getOrNull() }
            .filterNot { it is SkillsListEvent.LoadFailed || it is SkillsListEvent.PageFailed }
            .toList()
        kept.forEach { _events.trySend(it) }
    }

    /** Pages from the top until [atLeast] skills are in hand (one page when 0), as one response. */
    private suspend fun fetchFromTop(atLeast: Int): Result<SkillListResponse> {
        val query = currentQuery()
        val collected = LinkedHashMap<String, SkillSummary>()
        var cursor: String? = null
        while (true) {
            val page = when (val result = skillsRepository.listSkills(limit = PAGE_SIZE, search = query, cursor = cursor)) {
                is Result.Success -> result.data
                is Result.Error -> return result
                is Result.Loading -> return result
            }
            val before = collected.size
            page.skills.forEach { collected.getOrPut(it.id) { it } }
            cursor = page.after
            // A page that adds nothing new would repeat forever; stop there, and report the end of
            // the list so the next scroll doesn't ask for the same page again.
            if (collected.size == before) {
                return Result.Success(SkillListResponse(collected.values.toList(), hasMore = false, after = null))
            }
            if (!page.hasMore || cursor == null || collected.size >= atLeast) {
                return Result.Success(SkillListResponse(collected.values.toList(), page.hasMore, page.after))
            }
        }
    }

    /**
     * Trimmed: the server builds a case-insensitive regex from the text as is, so a trailing space
     * left by the keyboard would have to match literally.
     */
    private fun currentQuery(): String? = _uiState.value.searchQuery.trim().takeIf { it.isNotEmpty() }

    /**
     * The states map, on open, refresh and return (so a toggle on the detail page shows), and the
     * viewer until one with an id arrives. One fetch of each at a time: a newer states GET replaces
     * an older one in flight, which could otherwise land last with the map from before. Failures
     * keep the last known values; a first failure leaves the rows' active state unknown rather
     * than guessed.
     */
    private fun loadRowInputs() {
        statesJob?.cancel()
        statesJob = viewModelScope.launch {
            val result = skillsRepository.getSkillStates()
            if (result is Result.Success) {
                states = result.data
                recomputeRows()
            }
        }
        // An id-less user counts as not loaded: without the id no row can resolve its state.
        if (viewer?.userId == null && viewerJob?.isActive != true) {
            viewerJob = viewModelScope.launch {
                val result = userRepository.getUser()
                if (result is Result.Success) {
                    viewer = result.data.toSkillViewer()
                    recomputeRows()
                }
            }
        }
    }

    private fun recomputeRows() {
        val state = _uiState.value
        val userId = viewer?.userId
        val snapshot = states
        val rows = state.skills.map { skill ->
            SkillRowUi(
                summary = skill,
                isActive = if (snapshot != null && userId != null) {
                    resolveSkillActive(skill.id, skill.author, skill.source, snapshot, userId, defaultActiveOnShare)
                } else {
                    null
                },
                isOwnedByViewer = isSkillOwner(skill.author, userId),
            )
        }
        _uiState.value = state.copy(rows = rows)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L

        /** Upstream clamps `limit` to 1..100; one full page per request. */
        const val PAGE_SIZE = 100

        val CLIENT_ERROR_STATUSES = 400..499
    }
}

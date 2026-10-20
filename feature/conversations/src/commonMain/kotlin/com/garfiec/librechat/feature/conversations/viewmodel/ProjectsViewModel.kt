package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Project index orderings, as the `GET /api/projects` `sortBy`/`sortDirection` pair. */
enum class ProjectSort(val wire: String, val direction: String) {
    ACTIVITY("lastConversationAt", "desc"),
    CREATED("createdAt", "desc"),
    NAME("name", "asc"),
}

@Immutable
data class ProjectsUiState(
    val projects: List<ChatProject> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    /** Lives only as long as this ViewModel: there is no saved sort preference yet. */
    val sort: ProjectSort = ProjectSort.ACTIVITY,
    val searchQuery: String = "",
    /** The trimmed search the shown list answers. Trails [searchQuery] until its results land. */
    val shownQuery: String = "",
)

sealed interface ProjectsEvent {
    data class ShowError(val message: String) : ProjectsEvent

    /** A project was just created here; the screen opens it so the next step is a chat inside it. */
    data class OpenProject(val projectId: String, val projectName: String) : ProjectsEvent
}

/** Browse-all-folders index (v0.8.7). Server is the source of truth — no local cache. */
class ProjectsViewModel(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProjectsEvent>()
    val events: SharedFlow<ProjectsEvent> = _events.asSharedFlow()

    private val actions = ProjectActionsDelegate(
        scope = viewModelScope,
        projectRepository = projectRepository,
        emitError = { _events.emit(ProjectsEvent.ShowError(it)) },
        onCreated = { _events.emit(ProjectsEvent.OpenProject(it.id, it.name)) },
    )

    private var searchJob: Job? = null

    /** The trimmed search the current first page asks for; set when it starts, not when it lands. */
    private var requestedQuery = ""

    private val pager = CursorPager(
        scope = viewModelScope,
        idOf = ChatProject::id,
        onError = { message -> _uiState.update { it.copy(error = message) } },
    ) { cursor, limit ->
        val state = _uiState.value
        when (
            val result = projectRepository.listProjects(
                cursor = cursor,
                sortBy = state.sort.wire,
                sortDirection = state.sort.direction,
                search = requestedQuery.ifBlank { null },
                limit = limit,
            )
        ) {
            is Result.Success -> Result.Success(CursorPage(result.data.projects, result.data.nextCursor))
            is Result.Error -> result
            is Result.Loading -> result
        }
    }

    init {
        viewModelScope.launch {
            combine(pager.items, pager.state, ::Pair).collect { (projects, paging) ->
                val settled = !paging.isLoading && !paging.isRefreshing
                _uiState.update {
                    it.copy(
                        projects = projects,
                        shownQuery = if (settled) requestedQuery else it.shownQuery,
                        isLoading = paging.isLoading,
                        isRefreshing = paging.isRefreshing,
                        nextCursor = paging.nextCursor,
                        hasMore = paging.nextCursor != null,
                    )
                }
            }
        }
        load()
        // A project changed here or elsewhere, or a chat was filed into one.
        viewModelScope.launch {
            projectRepository.revision.drop(1).collect { pager.reloadQuietly() }
        }
    }

    fun load() {
        // A search still waiting out its debounce would repeat this request.
        searchJob?.cancel()
        loadFirstPage()
    }

    private fun loadFirstPage() {
        requestedQuery = _uiState.value.searchQuery.trim()
        pager.load()
    }

    fun loadMore() = pager.loadMore()

    fun refresh() = pager.refresh()

    /**
     * Back on the index, e.g. from a chat started from a card, which may have reordered the list
     * since. Skips while a first page is already loading.
     */
    fun refreshOnReturn() {
        if (!pager.isLoadingFirstPage) pager.reloadQuietly()
    }

    fun setSort(sort: ProjectSort) {
        if (sort == _uiState.value.sort) return
        _uiState.update { it.copy(sort = sort) }
        load()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadFirstPage()
        }
    }

    fun createProject(name: String, description: String) = actions.create(name, description)

    fun editProject(projectId: String, name: String, description: String) = actions.edit(projectId, name, description)

    fun deleteProject(projectId: String) = actions.delete(projectId)

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 500L
    }
}

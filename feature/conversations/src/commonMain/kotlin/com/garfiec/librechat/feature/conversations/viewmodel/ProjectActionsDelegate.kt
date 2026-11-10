package com.garfiec.librechat.feature.conversations.viewmodel

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.project_delete_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Shared Chat Projects CRUD (create/rename/delete) used by both [ProjectsViewModel] (the browse-all
 * index) and the drawer's folder section. Hosts don't reload from here: every successful change bumps
 * [ProjectRepository.revision], which each host collects, so the drawer also catches a change made on
 * the index and vice versa. A create or edit reports through `onResult`, which its sheet waits on;
 * [emitError] routes a failed delete to the host's one-shot event type, and [onCreated] hands the host
 * a project it just made (the index opens it).
 */
class ProjectActionsDelegate(
    private val scope: CoroutineScope,
    private val projectRepository: ProjectRepository,
    private val emitError: suspend (message: StringResource) -> Unit,
    private val onCreated: suspend (ChatProject) -> Unit = {},
) {

    /** A blank [description] creates the project without one. */
    fun create(name: String, description: String, onResult: (saved: Boolean) -> Unit) {
        scope.launch {
            when (val result = projectRepository.createProject(name, description.ifBlank { null })) {
                is Result.Success -> {
                    onResult(true)
                    onCreated(result.data)
                }
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to create project" }
                    onResult(false)
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Sends [description] as-is: the server stores `""` as "no description", so a cleared field clears
     * it. (Null would mean "leave unchanged".)
     */
    fun edit(projectId: String, name: String, description: String, onResult: (saved: Boolean) -> Unit) {
        scope.launch {
            when (val result = projectRepository.updateProject(projectId, name = name, description = description)) {
                is Result.Success -> onResult(true)
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to update project" }
                    onResult(false)
                }
                is Result.Loading -> Unit
            }
        }
    }

    fun delete(projectId: String) {
        scope.launch {
            when (val result = projectRepository.deleteProject(projectId)) {
                is Result.Success -> Unit
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to delete project" }
                    emitError(Res.string.project_delete_failed)
                }
                is Result.Loading -> Unit
            }
        }
    }
}

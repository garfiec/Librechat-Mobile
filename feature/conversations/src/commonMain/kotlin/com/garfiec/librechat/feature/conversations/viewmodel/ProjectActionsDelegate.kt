package com.garfiec.librechat.feature.conversations.viewmodel

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shared Chat Projects CRUD (create/rename/delete) used by both [ProjectsViewModel] (the browse-all
 * index) and the drawer's folder section. Hosts don't reload from here: every successful change bumps
 * [ProjectRepository.revision], which each host collects, so the drawer also catches a change made on
 * the index and vice versa. [emitError] routes a failure to the host's one-shot event type, and
 * [onCreated] hands the host a project it just made (the index opens it).
 */
class ProjectActionsDelegate(
    private val scope: CoroutineScope,
    private val projectRepository: ProjectRepository,
    private val emitError: suspend (message: String) -> Unit,
    private val onCreated: suspend (ChatProject) -> Unit = {},
) {

    /** A blank [description] creates the project without one. */
    fun create(name: String, description: String = "") {
        scope.launch {
            when (val result = projectRepository.createProject(name, description.ifBlank { null })) {
                is Result.Success -> onCreated(result.data)
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to create project" }
                    emitError("Failed to create project")
                }
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Sends [description] as-is: the server stores `""` as "no description", so a cleared field clears
     * it. (Null would mean "leave unchanged".)
     */
    fun edit(projectId: String, name: String, description: String) {
        scope.launch {
            when (val result = projectRepository.updateProject(projectId, name = name, description = description)) {
                is Result.Success -> Unit
                is Result.Error -> {
                    Logger.e(result.exception) { "Failed to update project" }
                    emitError("Failed to update project")
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
                    emitError("Failed to delete project")
                }
                is Result.Loading -> Unit
            }
        }
    }
}

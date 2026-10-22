package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.data.skills.resolveSkillActive
import com.garfiec.librechat.core.data.skills.toSkillViewer
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.config.skillsDefaultActiveOnShare
import com.garfiec.librechat.feature.chat.util.CatalogSkill
import com.garfiec.librechat.feature.chat.util.toCatalogSkill
import com.garfiec.librechat.feature.chat.viewmodel.SkillPickerHandle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Loads the skill catalog behind the composer picker's skill rows.
 *
 * Lazy: nothing is fetched until the user first opens the picker in a chat screen where a skill
 * could be picked, then the catalog is kept for the screen. Every page is fetched, like web, so
 * the picker's search covers the whole catalog rather than its first page. Each skill's active
 * state is resolved once here from the user's overrides, the viewer and
 * `interface.skills.defaultActiveOnShare`.
 *
 * A first load that fails anywhere leaves no catalog and marks it failed: the picker says so and
 * offers a retry, and only that retry, a new trigger or a return to the screen with chips staged
 * ([refreshOnReturn]) loads again, so typing on in an open picker doesn't restart the load per
 * keystroke. A partial catalog is never kept: a missing
 * states map would offer skills the user turned off, which the server then drops without a word.
 *
 * The catalog goes stale when the user leaves and comes back (a skill turned off or created on
 * the Skills screen): [refreshOnReturn] marks it, and the next trigger reloads it quietly, the old
 * rows staying up until the new ones land (at once when the picker is open or chips are staged).
 * A quiet reload that fails keeps the old catalog, still marked stale: the next new trigger (or
 * return) reloads it again.
 */
class SkillPickerDelegate(
    private val handle: SkillPickerHandle,
    private val skillsRepository: SkillsRepository,
    private val userRepository: UserRepository,
    private val configRepository: ConfigRepository,
) {
    /** A load is in flight. Plain flag rather than the job's state: the job may finish before `launch` returns. */
    private var loading = false

    /**
     * The loaded catalog may be out of date; the next trigger reloads it quietly. Cleared only by
     * a load that succeeded and began after it was set.
     */
    private var stale = false

    /** The catalog went stale while the current load was in flight, which may have read the old state. */
    private var staleDuringLoad = false

    /** A reload was wanted at once while a load was in flight: it runs when that load ends. */
    private var reloadAfterLoad = false

    /** The last quiet reload failed: like a failed first load, only a new trigger retries it. */
    private var quietReloadFailed = false

    /**
     * Starts the load unless the catalog is here and fresh, or on its way. [newTrigger] is true
     * when the picker has just opened (or the user asked to retry); only then does a failed load
     * try again.
     */
    fun ensureLoaded(newTrigger: Boolean) {
        if (loading) return
        val state = handle.state
        val quiet = state.skillCatalog != null
        if (quiet && !stale) return
        if (!newTrigger && (if (quiet) quietReloadFailed else state.skillsLoadFailed)) return
        loading = true
        staleDuringLoad = false
        reloadAfterLoad = false
        if (!quiet) {
            handle.update {
                isLoadingSkills = true
                skillsLoadFailed = false
            }
        }
        handle.scope.launch {
            val catalog = try {
                loadCatalog()
            } finally {
                loading = false
            }
            if (catalog != null && !staleDuringLoad) stale = false
            if (quiet) quietReloadFailed = catalog == null
            handle.update {
                if (catalog != null) skillCatalog = catalog
                if (!quiet) {
                    isLoadingSkills = false
                    skillsLoadFailed = catalog == null
                }
            }
            if (reloadAfterLoad) {
                reloadAfterLoad = false
                // The return that asked for it is a fresh chance, as it is for refreshOnReturn.
                ensureLoaded(newTrigger = true)
            }
        }
    }

    /** The picker's "Couldn't load skills" retry. */
    fun retry() = ensureLoaded(newTrigger = true)

    /**
     * The chat screen is back in front (ON_RESUME): skills may have been turned off, created or
     * deleted meanwhile. Marks a loaded catalog stale, and reloads it now when the picker is
     * already open ([pickerOpen]) or skill chips are staged: the send rechecks the chips against
     * this catalog, so a skill turned off meanwhile must be off here before Send is tapped.
     * Nothing happens before the first load, unless that load failed while a reload is wanted
     * now: the return retries it. A load already in flight may have read the old state, so the
     * catalog stays stale after it and, when a reload is wanted now, one follows it (a retry, if
     * that load fails).
     */
    fun refreshOnReturn(pickerOpen: Boolean) {
        val reloadNow = pickerOpen || handle.state.pendingManualSkills.isNotEmpty()
        if (handle.state.skillCatalog == null && !loading) {
            // Staged chips wait on the catalog for the send's check, picker closed or not.
            if (handle.state.skillsLoadFailed && reloadNow) ensureLoaded(newTrigger = true)
            return
        }
        stale = true
        // A return is a fresh chance for a quiet reload that failed before.
        quietReloadFailed = false
        if (loading) {
            staleDuringLoad = true
            if (reloadNow) reloadAfterLoad = true
        } else if (reloadNow) {
            ensureLoaded(newTrigger = false)
        }
    }

    private suspend fun loadCatalog(): List<CatalogSkill>? = coroutineScope {
        val statesCall = async { skillsRepository.getSkillStates() }
        val userCall = async { userRepository.getUser() }
        val skills = loadAllPages() ?: return@coroutineScope null
        val states = when (val result = statesCall.await()) {
            is Result.Success -> result.data
            else -> {
                Logger.d { "Skill picker: states failed to load; offering no skills" }
                return@coroutineScope null
            }
        }
        val userId = when (val result = userCall.await()) {
            is Result.Success -> result.data.toSkillViewer().userId
            else -> {
                Logger.d { "Skill picker: user failed to load; offering no skills" }
                return@coroutineScope null
            }
        }
        val defaultActiveOnShare =
            skillsDefaultActiveOnShare(configRepository.startupConfig.value?.interfaceConfig?.skills)
        skills.map { skill ->
            skill.toCatalogSkill(
                active = resolveSkillActive(skill.id, skill.author, skill.source, states, userId, defaultActiveOnShare),
            )
        }
    }

    private suspend fun loadAllPages(): List<SkillSummary>? {
        val all = mutableListOf<SkillSummary>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            when (val page = skillsRepository.listSkills(limit = PAGE_SIZE, cursor = cursor)) {
                is Result.Success -> {
                    all += page.data.skills
                    val next = page.data.after
                    if (!page.data.hasMore || next.isNullOrEmpty() || next == cursor) return all
                    cursor = next
                }
                else -> {
                    Logger.d { "Skill picker: a catalog page failed to load; offering no skills" }
                    return null
                }
            }
        }
        // A server that keeps answering has_more is cut off rather than paged forever.
        return all
    }

    private companion object {
        /** The skills route's own maximum page size. */
        const val PAGE_SIZE = 100

        /** 5,000 skills: far past any real catalog, and a bound on a server that never ends. */
        const val MAX_PAGES = 50
    }
}

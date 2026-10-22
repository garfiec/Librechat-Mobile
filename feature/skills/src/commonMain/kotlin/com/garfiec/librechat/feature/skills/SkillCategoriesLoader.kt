package com.garfiec.librechat.feature.skills

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.response.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The server's category presets for one skills screen, fetched once. A failure is quiet (labels
 * fall back to the stored value) and the next [load] asks again.
 */
internal class SkillCategoriesLoader(
    private val configRepository: ConfigRepository,
    private val scope: CoroutineScope,
    private val onLoaded: (List<Category>) -> Unit,
) {
    private var job: Job? = null
    private var loaded = false

    fun load() {
        if (loaded || job?.isActive == true) return
        job = scope.launch {
            val result = configRepository.getCategories()
            if (result is Result.Success) {
                loaded = true
                onLoaded(result.data)
            }
        }
    }
}

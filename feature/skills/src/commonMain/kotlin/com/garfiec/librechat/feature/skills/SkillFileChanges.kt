package com.garfiec.librechat.feature.skills

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Tells a skill's file list that one of its files was saved from the file screen. One per app (a
 * Koin single): the file screen posts on every successful save, whatever happens to the screen
 * afterwards, and the detail page's list reloads for it, so the list never shows the
 * pre-save size or type.
 */
class SkillFileChanges {
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 16)

    fun post(skillId: String) {
        changes.tryEmit(skillId)
    }

    /** Emits once per save to [skillId]'s files. */
    fun changesTo(skillId: String): Flow<Unit> = changes.asSharedFlow().filter { it == skillId }.map { }
}

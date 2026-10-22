package com.garfiec.librechat.feature.skills.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.skills.screen.SkillDetailScreen
import com.garfiec.librechat.feature.skills.screen.SkillEditorScreen
import com.garfiec.librechat.feature.skills.screen.SkillFileScreen
import com.garfiec.librechat.feature.skills.screen.SkillsListScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface SkillsRoute : NavKey

@Serializable data object SkillsList : SkillsRoute

@Serializable data class SkillDetail(val skillId: String) : SkillsRoute

/** [skillId] null = create a new skill; non-null = edit an existing one. */
@Serializable data class SkillEditor(val skillId: String? = null) : SkillsRoute

/** One of [skillId]'s files, by the relative path the server stores it under. */
@Serializable data class SkillFile(val skillId: String, val path: String) : SkillsRoute

fun EntryProviderScope<NavKey>.skillsEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onReplace: (route: NavKey, replacement: NavKey) -> Unit,
) {
    entry<SkillsList> {
        SkillsListScreen(
            onSkillClick = { id -> onNavigate(SkillDetail(skillId = id)) },
            onCreateSkill = { onNavigate(SkillEditor(skillId = null)) },
            onBack = onBack,
        )
    }
    entry<SkillDetail> { key ->
        SkillDetailScreen(
            skillId = key.skillId,
            onBack = onBack,
            onEdit = { id -> onNavigate(SkillEditor(skillId = id)) },
            onOpenFile = { path -> onNavigate(SkillFile(skillId = key.skillId, path = path)) },
            onDelete = onBack,
        )
    }
    entry<SkillEditor> { key ->
        SkillEditorScreen(
            skillId = key.skillId,
            onBack = onBack,
            // A new skill opens on its page, as a new agent does; an edit returns to the page it came from.
            onSave = { id -> if (key.skillId == null) onReplace(key, SkillDetail(skillId = id)) else onBack() },
        )
    }
    entry<SkillFile> { key ->
        SkillFileScreen(skillId = key.skillId, relativePath = key.path, onBack = onBack)
    }
}

val skillsSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(SkillsList::class, SkillsList.serializer())
        subclass(SkillDetail::class, SkillDetail.serializer())
        subclass(SkillEditor::class, SkillEditor.serializer())
        subclass(SkillFile::class, SkillFile.serializer())
    }
}

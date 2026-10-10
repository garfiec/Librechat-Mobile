package com.garfiec.librechat.feature.schedules.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.schedules.screen.ScheduleEditorScreen
import com.garfiec.librechat.feature.schedules.screen.SchedulesListScreen
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable sealed interface SchedulesRoute : NavKey

@Serializable data object SchedulesList : SchedulesRoute

/** [scheduleId] null = create a new schedule; non-null = edit an existing one. */
@Serializable data class ScheduleEditor(val scheduleId: String? = null) : SchedulesRoute

fun EntryProviderScope<NavKey>.schedulesEntries(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    entry<SchedulesList> {
        SchedulesListScreen(
            onBack = onBack,
            onCreate = { onNavigate(ScheduleEditor(scheduleId = null)) },
            onEdit = { id -> onNavigate(ScheduleEditor(scheduleId = id)) },
            onOpenConversation = onOpenConversation,
        )
    }
    entry<ScheduleEditor> { key ->
        ScheduleEditorScreen(
            scheduleId = key.scheduleId,
            onBack = onBack,
            onSaveComplete = onBack,
        )
    }
}

val schedulesSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(SchedulesList::class, SchedulesList.serializer())
        subclass(ScheduleEditor::class, ScheduleEditor.serializer())
    }
}

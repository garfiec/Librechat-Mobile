package com.garfiec.librechat.feature.schedules.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleCadence
import com.garfiec.librechat.core.model.schedule.ScheduleFrequency
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

@PreviewNarrowScreens
@Composable
private fun ScheduleCardMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { ScheduleCardHost() }

@PreviewNarrowScreens
@Composable
private fun ScheduleCardGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { ScheduleCardHost() }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

/** A schedule the user can manage, so Run now and Delete show. */
@Composable
private fun ScheduleCardHost() {
    Box(Modifier.padding(16.dp)) {
        ScheduleCard(
            schedule = Schedule(
                id = "s1",
                name = "Morning news digest",
                cadence = ScheduleCadence.structured(ScheduleFrequency.DAILY, hour = 8, minute = 0),
                timezone = "Europe/Paris",
                nextRunAt = "2026-10-19T06:00:00.000Z",
            ),
            canManage = true,
            isBusy = false,
            onClick = {},
            onToggle = {},
            onRunNow = {},
            onDelete = {},
            onOpenConversation = {},
        )
    }
}

package com.garfiec.librechat.feature.schedules.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// A weekly schedule on Monday, Wednesday and Friday.

@PreviewNarrowScreens
@Composable
private fun WeekdayChipsMaterialPreview() = WeekdayChipsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun WeekdayChipsGlassPreview() = WeekdayChipsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun WeekdayChipsHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            var days by remember { mutableStateOf(setOf(1, 3, 5)) }
            WeekdayChips(
                selected = days,
                enabled = true,
                onToggle = { day -> days = if (day in days) days - day else days + day },
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

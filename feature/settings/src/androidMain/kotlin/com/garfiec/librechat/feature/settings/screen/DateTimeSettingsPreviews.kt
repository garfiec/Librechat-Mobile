package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.TimestampStyle
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.datetime.ProvideDateTimeFormat
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// Smart: the style whose description is longest.
@PreviewNarrowScreens
@Composable
private fun DateTimeSettingsMaterialPreview() = DateTimePreviewHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun DateTimeSettingsGlassPreview() = DateTimePreviewHost(UiStyle.LIQUID_GLASS)

@Composable
private fun DateTimePreviewHost(style: UiStyle) {
    var prefs by remember { mutableStateOf(DateTimeFormatPrefs(style = TimestampStyle.SMART)) }
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        ProvideDateTimeFormat(prefs) {
            Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                DateTimeSettingsContent(prefs = prefs, onChange = { prefs = it(prefs) })
            }
        }
    }
}

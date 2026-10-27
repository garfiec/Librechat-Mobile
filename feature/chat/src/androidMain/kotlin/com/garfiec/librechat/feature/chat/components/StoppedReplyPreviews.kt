package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// The chip under a cut-short reply, in a thread's content column past the avatar: the plain chat's
// Continue, and a comparison pane's longer Keep and continue in a narrow side-by-side pane.

@PreviewNarrowScreens
@Composable
private fun StoppedReplyPreview() = StoppedReplySurface(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun StoppedReplyGlassPreview() = StoppedReplySurface(UiStyle.LIQUID_GLASS)

@Composable
private fun StoppedReplySurface(style: UiStyle) {
    LibreChatTheme(darkTheme = false, uiStyle = style) {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("That night he climbed the stairs as usual. The light beam swept")
                StoppedReplyFooter(StoppedReplyAction("a1", "Continue") {})
                // A side-by-side pane is about half a tablet's width.
                Column(Modifier.width(260.dp)) {
                    StoppedReplyFooter(StoppedReplyAction("a1", "Keep and continue") {})
                }
                Column(Modifier.width(180.dp)) {
                    StoppedReplyFooter(StoppedReplyAction("a1", "Behalten und fortsetzen") {})
                }
            }
        }
    }
}

package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactButton

// Labels the chat screen builds itself rather than reading from the server: the landing greeting
// and an artifact card's type line.

private val artifacts = listOf(
    "application/vnd.mermaid" to "Quarterly revenue breakdown by region and product line",
    "application/vnd.react" to "Pricing calculator",
    "text/markdown" to "Release notes",
)

@PreviewNarrowScreens
@Composable
private fun ChatLabelsMaterialPreview() = ChatLabelsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun ChatLabelsGlassPreview() = ChatLabelsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun ChatLabelsHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                // The landing content fills its parent; hold it at its own height so nothing clips.
                LandingContent(selectedModel = "gpt-5", modifier = Modifier.height(IntrinsicSize.Min))
                artifacts.forEachIndexed { index, (type, title) ->
                    ArtifactButton(
                        artifact = Artifact(identifier = "a$index", type = type, title = title, language = null, content = ""),
                        onClick = {},
                        versionCount = if (index == 0) 3 else 1,
                    )
                }
            }
        }
    }
}

package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.chat.components.artifact.AddArtifactToHomeScreenDialog
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactButton
import com.garfiec.librechat.feature.chat.components.artifact.ArtifactViewer
import com.garfiec.librechat.feature.chat.components.artifact.LocalAddArtifactToHomeScreen
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.sin

// Inline media and the artifact surfaces, hosted like the rows in ChatRowPreviews.

@PreviewNarrowScreens
@Composable
private fun ArtifactSheetHeaderPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { ArtifactSheetHost() }

@PreviewNarrowScreens
@Composable
private fun TwoColumnTablePreview() = RowsPreviewSurface(UiStyle.MATERIAL) { TableHost(twoColumnTable) }

@PreviewNarrowScreens
@Composable
private fun WideTablePreview() = RowsPreviewSurface(UiStyle.MATERIAL) { TableHost(wideTable) }

@PreviewNarrowScreens
@Composable
private fun AudioPlayerPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { AudioHost() }

@PreviewNarrowScreens
@Composable
private fun ArtifactButtonVersionsPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { ArtifactButtonHost() }

@PreviewNarrowScreens
@Composable
private fun AddToHomeScreenDialogPreview() = RowsPreviewSurface(UiStyle.MATERIAL) { HomeScreenDialogHost() }

/** The sheet's viewer for an artifact with two versions, where the pin action is available. */
@Composable
private fun ArtifactSheetHost() {
    val versions = listOf(1, 2).map { version ->
        Artifact(
            identifier = "planner",
            type = "text/html",
            title = "Park day planner",
            language = null,
            content = "<p>v$version</p>",
            version = version,
        )
    }
    CompositionLocalProvider(LocalAddArtifactToHomeScreen provides {}) {
        Box(Modifier.height(120.dp)) {
            ArtifactViewer(
                artifact = versions.last(),
                versions = versions,
                isFullscreen = false,
                onClose = {},
                onExpand = {},
            )
        }
    }
}

private val twoColumnTable = """
    | Attraction | Recommendation |
    | --- | --- |
    | Seven Dwarfs Mine Train | Lightning Lane, otherwise rope drop |
    | Haunted Mansion | Afternoon, when the queue shortens |
""".trimIndent()

private val wideTable = """
    | Park | Opens | Closes | Fireworks | Crowd level |
    | --- | --- | --- | --- | --- |
    | Magic Kingdom | 8:00 | 23:00 | 21:00 | High |
    | EPCOT | 9:00 | 21:00 | 21:00 | Moderate |
""".trimIndent()

@Composable
private fun TableHost(markdown: String) = ThreadContent { MarkdownContent(text = markdown) }

@Composable
private fun AudioHost() = ThreadContent {
    val clip = remember { previewWavBase64() }
    AudioContent(data = clip, format = "wav")
}

/** A tenth of a second of tone, so the player has real media behind its controls. */
private fun previewWavBase64(): String {
    val sampleRate = 8_000
    val samples = 800
    val out = ByteArrayOutputStream()
    fun le(value: Int, bytes: Int) = repeat(bytes) { out.write((value shr (8 * it)) and 0xFF) }
    out.write("RIFF".toByteArray())
    le(36 + samples * 2, 4)
    out.write("WAVEfmt ".toByteArray())
    le(16, 4)
    le(1, 2)
    le(1, 2)
    le(sampleRate, 4)
    le(sampleRate * 2, 4)
    le(2, 2)
    le(16, 2)
    out.write("data".toByteArray())
    le(samples * 2, 4)
    repeat(samples) { le((3_000 * sin(it / 10.0)).toInt(), 2) }
    return Base64.getEncoder().encodeToString(out.toByteArray())
}

@Composable
private fun ArtifactButtonHost() = ThreadContent {
    ArtifactButton(
        artifact = Artifact(
            identifier = "planner",
            type = "text/html",
            title = "Park day planner",
            language = null,
            content = "",
        ),
        onClick = {},
        versionCount = 3,
        onAddToHomeScreen = {},
    )
}

@Composable
private fun HomeScreenDialogHost() {
    AddArtifactToHomeScreenDialog(
        initialName = "Park day planner",
        artifactType = "text/html",
        onConfirm = { _, _ -> },
        onDismiss = {},
    )
}

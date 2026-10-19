package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ArtifactKind
import com.garfiec.librechat.core.model.artifactKind
import com.garfiec.librechat.core.ui.resources.*
import com.garfiec.librechat.core.ui.resources.Res
import org.jetbrains.compose.resources.stringResource

/** The label for an artifact's type string, shared by the chat card and the pinned-shortcut list. */
@Composable
fun artifactTypeLabel(type: String): String = stringResource(
    when (artifactKind(type)) {
        ArtifactKind.MERMAID -> Res.string.artifact_type_mermaid
        ArtifactKind.REACT -> Res.string.artifact_type_react
        ArtifactKind.SVG -> Res.string.artifact_type_svg
        ArtifactKind.MARKDOWN -> Res.string.artifact_type_markdown
        ArtifactKind.HTML -> Res.string.artifact_type_html
        ArtifactKind.PLAIN_TEXT -> Res.string.artifact_type_plain_text
        ArtifactKind.CODE -> Res.string.artifact_type_code
    },
)

package com.garfiec.librechat.core.ui.components.pullrequest

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.pullrequest.PullRequestIcon

/**
 * The four pull request glyphs, drawn as strokes on a 24-unit grid (Material Icons has none): two
 * commits joined on the left, and on the right what became of the branch. Tinted by the caller.
 */
object PullRequestGlyphs {
    private const val LEFT_BRANCH = "M6,7 V17 " + "M4,5 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0 " + "M4,19 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0"
    private const val HEAD_COMMIT = "M16,19 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0"

    /** An open pull request: the head's branch arcs back towards the base. */
    val Open: ImageVector =
        glyph("PullRequestOpen", "$LEFT_BRANCH $HEAD_COMMIT M18,17 V10 C18,8 17,7 15,7 H11 M13,5 L11,7 L13,9")

    /** A draft: the branch is only dotted in. */
    val Draft: ImageVector = glyph("PullRequestDraft", "$LEFT_BRANCH $HEAD_COMMIT M18,8 V9 M18,12 V13")

    /** Closed without merging: the branch ends in a cross. */
    val Closed: ImageVector = glyph("PullRequestClosed", "$LEFT_BRANCH $HEAD_COMMIT M18,11 V17 M16,3 L20,7 M20,3 L16,7")

    /** Merged: the branch joins the base. */
    val Merged: ImageVector = glyph(
        "PullRequestMerged",
        "$LEFT_BRANCH M16,12 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0 M6,7 C6,10 9,12 16,12",
    )

    fun of(icon: PullRequestIcon): ImageVector = when (icon) {
        PullRequestIcon.OPEN -> Open
        PullRequestIcon.DRAFT -> Draft
        PullRequestIcon.CLOSED -> Closed
        PullRequestIcon.MERGED -> Merged
    }

    private fun glyph(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}

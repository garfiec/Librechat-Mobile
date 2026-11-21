package com.garfiec.librechat.feature.chat.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.SuccessResult
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// A code-interpreter reply that names its chart by filename, as the web resolves it.

@PreviewNarrowScreens
@Composable
internal fun MarkdownInlineChartPreview() = MarkdownImagePreviewHost(
    """
    Revenue grew every quarter:

    ![Quarterly revenue](revenue.png)

    The dip in Q3 is the holiday freeze.
    """.trimIndent(),
)

@PreviewNarrowScreens
@Composable
internal fun MarkdownMissingImagePreview() = MarkdownImagePreviewHost(
    """
    The script saved a plot, but this turn has no file by that name:

    ![Monthly active users by region](users_by_region.png)

    Still loading: ![Churn](/images/slow/churn.png)
    """.trimIndent(),
)

@PreviewNarrowScreens
@Composable
internal fun MarkdownRemoteImagePreview() = MarkdownImagePreviewHost(
    """
    Here is the reference diagram from the vendor's docs:

    ![Architecture overview of the ingestion pipeline](https://docs.example.net/img/pipeline.png)

    It shows the three stages.
    """.trimIndent(),
)

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun MarkdownImagePreviewHost(markdown: String) {
    val handler = remember {
        AsyncImagePreviewHandler { _, request ->
            if ("slow" in request.data.toString()) {
                AsyncImagePainter.State.Loading(null)
            } else {
                val chart = previewChartBitmap()
                AsyncImagePainter.State.Success(
                    BitmapPainter(chart.asImageBitmap()),
                    SuccessResult(chart.asImage(), request),
                )
            }
        }
    }
    LibreChatTheme(darkTheme = true, uiStyle = UiStyle.MATERIAL) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides handler,
            LocalParsedMarkdownCache provides remember { ParsedMarkdownCache() },
        ) {
            ProvideMarkdownMedia(attachments = PreviewAttachments, baseUrl = "https://chat.example.com") {
                MarkdownContent(text = markdown, modifier = Modifier.padding(16.dp), immediate = true)
            }
        }
    }
}

private val PreviewAttachments = listOf(
    Attachment(fileId = "f1", filename = "revenue.png", filepath = "/api/files/code/download/s1/revenue.png"),
)

/** A 640×400 bar chart, the shape a matplotlib default figure comes out at. */
private fun previewChartBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(640, 400, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFFFFFFFF.toInt())
    val bar = Paint().apply { color = 0xFF1F77B4.toInt() }
    listOf(120, 190, 150, 260).forEachIndexed { i, height ->
        val left = 80f + i * 140f
        canvas.drawRect(left, 360f - height, left + 90f, 360f, bar)
    }
    val axis = Paint().apply { color = 0xFF333333.toInt(); strokeWidth = 3f }
    canvas.drawLine(60f, 360f, 620f, 360f, axis)
    canvas.drawLine(60f, 40f, 60f, 360f, axis)
    return bitmap
}

package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.viewmodel.ModelChoice
import com.garfiec.librechat.feature.chat.viewmodel.PendingSteerChip
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.SteerChipStatus

// The steer and queue region above the composer, past its cap: it rests on its bottom (the queue
// nearest the field) and the oldest steer scrolls under the top edge.

@PreviewNarrowScreens
@Composable
private fun SteerAndQueueCappedMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) { SteerAndQueueComposerHost() }

@PreviewNarrowScreens
@Composable
private fun SteerAndQueueCappedGlassPreview() = PreviewSurface(UiStyle.LIQUID_GLASS) { SteerAndQueueComposerHost() }

/** One steer and one queued follow-up: under the cap, so the region doesn't scroll. */
@PreviewNarrowScreens
@Composable
private fun SteerAndQueueUnderCapMaterialPreview() = PreviewSurface(UiStyle.MATERIAL) {
    SteerAndQueueComposerHost(steers = previewSteers.take(1), queued = previewQueue.take(1))
}

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

@Composable
private fun SteerAndQueueComposerHost(
    steers: List<PendingSteerChip> = previewSteers,
    queued: List<QueuedMessage> = previewQueue,
) {
    ChatInput(
        inputText = "",
        isStreaming = true,
        onInputChange = {},
        onSend = {},
        onStop = {},
        onOpenTools = {},
        canQueue = true,
        canSteer = true,
        chrome = ComposerChrome(pendingSteers = steers, queuedMessages = queued),
    )
}

private val previewSteers = listOf(
    PendingSteerChip(steerId = "s1", text = "Use metric units throughout", status = SteerChipStatus.PENDING, createdAt = 0L),
    PendingSteerChip(
        steerId = "s2",
        text = "And keep the itinerary to walking distance from the hotel where you can",
        status = SteerChipStatus.PENDING,
        createdAt = 1L,
    ),
)

private val previewDispatch = EndpointDispatch(endpointType = null, key = null, modelDisplayLabel = null)

private val previewQueue = listOf("Then a packing list", "Which of those are open on Mondays?", "Then a rain plan")
    .mapIndexed { index, text ->
        QueuedMessage(
            localId = "q$index",
            text = text,
            choice = ModelChoice.Raw("openAI", "gpt-5.1"),
            dispatch = previewDispatch,
        )
    }

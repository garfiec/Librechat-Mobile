package com.garfiec.librechat.feature.chat.screen

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.feature.chat.viewmodel.SharedChatError

// The shared chat viewer's error states; the loaded and loading states are in SharedChatPreviews.kt.

@PreviewNarrowScreens
@Composable
private fun NotFoundMaterialPreview() = SharedChatViewerPreview(UiStyle.MATERIAL, failedSharedChat(SharedChatError.NOT_FOUND))

@PreviewNarrowScreens
@Composable
private fun NotFoundGlassPreview() = SharedChatViewerPreview(UiStyle.LIQUID_GLASS, failedSharedChat(SharedChatError.NOT_FOUND))

@PreviewNarrowScreens
@Composable
private fun NoAccessMaterialPreview() = SharedChatViewerPreview(UiStyle.MATERIAL, failedSharedChat(SharedChatError.NO_ACCESS))

@PreviewNarrowScreens
@Composable
private fun NoAccessGlassPreview() = SharedChatViewerPreview(UiStyle.LIQUID_GLASS, failedSharedChat(SharedChatError.NO_ACCESS))

@PreviewNarrowScreens
@Composable
private fun RateLimitedMaterialPreview() = SharedChatViewerPreview(UiStyle.MATERIAL, failedSharedChat(SharedChatError.RATE_LIMITED))

@PreviewNarrowScreens
@Composable
private fun RateLimitedGlassPreview() = SharedChatViewerPreview(UiStyle.LIQUID_GLASS, failedSharedChat(SharedChatError.RATE_LIMITED))

@PreviewNarrowScreens
@Composable
private fun BlockedMaterialPreview() = SharedChatViewerPreview(
    UiStyle.MATERIAL,
    failedSharedChat(SharedChatError.BLOCKED).copy(blockedMessage = "Message 4 matched the policy \"no internal hostnames\"."),
)

@PreviewNarrowScreens
@Composable
private fun BlockedGlassPreview() = SharedChatViewerPreview(
    UiStyle.LIQUID_GLASS,
    failedSharedChat(SharedChatError.BLOCKED).copy(blockedMessage = "Message 4 matched the policy \"no internal hostnames\"."),
)

/** A filter's own explanation can run to several sentences: it keeps a gutter and scrolls. */
private val blockedLong = failedSharedChat(SharedChatError.BLOCKED).copy(
    blockedMessage = "Sharing was blocked by your organisation's content policy. Message 4 contains an internal " +
        "hostname and message 9 an access token, and both rules apply to shared links as well as exports. " +
        "Remove or redact those messages, then ask the owner to update the link. If you believe this is a " +
        "mistake, contact your administrator and quote reference 7f3c-2291.",
)

@PreviewNarrowScreens
@Composable
private fun BlockedLongMaterialPreview() = SharedChatViewerPreview(UiStyle.MATERIAL, blockedLong)

@PreviewNarrowScreens
@Composable
private fun BlockedLongGlassPreview() = SharedChatViewerPreview(UiStyle.LIQUID_GLASS, blockedLong)

@PreviewNarrowScreens
@Composable
private fun LoadFailedMaterialPreview() = SharedChatViewerPreview(UiStyle.MATERIAL, failedSharedChat(SharedChatError.NETWORK))

@PreviewNarrowScreens
@Composable
private fun LoadFailedGlassPreview() = SharedChatViewerPreview(UiStyle.LIQUID_GLASS, failedSharedChat(SharedChatError.NETWORK))

package com.garfiec.librechat.feature.chat.viewmodel

/**
 * A chat with nothing in it yet. `LANDING` alone is not enough: it holds until the server's
 * `created` event, so the first send of a new chat would otherwise keep the banner up mid-reply.
 */
fun ChatUiState.isEmptyLanding(): Boolean =
    screenState == ChatScreenState.LANDING &&
        !isStreaming &&
        displayMessages.isEmpty() &&
        !comparisonState.primaryIsStreaming &&
        !comparisonState.secondaryIsStreaming

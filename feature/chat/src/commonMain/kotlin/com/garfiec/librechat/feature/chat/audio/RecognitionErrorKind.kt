package com.garfiec.librechat.feature.chat.audio

/** How the iOS recognizer's runtime error should end (or not end) a dictation segment. */
internal enum class RecognitionErrorKind {
    /** The recognizer heard nothing. Not a failure of on-device recognition. */
    NO_SPEECH,

    /** The task was cancelled. Not a failure of on-device recognition. */
    CANCELLED,

    /** Anything else: on-device recognition may genuinely be unusable, so the cloud is worth a try. */
    FAILURE,
}

/**
 * Classify an `SFSpeechRecognitionTask` error by its NSError [domain] and [code]. The codes are
 * undocumented but stable (`kAFAssistantErrorDomain` 1110 "No speech detected", 216 and
 * `kLSRErrorDomain` 301 "request was canceled"). An unknown error stays a [RecognitionErrorKind.FAILURE]
 * so a real on-device failure still reaches the cloud fallback.
 */
internal fun classifyRecognitionError(domain: String, code: Long): RecognitionErrorKind = when {
    domain == AF_ASSISTANT_ERROR_DOMAIN && code == AF_NO_SPEECH -> RecognitionErrorKind.NO_SPEECH
    domain == AF_ASSISTANT_ERROR_DOMAIN && code == AF_CANCELLED -> RecognitionErrorKind.CANCELLED
    domain == LSR_ERROR_DOMAIN && code == LSR_CANCELLED -> RecognitionErrorKind.CANCELLED
    else -> RecognitionErrorKind.FAILURE
}

private const val AF_ASSISTANT_ERROR_DOMAIN = "kAFAssistantErrorDomain"
private const val LSR_ERROR_DOMAIN = "kLSRErrorDomain"
private const val AF_NO_SPEECH = 1110L
private const val AF_CANCELLED = 216L
private const val LSR_CANCELLED = 301L

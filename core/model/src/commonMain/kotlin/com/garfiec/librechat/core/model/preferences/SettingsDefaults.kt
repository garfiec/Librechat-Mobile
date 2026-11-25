package com.garfiec.librechat.core.model.preferences

/**
 * Sentinel meaning "follow the device locale": the default until the user picks a specific language.
 * Maps to a null locale override at the app root, so formatting and resource resolution keep
 * following the system.
 */
const val DEFAULT_LANGUAGE = "system"

/** The stored text-to-speech source values: the device's own engine, or the server's TTS route. */
const val TTS_SOURCE_DEVICE = "device"
const val TTS_SOURCE_SERVER = "server"

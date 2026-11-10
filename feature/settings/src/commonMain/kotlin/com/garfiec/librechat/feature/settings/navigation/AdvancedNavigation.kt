package com.garfiec.librechat.feature.settings.navigation

import kotlinx.serialization.Serializable

/** Advanced's sections in page order, named after the categories they extend. */
@Serializable
enum class AdvancedSection { CHATS, ARTIFACTS, DISPLAY, VOICE, SEARCH, SERVER, DEVELOPER, CODE_ENVIRONMENTS }

/** Every power setting on one page; [section] opens it scrolled to that section. */
@Serializable data class AdvancedSettings(val section: AdvancedSection? = null) : SettingsPaneRoute

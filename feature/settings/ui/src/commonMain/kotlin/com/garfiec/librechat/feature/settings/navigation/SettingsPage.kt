package com.garfiec.librechat.feature.settings.navigation

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.category_account
import com.garfiec.librechat.feature.settings.resources.category_administration
import com.garfiec.librechat.feature.settings.resources.category_advanced
import com.garfiec.librechat.feature.settings.resources.category_chats
import com.garfiec.librechat.feature.settings.resources.category_models_keys
import com.garfiec.librechat.feature.settings.resources.category_notifications
import com.garfiec.librechat.feature.settings.resources.category_privacy_data
import com.garfiec.librechat.feature.settings.resources.category_voice
import com.garfiec.librechat.feature.settings.resources.section_about
import com.garfiec.librechat.feature.settings.resources.section_appearance
import com.garfiec.librechat.feature.settings.resources.section_language_region
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/** Advanced's sections in page order, named after the categories they extend. */
@Serializable
enum class AdvancedSection { CHATS, ARTIFACTS, DISPLAY, VOICE, SEARCH, SERVER, DEVELOPER, CODE_ENVIRONMENTS }

/** A Settings category with a page of its own. Appearance has its own page, [SettingsPage.Appearance]. */
@Serializable
enum class SettingsCategoryKey { ACCOUNT, CHATS, NOTIFICATIONS, VOICE, MODELS, LANGUAGE, PRIVACY_DATA, ADMINISTRATION, ABOUT }

/** A page the Settings home lists and, when wide, shows beside the list. Each has a route of its own. */
@Immutable
sealed interface SettingsPage {
    data object Appearance : SettingsPage

    data class Category(val key: SettingsCategoryKey) : SettingsPage

    /** Every power setting; [section] opens it scrolled to that section. */
    data class Advanced(val section: AdvancedSection? = null) : SettingsPage
}

/** The bar title of [this] page, on its route and its row on the Settings home. */
fun SettingsPage.titleRes(): StringResource = when (this) {
    SettingsPage.Appearance -> Res.string.section_appearance
    is SettingsPage.Advanced -> Res.string.category_advanced
    is SettingsPage.Category -> when (key) {
        SettingsCategoryKey.ACCOUNT -> Res.string.category_account
        SettingsCategoryKey.CHATS -> Res.string.category_chats
        SettingsCategoryKey.NOTIFICATIONS -> Res.string.category_notifications
        SettingsCategoryKey.VOICE -> Res.string.category_voice
        SettingsCategoryKey.MODELS -> Res.string.category_models_keys
        SettingsCategoryKey.LANGUAGE -> Res.string.section_language_region
        SettingsCategoryKey.PRIVACY_DATA -> Res.string.category_privacy_data
        SettingsCategoryKey.ADMINISTRATION -> Res.string.category_administration
        SettingsCategoryKey.ABOUT -> Res.string.section_about
    }
}

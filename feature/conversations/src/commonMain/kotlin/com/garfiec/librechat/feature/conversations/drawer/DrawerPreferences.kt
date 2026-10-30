package com.garfiec.librechat.feature.conversations.drawer

import com.garfiec.librechat.core.data.datastore.DrawerPinsStore
import com.garfiec.librechat.core.data.datastore.SettingsDataStore

/** What the drawer keeps on the device: its Library tab, and the Workspace destinations pinned in its footer. */
class DrawerPreferences(
    val settings: SettingsDataStore,
    val pins: DrawerPinsStore,
)

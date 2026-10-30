package com.garfiec.librechat.core.data.datastore

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * The drawer's pinned destinations ([AppPreferences.DrawerPinned]) as opaque ids in the user's order.
 * The feature maps them to its own destinations and skips ids it doesn't know.
 *
 * Bound eagerly and held on the application scope, so the value is loaded before the tablet sidebar's
 * first frame and survives the drawer's ViewModel being recreated. `null` = not read yet, which is not
 * the same as nothing pinned.
 */
class DrawerPinsStore(
    private val preferences: PreferenceStore,
    appScope: CoroutineScope,
) {
    val pinned: StateFlow<List<String>?> =
        preferences.observe(AppPreferences.DrawerPinned).stateIn(appScope, SharingStarted.Eagerly, null)

    /** Appends [id] if it isn't pinned yet. */
    suspend fun pin(id: String) = preferences.update(AppPreferences.DrawerPinned) { ids ->
        if (id in ids) ids else ids + id
    }

    suspend fun unpin(id: String) = preferences.update(AppPreferences.DrawerPinned) { ids -> ids - id }

    /**
     * Saves [ordered] as the pinned order. Ids it leaves out that are stored (ones this build hid or
     * doesn't know) keep their place at the end, so a reorder of the visible pins can't drop them.
     */
    suspend fun reorder(ordered: List<String>) = preferences.update(AppPreferences.DrawerPinned) { ids ->
        val kept = ordered.filter { it in ids }
        kept + ids.filterNot { it in kept }
    }
}

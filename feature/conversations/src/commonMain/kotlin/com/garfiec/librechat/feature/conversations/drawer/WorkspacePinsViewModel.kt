package com.garfiec.librechat.feature.conversations.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.DrawerPinsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The drawer's pinned Workspace destinations. [pinned] is the stored ids, null until read; the drawer
 * maps them with [pinnedDestinations] against the destinations its gates allow, so a pin whose gate is
 * off is hidden but kept, and comes back with the gate.
 */
class WorkspacePinsViewModel(private val store: DrawerPinsStore) : ViewModel() {
    val pinned: StateFlow<List<String>?> = store.pinned

    fun pin(destination: WorkspaceDestination) {
        viewModelScope.launch { store.pin(destination.storageId) }
    }

    fun unpin(destination: WorkspaceDestination) {
        viewModelScope.launch { store.unpin(destination.storageId) }
    }

    /** Saves a dropped order of the visible pins; hidden ones keep their stored place after them. */
    fun reorder(ordered: List<WorkspaceDestination>) {
        viewModelScope.launch { store.reorder(ordered.map { it.storageId }) }
    }
}

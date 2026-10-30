package com.garfiec.librechat.feature.conversations.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.DrawerPinsStore
import kotlinx.coroutines.launch

/**
 * Pins, unpins and reorders the drawer's Workspace destinations. The drawer reads them through
 * [DrawerViewModel.workspace], which keeps a pin whose gate is off stored but hidden, back with the gate.
 */
class WorkspacePinsViewModel(private val store: DrawerPinsStore) : ViewModel() {
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

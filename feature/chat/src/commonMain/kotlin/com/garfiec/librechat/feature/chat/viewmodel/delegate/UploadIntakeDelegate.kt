package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.datastore.UploadRoutingMode
import com.garfiec.librechat.core.model.FileObject
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.media.resolveFileReferenceUrl
import com.garfiec.librechat.core.model.response.UploadRoute
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.util.isImageType
import com.garfiec.librechat.feature.chat.util.visionUnreadableImageNames
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.PendingUploadFile
import com.garfiec.librechat.feature.chat.viewmodel.PendingUploadRouting
import com.garfiec.librechat.feature.chat.viewmodel.UploadIntakeHandle
import com.garfiec.librechat.feature.chat.viewmodel.UploadRoutingContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Takes picked files — from the picker, a share, or the "From server" picker — into the composer's
 * attachment tray, and gates every send on them having got there.
 *
 * Intake is asynchronous: it reads the routing preference and waits on the selected agent's
 * provider, then either hands the routed batch to the platform [fileHandler] or stages it for the
 * manual routing sheet. The routing *table* itself is `ChatUiState.uploadRouteFor` /
 * `uploadRouteIsAmbiguous`; this delegate only decides when to ask and what to hand over.
 *
 * Owns `ComposerState.isAwaitingUploadSend`, `pendingUploadRouting` and `resolvingPickCount`. The
 * send paths' composer transactions (`ChatViewModel.applyComposer` / `SendDispatchDelegate.clearComposer`)
 * still clear a staged batch themselves, inside their own single-emission writes. The parked-send job stays on
 * the platform handler ([PlatformFileHandler.pendingUploadSendJob]).
 */
class UploadIntakeDelegate(
    private val handle: UploadIntakeHandle,
    private val fileHandler: PlatformFileHandler,
    private val settingsDataStore: SettingsDataStore,
    /** `ModelSelectionDelegate.awaitSelectedAgentProvider` — resolved before every route decision. */
    private val awaitAgentProvider: suspend () -> Unit,
    /** The exposed `uiState`'s server URL; the source state never carries it (see `ChatViewModel.uiState`). */
    private val serverUrl: () -> String,
) {

    /**
     * Runs [action] once any pending file uploads have finished, guarding against a double-send
     * while a previous wait is still in flight. Shared by the live-send and queue paths so the
     * upload-wait semantics live in one place.
     */
    fun withUploadGate(text: String, action: (String) -> Unit) {
        if (fileHandler.pendingUploadSendJob?.isActive == true) return
        if (hasUnsettledPicks()) {
            Logger.d { "withUploadGate: refusing send — picked files are not settled yet" }
            return
        }
        if (fileHandler.hasPendingUploads()) {
            Logger.d { "withUploadGate: waiting for pending upload(s) to complete" }
            // Park the send behind the upload and flip the composer's Send button to a cancellable
            // spinner, so a tap isn't a silent no-op while we wait (see [cancelPendingUploadSend]).
            setAwaitingUploadSend(true)
            fileHandler.pendingUploadSendJob = handle.scope.launch {
                try {
                    fileHandler.waitForUploadsAndSend(text) { ready ->
                        // Clear before handing off so the button never shows a spinner over an
                        // already-started send (the success path may set streaming synchronously).
                        setAwaitingUploadSend(false)
                        action(ready)
                    }
                } finally {
                    // Covers the abort/timeout/cancel paths where [action] never runs.
                    setAwaitingUploadSend(false)
                }
            }
            return
        }
        action(text)
    }

    private fun setAwaitingUploadSend(awaiting: Boolean) {
        handle.update { isAwaitingUploadSend = awaiting }
    }

    /**
     * Cancels a send that is parked waiting for its attachment(s) to finish uploading (the composer
     * shows a spinner in place of Send). The draft and attachment chips stay put so the user can
     * retry once the upload settles; the uploads themselves keep running.
     */
    fun cancelPendingUploadSend() {
        val job = fileHandler.pendingUploadSendJob ?: return
        fileHandler.pendingUploadSendJob = null
        job.cancel()
        setAwaitingUploadSend(false)
    }

    /**
     * The one asynchronous intake every pick passes through, whether it came from the picker or
     * from a share. Resolves each pick's name and MIME type, chooses a delivery route for it, then
     * hands the routed list to the platform handler.
     *
     * [prompt] is false for a share, which never opens the routing sheet — but still has to resolve
     * the provider before it can route. In Manual mode a prompted intake may instead stage the batch
     * for the routing sheet — which is why it launches: the preference is read with `.first()` at
     * the decision point rather than folded into the `uiState` combine, where a behaviour flag reads
     * its default forever (see `ChatPrefsState`).
     */
    fun intakePickedFiles(platformRefs: List<Any>, prompt: Boolean) {
        // The composer these files were picked for. A queued-edit session is a *different* draft
        // sharing one composer, and it can end while we resolve.
        val pickedFor = handle.state.composer.editingQueuedItem
        // Incremented BEFORE the launch, synchronously on the caller's dispatch: everything below
        // suspends at least once, and until this lands the picked files are in no list a send
        // gate reads.
        changeResolvingPicks(+1)
        handle.scope.launch {
            try {
                // Short-circuits on the share path, so it never touches the preference at all.
                val manual = prompt &&
                    settingsDataStore.uploadRoutingMode.first() == UploadRoutingMode.MANUAL
                // Resolve the agent's provider first — routing without it silently takes the
                // provider path for everything, which is the failure this feature exists to fix.
                awaitAgentProvider()
                // Cancelling a queued edit restores the stashed new-message draft over the whole
                // tray, so landing these files now would attach them to a draft they were not
                // picked for while the queued item goes back without them. Cancel means "discard
                // composer changes", and a file picked during the edit is one of those changes —
                // so drop it here rather than re-home it onto whatever is on screen now.
                if (handle.state.composer.editingQueuedItem != pickedFor) {
                    Logger.w { "intakePickedFiles: dropping ${platformRefs.size} pick(s) — the composer they were picked for is gone" }
                    return@launch
                }
                if (manual) {
                    stageForManualRouting(platformRefs)
                } else {
                    attachWithAutoRouting(platformRefs)
                }
            } finally {
                changeResolvingPicks(-1)
            }
        }
    }

    private fun changeResolvingPicks(delta: Int) {
        handle.update { resolvingPickCount = (resolvingPickCount + delta).coerceAtLeast(0) }
    }

    /** See [ChatUiState.arePicksUnsettled]; every send path must refuse while it holds. */
    fun hasUnsettledPicks(): Boolean = handle.state.arePicksUnsettled

    /**
     * Stages a picked batch for the routing sheet, or attaches it straight away when there is
     * nothing worth asking about — a sheet whose every control is disabled is friction, not
     * choice.
     */
    private fun stageForManualRouting(platformRefs: List<Any>) {
        val state = handle.state
        val picked = fileHandler.describe(platformRefs)
        if (picked.isEmpty()) return

        val staged = picked.map { file ->
            PendingUploadFile(
                file = file,
                route = state.uploadRouteFor(file.mimeType),
                choosable = state.uploadRouteIsAmbiguous(file.mimeType),
            )
        }
        if (staged.none { it.choosable }) {
            fileHandler.onFilesSelected(staged.map { RoutedFile(it.file, it.route) })
            return
        }
        handle.update {
            val existing = pendingUploadRouting
            // Append to a batch already staged rather than replacing it. Intake is
            // asynchronous and nothing disables the attach affordance while it runs, so a
            // second pick can land before the sheet for the first one is even on screen —
            // and an assignment there would discard files the user picked, with no upload
            // and no error. Keep the original context: confirm re-checks it against the
            // live selection anyway, and the older one is the conservative side of that.
            pendingUploadRouting = existing?.copy(files = existing.files + staged)
                ?: PendingUploadRouting(files = staged, context = state.uploadRoutingContext())
        }
    }

    /**
     * Flips the route of the staged file at [index] — the sheet's own row position. No-op for a
     * file with only one usable mode.
     *
     * Addressed by position rather than by value: batches append, so the same file picked twice
     * before the sheet paints is two equal [PickedFile]s, and matching on equality would flip both
     * rows at once with no way to tell which one the tap reached.
     */
    fun setPendingUploadRoute(index: Int, route: UploadRoute) {
        updatePendingRouting { pending ->
            pending.copy(
                files = pending.files.mapIndexed { i, staged ->
                    if (i == index && staged.choosable) staged.copy(route = route) else staged
                },
            )
        }
    }

    /** Applies [route] to every staged file that has a choice — the sheet's "apply to all". */
    fun setAllPendingUploadRoutes(route: UploadRoute) {
        updatePendingRouting { pending ->
            pending.copy(files = pending.files.map { if (it.choosable) it.copy(route = route) else it })
        }
    }

    /** Commits the staged batch: uploads every file with the route now shown against it. */
    fun confirmPendingUploadRouting() {
        val pending = handle.state.composer.pendingUploadRouting ?: return
        clearPendingUploadRouting()
        val state = handle.state
        val now = state.uploadRoutingContext()
        val routed = if (now == pending.context) {
            pending.files.map { RoutedFile(it.file, it.route) }
        } else {
            // The sheet is a window in which the selection can move under the user — a models/
            // config refresh corrects an invalidated selection, a conversation load re-seeds it,
            // and the scrim blocks neither. Honour each choice only where it is still one of two
            // real options; otherwise take what auto would pick against the selection that will
            // actually receive the upload.
            Logger.d { "confirmPendingUploadRouting: selection moved from ${pending.context} to $now" }
            pending.files.map { staged ->
                val route = if (state.uploadRouteIsAmbiguous(staged.file.mimeType)) {
                    staged.route
                } else {
                    state.uploadRouteFor(staged.file.mimeType)
                }
                RoutedFile(staged.file, route)
            }
        }
        fileHandler.onFilesSelected(routed)
    }

    /**
     * Abandons the staged batch. Nothing was uploaded, so there is no server record to clean up —
     * that is the whole reason the decision happens before the upload rather than after it.
     */
    fun cancelPendingUploadRouting() = clearPendingUploadRouting()

    private fun clearPendingUploadRouting() {
        handle.update { pendingUploadRouting = null }
    }

    private fun updatePendingRouting(block: (PendingUploadRouting) -> PendingUploadRouting) {
        handle.update {
            val pending = pendingUploadRouting ?: return@update
            pendingUploadRouting = block(pending)
        }
    }

    private fun attachWithAutoRouting(platformRefs: List<Any>) {
        // Read the live selection slice, not the exposed `uiState`: behaviour must not be decided
        // from a projection built for rendering (see ChatPrefsState's post-mortem).
        val state = handle.state
        val routed = fileHandler.describe(platformRefs).map { picked ->
            RoutedFile(file = picked, route = state.uploadRouteFor(picked.mimeType))
        }
        if (routed.isNotEmpty()) fileHandler.onFilesSelected(routed)
    }

    fun removeFile(file: AttachedFile) = fileHandler.removeFile(file)

    /**
     * Re-uploads a failed attachment. Resolves the agent's provider first, exactly as the intake
     * path does: the handler re-derives the route from the live selection, and a retry is the one
     * action a user takes *after* a failure — the same outage that failed the upload will often
     * have failed the provider fetch, and routing against an unresolved provider silently sends
     * every document down the provider path.
     */
    fun retryUpload(file: AttachedFile) {
        handle.scope.launch {
            awaitAgentProvider()
            fileHandler.retryUpload(file)
        }
    }

    /** Re-uploads the prompted PDF with its password; routed like [retryUpload], for the same reason. */
    fun submitPdfPassword(password: String) {
        // Bound to the prompt on screen NOW: the dialog stays up through the await, so a second
        // tap or a Cancel in that window must not hand this password to the next queued PDF.
        val prompt = fileHandler.pdfPasswordPrompts.value.firstOrNull() ?: return
        handle.scope.launch {
            awaitAgentProvider()
            fileHandler.submitPdfPassword(prompt, password)
        }
    }

    fun dismissPdfPassword() = fileHandler.dismissPdfPassword()

    /**
     * Attaches already-uploaded server files (from the "From server" picker) to the composer by
     * reference — no re-upload. Each [FileObject] is already persisted, so it maps to a completed
     * [AttachedFile] (`uploadProgress = 1f`, real `fileId`); the synthetic `uri = fileId` just gives
     * the chip a stable key for removal (mirrors iOS, which already uses a String uri).
     */
    fun attachServerFiles(files: List<FileObject>) {
        if (files.isEmpty()) return
        // Attach every pick — the server keeps a heightless image as a plain file record, and an
        // agent may route it to a tool — but warn about images the vision encoder will skip so a
        // picked image doesn't silently do nothing on a vision model (issue #252). Yield to any
        // real error already showing so the heads-up can't clobber a more important notice.
        val unreadable = visionUnreadableImageNames(files)
        if (unreadable.isNotEmpty() && handle.state.error == null) {
            handle.setError(unreadableImageWarning(unreadable))
        }
        val baseUrl = serverUrl()
        fileHandler.addPreUploadedFiles(
            files.map { file ->
                val isImage = isImageType(file.type)
                // The preview row loads images from `uri`, so resolve the same server URL the
                // message renderers use. Non-image files show an icon, so the bare id is fine as
                // a stable key for removal.
                val previewUrl = if (isImage) {
                    resolveFileReferenceUrl(
                        FileReference(fileId = file.fileId, filepath = file.filepath, type = file.type),
                        baseUrl,
                    )
                } else {
                    null
                }
                AttachedFile(
                    uri = previewUrl ?: file.fileId,
                    name = file.filename,
                    isImage = isImage,
                    uploadProgress = 1f,
                    fileId = file.fileId,
                    filepath = file.filepath,
                    type = file.type,
                    width = file.width,
                    height = file.height,
                )
            },
        )
    }

    /** Advisory shown when a picked server image has no stored dimensions the model can read. */
    private fun unreadableImageWarning(names: List<String>): String = when (names.size) {
        1 -> "\"${names.first()}\" has no saved dimensions, so the model may not read it as an " +
            "image. Try re-uploading it from your device."
        else -> "${names.size} picked images have no saved dimensions, so the model may not read " +
            "them as images. Try re-uploading them from your device."
    }
}

/** The selection a routing decision is being made against; re-checked at confirm. */
private fun ChatUiState.uploadRoutingContext() = UploadRoutingContext(
    endpoint = selectedEndpoint,
    endpointType = endpointConfigs[selectedEndpoint]?.type,
    agentProvider = selectedAgentProvider,
)

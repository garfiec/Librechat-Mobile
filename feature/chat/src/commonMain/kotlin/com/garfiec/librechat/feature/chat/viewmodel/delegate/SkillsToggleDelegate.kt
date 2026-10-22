package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.SkillsToggleHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Where a chat's Skills toggle is kept. Web's rule (`BadgeRowContext.tsx`, `useToolToggle.ts`):
 * an existing conversation keeps its own value; a new chat reads and writes one shared slot, so a
 * fresh chat starts from the last new-chat setting.
 */
internal sealed interface SkillsToggleSlot {
    /** Read from and written to [SettingsDataStore.skillsToggle] under [key]. */
    data class Stored(val key: String) : SkillsToggleSlot

    /** A temporary chat: the value lives in memory only and is never written. */
    data object Memory : SkillsToggleSlot

    /** The active model spec configures `skills`: the toggle is neither read nor written. */
    data object SpecGoverned : SkillsToggleSlot

    /**
     * A model spec is selected that leaves `skills` alone. Web reads nothing while a spec is
     * active and rebuilds the ephemeral agent from the spec, which has no `skills` key, so the
     * chat starts off on every spec change; the session's value is kept in memory. A toggle in an
     * existing (not temporary) conversation is still written to that conversation's own slot, as
     * web's `useToolToggle` writes `LAST_SKILLS_TOGGLE_<id>` whatever the spec, so it is what the
     * conversation reads once the spec is cleared.
     */
    data class SpecSelected(val specName: String) : SkillsToggleSlot
}

/**
 * The slot [state] maps to.
 *
 * A selected spec reads nothing ([SkillsToggleSlot.SpecSelected]). A new chat uses the separate
 * `__defaults__` slot when the server offers model specs and none is selected (web's
 * `storageContextKey`), else the plain new-chat slot. MIRRORED rule, registered in
 * `scripts/mirrors.json` as `skills-toggle-slots`.
 */
internal fun skillsToggleSlot(state: ChatUiState): SkillsToggleSlot {
    if (state.activeSpecSkills != null) return SkillsToggleSlot.SpecGoverned
    state.selectedSpec?.let { return SkillsToggleSlot.SpecSelected(it) }
    if (state.isTemporaryChat) return SkillsToggleSlot.Memory
    state.conversationId?.let { return SkillsToggleSlot.Stored(it) }
    val specsOffered = state.selection.modelSpecs?.list.orEmpty().isNotEmpty()
    return SkillsToggleSlot.Stored(
        if (specsOffered) {
            SettingsDataStore.SKILLS_TOGGLE_SPEC_DEFAULTS_SLOT
        } else {
            SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT
        },
    )
}

/** Every field [skillsToggleSlot] reads, and whether a queued message is in the composer. */
private data class SlotInputs(
    val selectedSpec: String?,
    val modelSpecs: ModelSpecs?,
    val isTemporaryChat: Boolean,
    val conversationId: String?,
    val isEditingQueued: Boolean,
)

/**
 * The slot the chat maps to, and whether this change of it came from the composer swapping a
 * queued message in or out ([swapped]). A swap brings its own value, so it is not a spec pick and
 * not a reason to read the slot over what the swap put on screen.
 */
private data class SlotChange(val slot: SkillsToggleSlot, val swapped: Boolean)

/** The slots a new chat reads and writes, shared by every new chat. */
private val NEW_CHAT_SLOTS = setOf(
    SettingsDataStore.SKILLS_TOGGLE_NEW_CHAT_SLOT,
    SettingsDataStore.SKILLS_TOGGLE_SPEC_DEFAULTS_SLOT,
)

/**
 * Owns the chat Skills toggle (`ephemeralAgent.skills`), which is per conversation where every
 * other chat tool is global.
 *
 * The value follows the slot the chat maps to ([skillsToggleSlot]): it is re-read whenever the
 * conversation, temp-ness or spec changes, and a toggle writes it back. On a new chat's first send
 * the value carries to the created conversation ([carryToCreatedConversation]), and the Chat(id)
 * ViewModel that takes over is seeded with it ([seed]) — the only path for a temporary chat, and
 * what keeps the switch from reading "off" in the moment before the carried write lands.
 *
 * While a queued message is being edited the composer shows that message's own value, and a
 * toggle or a skill pick changes the message only: nothing is written to the conversation's slot.
 * The edit's start and end swap the composer in one emission, and that swap is never read as a
 * spec pick or as a reason to re-read the slot.
 */
class SkillsToggleDelegate(
    private val handle: SkillsToggleHandle,
    private val settingsDataStore: SettingsDataStore,
) {
    /**
     * A stored slot's value until the store has one: the carried or handed-off value, keyed by the
     * conversation it belongs to. The store wins as soon as it emits a value.
     */
    private val pendingStored = MutableStateFlow<Pair<String, Boolean>?>(null)

    /** The handoff value of a chat that opened under a spec, taken by its first spec entry. */
    private var specOpening: Boolean? = null

    /**
     * The shared new-chat slot a skill pick turned on for this chat only ([turnOn]). It is never
     * written there, so the next new chat doesn't inherit it; it wins over the slot's stored value
     * until the user toggles, the chat is created or the landing starts over ([resetNewChat]).
     */
    private val pickedOnInNewChat = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        handle.scope.launch {
            var wasEditingQueued: Boolean? = null
            handle.stateFlow
                // Only what the slot reads, so a streaming flush costs a few reference compares.
                .distinctUntilChangedBy {
                    SlotInputs(
                        it.selection.selectedSpec,
                        it.selection.modelSpecs,
                        it.isTemporaryChat,
                        it.conversationId,
                        it.isEditingQueued,
                    )
                }
                .map { it.isEditingQueued to skillsToggleSlot(it) }
                .distinctUntilChanged()
                .map { (editing, slot) ->
                    val swapped = wasEditingQueued != null && wasEditingQueued != editing
                    wasEditingQueued = editing
                    SlotChange(slot, swapped)
                }
                .flatMapLatest { (slot, swapped) ->
                    when (slot) {
                        // A swap keeps its value; later writes to the slot still show.
                        is SkillsToggleSlot.Stored -> storedValue(slot.key).let { if (swapped) it.drop(1) else it }
                        // Every spec change starts off, as web rebuilds the agent from the spec.
                        is SkillsToggleSlot.SpecSelected ->
                            if (swapped) emptyFlow() else flowOf(specOpening.also { specOpening = null } ?: false)
                        // In memory the value stays what it is; a spec ignores it.
                        SkillsToggleSlot.Memory, SkillsToggleSlot.SpecGoverned -> emptyFlow()
                    }
                }
                .collect { on -> handle.update { skillsToggle = on } }
        }
    }

    /** A stored slot's value: a pick on this new chat, else the store, else the carried value. */
    private fun storedValue(key: String) = combine(
        // Distinct here too: after a swap every later value is trusted over what is on screen.
        settingsDataStore.skillsToggle(key).distinctUntilChanged(),
        pendingStored.map { pending -> pending?.takeIf { it.first == key }?.second }.distinctUntilChanged(),
        pickedOnInNewChat.map { it == key }.distinctUntilChanged(),
    ) { stored, pending, picked ->
        picked || (stored ?: pending ?: false)
    }

    /** The tools-sheet row and pinned chip. Ignored where the toggle isn't offered. */
    fun toggle() {
        if (!handle.state.isSkillsToolAvailable) return
        set(!handle.state.skillsToggle)
    }

    /**
     * Turns skills on for this conversation, as picking a skill does. A no-op in a persisted-agent
     * chat (no ephemeral agent to flag) and while a model spec governs skills, which ignores the
     * toggle.
     *
     * On a new chat the pick holds for this chat only: the shared new-chat slot is left alone, so
     * one pick (even a chip removed before sending) doesn't turn Skills on for every later new
     * chat. The value still carries to the created conversation on send. An existing
     * conversation stores it as a toggle does. During a queued edit it turns on that message only.
     */
    fun turnOn() {
        val state = handle.state
        if (!state.skillsCapable || !state.showEphemeralTools || state.skillsToggle) return
        val slot = skillsToggleSlot(state)
        if (state.isEditingQueued) {
            set(true)
        } else if (slot is SkillsToggleSlot.Stored && slot.key in NEW_CHAT_SLOTS) {
            pickedOnInNewChat.value = slot.key
            handle.update { skillsToggle = true }
        } else {
            set(true)
        }
    }

    /** The landing stands for the next new chat: a pick on the last one no longer applies. */
    fun resetNewChat() {
        pickedOnInNewChat.value = null
    }

    private fun set(on: Boolean) {
        val slot = skillsToggleSlot(handle.state)
        if (slot == SkillsToggleSlot.SpecGoverned) return
        // The composer holds a queued message: the value is that message's and goes out with it.
        // The conversation's own comes back when the edit ends, so it is left as it is.
        if (handle.state.isEditingQueued) {
            handle.update { skillsToggle = on }
            return
        }
        pickedOnInNewChat.value = null
        handle.update { skillsToggle = on }
        val key = when (slot) {
            is SkillsToggleSlot.Stored -> slot.key
            // Written though not read under the spec: it is the conversation's value after it.
            is SkillsToggleSlot.SpecSelected -> handle.state.conversationId?.takeUnless { handle.state.isTemporaryChat }
            SkillsToggleSlot.Memory, SkillsToggleSlot.SpecGoverned -> null
        }
        if (key != null) handle.scope.launch { settingsDataStore.setSkillsToggle(key, on) }
    }

    /**
     * A new chat's first send created [conversationId]: an on value moves with it, as web carries
     * the new-chat state to the created id, and the shared new-chat slot keeps its value for the
     * next new chat. Nothing is carried for a temporary chat or under a spec (their value stays in
     * memory and reaches the Chat(id) ViewModel through [seed]), nor for off, which is what an
     * empty slot reads as.
     */
    fun carryToCreatedConversation(conversationId: String, isTemporary: Boolean) {
        val state = handle.state
        if (isTemporary || state.activeSpecSkills != null || state.selectedSpec != null || !state.skillsToggle) return
        pendingStored.value = conversationId to true
        handle.scope.launch { settingsDataStore.setSkillsToggle(conversationId, true) }
    }

    /** Seeds this chat's value from the new-chat handoff. Called before [start]. */
    fun seed(conversationId: String, on: Boolean) {
        when (skillsToggleSlot(handle.state)) {
            SkillsToggleSlot.SpecGoverned -> Unit
            SkillsToggleSlot.Memory -> handle.update { skillsToggle = on }
            is SkillsToggleSlot.SpecSelected -> specOpening = on
            is SkillsToggleSlot.Stored -> pendingStored.value = conversationId to on
        }
    }
}

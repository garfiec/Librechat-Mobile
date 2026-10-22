package com.garfiec.librechat.feature.skills.screen

import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailAction
import com.garfiec.librechat.feature.skills.viewmodel.SkillDetailEvent
import com.garfiec.librechat.feature.skills.viewmodel.SkillsListEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A later event must not cut short a snackbar that can't be shown again. */
@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarEventsTest {

    private val warnings = SkillDetailEvent.SaveWarnings(listOf("Name was trimmed"))
    private val reloadFailed = SkillDetailEvent.ReloadFailed(SkillError(SkillErrorKind.LOAD_SKILL))
    private val toggleFailed = SkillDetailEvent.ActionFailed(SkillDetailAction.TOGGLE_ACTIVE, "nope")

    /** Records what was shown, and what was cut short before it finished. */
    private class Screen {
        val shown = mutableListOf<SkillDetailEvent>()
        val cutShort = mutableListOf<SkillDetailEvent>()
        val finished = mutableListOf<SkillDetailEvent>()
        val dismiss = mutableMapOf<SkillDetailEvent, CompletableDeferred<Unit>>()

        suspend fun show(event: SkillDetailEvent) {
            shown += event
            if (detailSnackbarSlot(event) == SnackbarSlot.NONE) return
            val gate = CompletableDeferred<Unit>().also { dismiss[event] = it }
            try {
                gate.await()
                finished += event
            } catch (e: CancellationException) {
                cutShort += event
                throw e
            }
        }
    }

    @Test
    fun `a reload failure after a save's warnings waits for them instead of replacing them`() = runTest {
        val events = Channel<SkillDetailEvent>(Channel.BUFFERED)
        val screen = Screen()
        val collector = launch { events.receiveAsFlow().collectSnackbarEvents(::detailSnackbarSlot, screen::show) }

        events.send(warnings)
        events.send(reloadFailed)
        runCurrent()
        assertThat(screen.shown).containsExactly(warnings)
        assertThat(screen.cutShort).isEmpty()

        screen.dismiss.getValue(warnings).complete(Unit)
        runCurrent()
        assertThat(screen.finished).containsExactly(warnings)
        assertThat(screen.shown).containsExactly(warnings, reloadFailed).inOrder()
        collector.cancel()
    }

    @Test
    fun `a newer failure still replaces an older one, and leaving never waits behind a snackbar`() = runTest {
        val events = Channel<SkillDetailEvent>(Channel.BUFFERED)
        val screen = Screen()
        val collector = launch { events.receiveAsFlow().collectSnackbarEvents(::detailSnackbarSlot, screen::show) }

        events.send(toggleFailed)
        runCurrent()
        events.send(reloadFailed)
        runCurrent()
        assertThat(screen.cutShort).containsExactly(toggleFailed)
        assertThat(screen.shown.last()).isEqualTo(reloadFailed)

        events.send(warnings)
        events.send(SkillDetailEvent.Exit)
        runCurrent()
        // The warnings are still up, and the page leaves anyway.
        assertThat(screen.finished).doesNotContain(warnings)
        assertThat(screen.shown).contains(SkillDetailEvent.Exit)
        collector.cancel()
    }

    @Test
    fun `the list keeps a failed import's retry and a save's warnings, not a load failure`() {
        assertThat(listSnackbarSlot(SkillsListEvent.SaveWarnings(listOf("w")))).isEqualTo(SnackbarSlot.KEPT)
        val importFailed = SkillsListEvent.ImportFailed(SkillError(SkillErrorKind.IMPORT_SKILL), canRetry = true)
        assertThat(listSnackbarSlot(importFailed)).isEqualTo(SnackbarSlot.KEPT)
        assertThat(listSnackbarSlot(SkillsListEvent.PageFailed)).isEqualTo(SnackbarSlot.REPLACEABLE)
        assertThat(listSnackbarSlot(SkillsListEvent.LoadFailed(SkillError(SkillErrorKind.LOAD_SKILLS)))).isEqualTo(SnackbarSlot.REPLACEABLE)
    }
}

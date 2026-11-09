package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatOptionsSheetControllerTest {

    private val scope = SaverScope { true }

    private fun saved(controller: ChatOptionsSheetController): String =
        with(ChatOptionsSheetController.Saver) { scope.save(controller) }!!

    private fun restored(value: String): ChatOptionsSheetController =
        ChatOptionsSheetController.Saver.restore(value)!!

    @Test
    fun `a sub-page reached from Options keeps Options as the entry so back returns there`() {
        val controller = ChatOptionsSheetController()
        controller.open(ChatOptionsPage.Options)
        controller.open(ChatOptionsPage.ModelSelector)

        assertEquals(ChatOptionsPage.ModelSelector, controller.openPage)
        assertEquals(ChatOptionsPage.Options, controller.entryPage)
    }

    @Test
    fun `a sheet opened on Parameters has it as the entry so back closes`() {
        val controller = ChatOptionsSheetController()
        controller.open(ChatOptionsPage.ModelParameters)

        assertEquals(ChatOptionsPage.ModelParameters, controller.entryPage)
        assertEquals(controller.openPage, controller.entryPage)
    }

    @Test
    fun `close clears the entry so the next open sets it afresh`() {
        val controller = ChatOptionsSheetController()
        controller.open(ChatOptionsPage.ModelSelector)
        controller.close()

        assertNull(controller.openPage)
        assertNull(controller.entryPage)

        controller.open(ChatOptionsPage.Options)
        assertEquals(ChatOptionsPage.Options, controller.entryPage)
    }

    @Test
    fun `saver round trips the open and entry pages`() {
        val controller = ChatOptionsSheetController()
        controller.open(ChatOptionsPage.Options)
        controller.open(ChatOptionsPage.ModelSelector)

        val value = saved(controller)
        assertEquals("ModelSelector|Options", value)

        val back = restored(value)
        assertEquals(ChatOptionsPage.ModelSelector, back.openPage)
        assertEquals(ChatOptionsPage.Options, back.entryPage)
    }

    @Test
    fun `saver restores a value without an entry as opened on its open page`() {
        val back = restored("ModelSelector")

        assertEquals(ChatOptionsPage.ModelSelector, back.openPage)
        assertEquals(ChatOptionsPage.ModelSelector, back.entryPage)
    }

    @Test
    fun `saver round trips a closed sheet`() {
        val back = restored(saved(ChatOptionsSheetController()))

        assertNull(back.openPage)
        assertNull(back.entryPage)
    }
}

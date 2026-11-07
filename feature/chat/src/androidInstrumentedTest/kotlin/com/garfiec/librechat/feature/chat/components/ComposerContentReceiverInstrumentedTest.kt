package com.garfiec.librechat.feature.chat.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** A `TextFieldState` field under [imageContentReceiver], fed the way a keyboard and the clipboard do. */
@RunWith(AndroidJUnit4::class)
class ComposerContentReceiverInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val received = mutableListOf<Any>()
    private val field = TextFieldState()
    private lateinit var view: View
    private var source: Uri? = null

    private fun setUp(enabled: Boolean) {
        composeRule.setContent {
            view = LocalView.current
            val receiver = if (enabled) {
                Modifier.imageContentReceiver(LocalContext.current, rememberCoroutineScope()) { received.addAll(it) }
            } else {
                Modifier
            }
            OfferImageTypesToKeyboard {
                OutlinedTextField(state = field, modifier = receiver.testTag(FIELD))
            }
        }
        composeRule.onNodeWithTag(FIELD).performClick()
        composeRule.waitForIdle()
    }

    private fun connect(): Pair<EditorInfo, InputConnection> {
        val info = EditorInfo()
        var connection: InputConnection? = null
        composeRule.runOnUiThread { connection = view.onCreateInputConnection(info) }
        return info to checkNotNull(connection)
    }

    /** A keyboard and the clipboard hand over content URIs, so the test image is served from MediaStore. */
    private fun image(bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "composer_receiver_test_${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val resolver = context.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)).also { source = it }
        checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
        return uri
    }

    private fun paste(clip: ClipData) {
        composeRule.runOnUiThread {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        }
        composeRule.onNodeWithTag(FIELD).performSemanticsAction(SemanticsActions.PasteText)
        composeRule.waitForIdle()
    }

    private fun assertReceivedCopyOf(bytes: ByteArray) {
        composeRule.waitUntil(5_000) { received.isNotEmpty() }
        val copy = File(checkNotNull((received.single() as Uri).path))
        assertEquals("keyboard_images", copy.parentFile?.name)
        assertArrayEquals(bytes, copy.readBytes())
    }

    @After
    fun deleteSource() {
        source?.let { context.contentResolver.delete(it, null, null) }
    }

    @Test
    fun aKeyboardStickerArrivesAsACachedCopy() {
        setUp(enabled = true)
        val (info, connection) = connect()
        assertArrayEquals(IMAGE_TYPES, EditorInfoCompat.getContentMimeTypes(info))

        val bytes = PNG_BYTES
        val content = InputContentInfoCompat(image(bytes), ClipDescription("sticker", arrayOf("image/png")), null)
        var accepted = false
        composeRule.runOnUiThread {
            accepted = InputConnectionCompat.commitContent(connection, info, content, 0, null)
        }

        assertTrue(accepted)
        assertReceivedCopyOf(bytes)
        assertEquals("", field.text.toString())
    }

    @Test
    fun aKeyboardCommitOfATypeTheComposerDoesNotTakeIsRefused() {
        setUp(enabled = true)
        val (info, connection) = connect()

        val content = InputContentInfoCompat(image(PNG_BYTES), ClipDescription("photo", arrayOf("image/heic")), null)
        var accepted = true
        composeRule.runOnUiThread {
            accepted = InputConnectionCompat.commitContent(connection, info, content, 0, null)
        }

        assertFalse(accepted)
        composeRule.waitForIdle()
        assertTrue(received.isEmpty())
    }

    @Test
    fun aPastedImageArrivesAsACachedCopy() {
        setUp(enabled = true)
        val bytes = PNG_BYTES
        paste(ClipData.newUri(context.contentResolver, "image", image(bytes)))

        assertReceivedCopyOf(bytes)
        assertEquals("", field.text.toString())
    }

    @Test
    fun aPastedClipOfTextAndAnImageInsertsTheTextAndAttachesTheImage() {
        setUp(enabled = true)
        val bytes = PNG_BYTES
        val clip = ClipData(ClipDescription("mixed", arrayOf("text/plain", "image/png")), ClipData.Item("hello"))
        clip.addItem(ClipData.Item(image(bytes)))
        paste(clip)

        assertReceivedCopyOf(bytes)
        assertEquals("hello", field.text.toString())
    }

    @Test
    fun withoutTheReceiverTheFieldOffersNoImageTypesAndTakesNoImage() {
        setUp(enabled = false)
        val (info, connection) = connect()
        assertEquals(0, EditorInfoCompat.getContentMimeTypes(info).size)

        val uri = image(byteArrayOf(1))
        val content = InputContentInfoCompat(uri, ClipDescription("sticker", arrayOf("image/png")), null)
        var accepted = true
        composeRule.runOnUiThread {
            accepted = InputConnectionCompat.commitContent(connection, info, content, 0, null)
        }

        assertFalse(accepted)
        composeRule.waitForIdle()
        assertTrue(received.isEmpty())
    }

    private companion object {
        const val FIELD = "field"
        val IMAGE_TYPES = arrayOf("image/png", "image/gif", "image/webp", "image/jpeg")
        val PNG_BYTES = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    }
}

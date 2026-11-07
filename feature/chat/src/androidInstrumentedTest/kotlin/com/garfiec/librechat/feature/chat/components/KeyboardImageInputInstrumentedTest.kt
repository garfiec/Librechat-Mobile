package com.garfiec.librechat.feature.chat.components

import android.content.ClipDescription
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
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

/** The value-based field the composer uses, driven through the input connection a keyboard gets. */
@RunWith(AndroidJUnit4::class)
class KeyboardImageInputInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val received = mutableListOf<Any>()
    private lateinit var view: View

    private fun setUp(enabled: Boolean) {
        composeRule.setContent {
            view = LocalView.current
            KeyboardImageInput(enabled = enabled, onImages = { received.addAll(it) }, scope = rememberCoroutineScope()) {
                var value by remember { mutableStateOf(TextFieldValue()) }
                OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.testTag(FIELD))
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

    private val resolver get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private var source: Uri? = null

    /** A keyboard hands over a content URI, so the test image is served from MediaStore. */
    private fun sticker(bytes: ByteArray): InputContentInfoCompat {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "keyboard_image_test_${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)).also { source = it }
        checkNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
        return InputContentInfoCompat(uri, ClipDescription("sticker", arrayOf("image/png")), null)
    }

    @After
    fun deleteSource() {
        source?.let { resolver.delete(it, null, null) }
    }

    @Test
    fun aCommittedStickerArrivesAsACachedCopy() {
        setUp(enabled = true)
        val (info, connection) = connect()
        assertTrue("image/png" in EditorInfoCompat.getContentMimeTypes(info))

        val bytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
        val content = sticker(bytes)
        var accepted = false
        composeRule.runOnUiThread {
            accepted = InputConnectionCompat.commitContent(connection, info, content, 0, null)
        }
        assertTrue(accepted)
        composeRule.waitUntil(5_000) { received.isNotEmpty() }

        val copy = File(checkNotNull((received.single() as Uri).path))
        assertEquals("keyboard_images", copy.parentFile?.name)
        assertArrayEquals(bytes, copy.readBytes())
    }

    @Test
    fun withUploadsOffTheFieldOffersNoImageTypes() {
        setUp(enabled = false)
        val (info, connection) = connect()
        assertEquals(0, EditorInfoCompat.getContentMimeTypes(info).size)

        val content = sticker(byteArrayOf(1))
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
    }
}

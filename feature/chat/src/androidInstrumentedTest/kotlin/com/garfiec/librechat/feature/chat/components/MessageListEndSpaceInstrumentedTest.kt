package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reading room past the last line: a reader can scroll into it, and the list's own scrolls to its
 * tail stop short of it, so they still land the last line just above the composer.
 */
@RunWith(AndroidJUnit4::class)
class MessageListEndSpaceInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var listState: LazyListState
    private var extraEndPx = 0f

    private fun setChat() {
        composeRule.setContent {
            listState = rememberLazyListState()
            val markdownCache = remember { ParsedMarkdownCache() }
            CompositionLocalProvider(LocalParsedMarkdownCache provides markdownCache) {
                LibreChatTheme {
                    extraEndPx = with(LocalDensity.current) { EXTRA.toPx() }
                    MessageList(
                        displayMessages = buildActiveMessagePath(THREAD),
                        isStreaming = false,
                        streamingContent = "",
                        onSiblingNavigation = { _, _ -> },
                        onEditMessage = {},
                        onRegenerateMessage = {},
                        onCopyMessage = {},
                        listState = listState,
                        extraEndSpace = EXTRA,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** How far the last item's bottom sits above the list's very end (end padding included). */
    private fun lastLineAboveTheEnd(): Float {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.last()
        assertEquals(info.totalItemsCount - 1, last.index)
        return (info.viewportEndOffset - (last.offset + last.size)).toFloat()
    }

    @Test
    fun scrollingToTheTailStopsShortOfTheReadingRoom() {
        setChat()
        // On settled content: the messages render asynchronously and grow after the open's jump,
        // which moves the tail whatever the end space is.
        composeRule.runOnUiThread {
            runBlocking { listState.scrollToTail(listState.layoutInfo.totalItemsCount - 1, extraEndPx) }
        }
        composeRule.waitForIdle()
        val restGap = lastLineAboveTheEnd()

        // A reader can still scroll the whole reading room past it.
        composeRule.runOnUiThread { runBlocking { listState.scrollBy(100_000f) } }
        composeRule.waitForIdle()

        assertEquals(extraEndPx, lastLineAboveTheEnd() - restGap, 2f)
    }

    private companion object {
        val EXTRA = 300.dp

        // Taller than any test device's screen, so the list opens scrolled to its tail.
        val THREAD = (1..40).map { index ->
            Message(
                messageId = "m$index",
                conversationId = "convo-1",
                parentMessageId = if (index == 1) null else "m${index - 1}",
                text = "Turn $index of the conversation, long enough to occupy a line or two.",
                isCreatedByUser = index % 2 == 1,
                sender = "TestBot",
            )
        }
    }
}

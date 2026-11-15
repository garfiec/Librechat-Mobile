package com.garfiec.librechat.shared.navigation

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.garfiec.librechat.feature.chat.navigation.Chat
import kotlin.test.Test
import kotlin.test.assertEquals

class PopTransitionsTest {

    @Test
    fun defaultContentKey_matchesTheKeyEntryProviderGivesAnEntry() {
        val provider = entryProvider<NavKey> { entry<Chat> { } }
        val key = Chat(conversationId = "c1")

        assertEquals(provider(key).contentKey, defaultContentKey(key))
    }
}

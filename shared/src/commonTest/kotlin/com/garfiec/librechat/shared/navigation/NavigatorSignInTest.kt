package com.garfiec.librechat.shared.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.auth.navigation.AddAccountServerUrl
import com.garfiec.librechat.feature.auth.navigation.Login
import com.garfiec.librechat.feature.auth.navigation.Register
import com.garfiec.librechat.feature.auth.navigation.ServerUrl
import com.garfiec.librechat.feature.chat.navigation.NewChat
import kotlin.test.Test
import kotlin.test.assertEquals

/** [Navigator.navigateToSignIn]: a cold start on an account owing 2FA setup opens its sign-in. */
class NavigatorSignInTest {

    private fun navigatorOn(vararg keys: NavKey): Pair<Navigator, NavBackStack<NavKey>> {
        val backStack = NavBackStack<NavKey>(*keys)
        return Navigator(backStack, onScreenChange = {}) to backStack
    }

    @Test
    fun fromTheColdStartServerScreen_opensSignInOverIt() {
        val (navigator, backStack) = navigatorOn(ServerUrl)

        navigator.navigateToSignIn()

        assertEquals(listOf<NavKey>(ServerUrl, Login), backStack.toList())
    }

    @Test
    fun aRepeatedReport_leavesTheUserWhereTheyAreInTheSignInFlow() {
        val (navigator, backStack) = navigatorOn(ServerUrl, Login, Register)

        navigator.navigateToSignIn()

        assertEquals(listOf<NavKey>(ServerUrl, Login, Register), backStack.toList())
    }

    @Test
    fun fromChatOrAnAddAccountFlow_resetsToSignIn() {
        val (fromChat, chatStack) = navigatorOn(NewChat())
        fromChat.navigateToSignIn()
        assertEquals(listOf<NavKey>(ServerUrl, Login), chatStack.toList())

        val (fromAdd, addStack) = navigatorOn(NewChat(), AddAccountServerUrl)
        fromAdd.navigateToSignIn()
        assertEquals(listOf<NavKey>(ServerUrl, Login), addStack.toList())
    }
}

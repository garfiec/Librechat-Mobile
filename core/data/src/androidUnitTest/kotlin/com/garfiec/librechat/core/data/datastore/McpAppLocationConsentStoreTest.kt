package com.garfiec.librechat.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class McpAppLocationConsentStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.store(): Pair<McpAppLocationConsentStore, InMemoryActiveAccountProvider> {
        val prefs = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("p.preferences_pb") }
        val accounts = InMemoryActiveAccountProvider()
        accounts.set(AccountId("a"))
        return McpAppLocationConsentStore(prefs, accounts) to accounts
    }

    @Test
    fun `a server never asked reads NOT_ASKED, and each answer is per server`() = runTest {
        val (store, _) = store()
        assertThat(store.consent("weather")).isEqualTo(McpAppLocationConsent.NOT_ASKED)
        store.set("weather", McpAppLocationConsent.ALLOWED)
        store.set("maps", McpAppLocationConsent.REFUSED)
        assertThat(store.consent("weather")).isEqualTo(McpAppLocationConsent.ALLOWED)
        assertThat(store.consent("maps")).isEqualTo(McpAppLocationConsent.REFUSED)
        store.set("weather", McpAppLocationConsent.NOT_ASKED)
        assertThat(store.consents.first()).containsExactly("maps", McpAppLocationConsent.REFUSED)
    }

    @Test
    fun `answers belong to the account that gave them`() = runTest {
        val (store, accounts) = store()
        store.set("weather", McpAppLocationConsent.ALLOWED)
        accounts.set(AccountId("b"))
        assertThat(store.consent("weather")).isEqualTo(McpAppLocationConsent.NOT_ASKED)
        accounts.clear()
        store.set("weather", McpAppLocationConsent.REFUSED)
        accounts.set(AccountId("a"))
        assertThat(store.consent("weather")).isEqualTo(McpAppLocationConsent.ALLOWED)
    }
}

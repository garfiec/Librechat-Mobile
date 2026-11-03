package com.garfiec.librechat.core.data.datastore

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The cold-start drop of a binding the roster doesn't know (`AccountRegistry`) can run before the
 * token cache warm lands; the teardown must still reach the bound account's slot.
 */
class OrphanedBindingTeardownTest {

    @Test
    fun `reading the binding before the warm lets the teardown remove the bound account's tokens`() = runTest {
        val store = FakeTokenStore(
            refreshClientOf(unconfinedMockEngine { respond("", HttpStatusCode.OK) }),
            seed = mapOf(
                ACTIVE_ACCOUNT_KEY to "acctX",
                accessKeyOf("acctX") to "A0",
                refreshKeyOf("acctX") to "R0",
            ),
            warmEagerly = false,
        )

        assertThat(store.boundAccountId()).isEqualTo("acctX")
        store.onAccountCleared()

        assertThat(store.persistedAccess("acctX")).isNull()
        assertThat(store.persistedRefresh("acctX")).isNull()
        assertThat(store.store[ACTIVE_ACCOUNT_KEY]).isNull()
        assertThat(store.getAccessToken()).isNull()
    }
}

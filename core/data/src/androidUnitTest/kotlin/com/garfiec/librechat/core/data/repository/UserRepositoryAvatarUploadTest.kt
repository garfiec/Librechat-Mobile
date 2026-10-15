package com.garfiec.librechat.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.identity.deriveAccountId
import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.model.response.AvatarUploadResponse
import com.garfiec.librechat.core.network.api.UserApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class UserRepositoryAvatarUploadTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val serverUrl = "https://chat.example.com"
    private val accountId = deriveAccountId(deriveServerId(serverUrl), "user-1")
    private val userApi = mockk<UserApi>()
    private val activeAccount = InMemoryActiveAccountProvider(AccountState.Resolved(accountId))

    private suspend fun roster() = AccountRoster(
        PreferenceDataStoreFactory.create { File(tmpFolder.root, "roster.preferences_pb") },
        Json { ignoreUnknownKeys = true },
    ).also {
        it.upsertAndActivate(
            AccountEntry(
                accountId = accountId.value,
                serverUrl = serverUrl,
                displayLabel = "Alice",
                avatarUrl = null,
                lastActiveAt = 1L,
            ),
        )
    }

    @Test
    fun `a successful upload returns the url and updates the active account's roster avatar`() = runTest {
        val roster = roster()
        coEvery { userApi.uploadAvatar(any(), any(), any()) } returns
            AvatarUploadResponse(url = "/images/user-1/avatar-1.png?manual=true")

        val result = UserRepositoryImpl(userApi, roster, activeAccount)
            .uploadAvatar(byteArrayOf(1), "image/jpeg", "avatar.jpg")

        assertThat((result as Result.Success).data).isEqualTo("/images/user-1/avatar-1.png?manual=true")
        val entry = roster.snapshot().entries.single()
        assertThat(entry.avatarUrl).isEqualTo("/images/user-1/avatar-1.png?manual=true")
        assertThat(entry.displayLabel).isEqualTo("Alice")
    }

    @Test
    fun `a failed upload leaves the roster alone`() = runTest {
        val roster = roster()
        coEvery { userApi.uploadAvatar(any(), any(), any()) } throws IOException("offline")

        val result = UserRepositoryImpl(userApi, roster, activeAccount)
            .uploadAvatar(byteArrayOf(1), "image/jpeg", "avatar.jpg")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(roster.snapshot().entries.single().avatarUrl).isNull()
    }

    @Test
    fun `a roster write failure does not fail an upload the server accepted`() = runTest {
        val brokenRoster = mockk<AccountRoster> {
            coEvery { updateAvatar(any(), any()) } throws IOException("disk full")
        }
        coEvery { userApi.uploadAvatar(any(), any(), any()) } returns AvatarUploadResponse(url = "/images/u/a.png")

        val result = UserRepositoryImpl(userApi, brokenRoster, activeAccount)
            .uploadAvatar(byteArrayOf(1), "image/jpeg", "avatar.jpg")

        assertThat((result as Result.Success).data).isEqualTo("/images/u/a.png")
    }
}
